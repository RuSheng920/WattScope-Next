package io.github.rusheng920.wattscope.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 语义化版本。
 *
 * 比较规则按 SemVer 2.0：主次修订号逐段比，若前两段相同则**正式版大于同号预发布版**
 * —— `1.2.6 > 1.2.6-beta.1`。这条规则正是「beta 版本号小于正式版时更新正式版」的
 * 实现基础：候选集合里取版本最大者，同号时正式版天然胜出。
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** 预发布标识（`beta.1` → ["beta", "1"]）；空 = 正式版。 */
    val pre: List<String> = emptyList(),
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return when {
            pre.isEmpty() && other.pre.isEmpty() -> 0
            pre.isEmpty() -> 1
            other.pre.isEmpty() -> -1
            else -> comparePre(pre, other.pre)
        }
    }

    /** 数字标识符按数值比较且优先级低于字母标识符（SemVer 规定）。 */
    private fun comparePre(a: List<String>, b: List<String>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i]
            val y = b[i]
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            val cmp = when {
                xn != null && yn != null -> xn.compareTo(yn)
                xn != null -> -1
                yn != null -> 1
                else -> x.compareTo(y)
            }
            if (cmp != 0) return cmp
        }
        return a.size.compareTo(b.size)
    }

    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        if (pre.isNotEmpty()) append('-').append(pre.joinToString("."))
    }

    companion object {
        private val PATTERN = Regex("""(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.\-]+))?""")

        /**
         * 从任意含版本的字符串里取版本号：`v1.2.5`、`WattScope-Next-v1.2.6-beta.1.apk`、
         * `WattScope Next v1.2.5` 都能解析。取第一个匹配，忽略 `V` 前缀。
         */
        fun parse(raw: String?): SemVer? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val m = PATTERN.find(text) ?: return null
            val major = m.groupValues[1].toIntOrNull() ?: return null
            val minor = m.groupValues[2].toIntOrNull() ?: return null
            val patch = m.groupValues[3].toIntOrNull() ?: return null
            val preRaw = m.groupValues[4]
            return SemVer(major, minor, patch, if (preRaw.isEmpty()) emptyList() else preRaw.split('.'))
        }
    }
}

/** 一条可用于升级的发布：版本、安装包地址、更新说明。 */
data class ReleaseInfo(
    val version: SemVer,
    val isBeta: Boolean,
    val tag: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val notes: String,
    val publishedAtMs: Long,
) {
    val versionName: String get() = version.toString()
}

/**
 * GitHub Releases 查询。
 *
 * 只取已上传 APK 资产的正式/预发布 Release：`beta` 前缀标签的构建在 CI 里以
 * prerelease 形式发布，因此「正式版 / Beta 版」两个通道共用同一个数据源，
 * 区别只在候选集合是否包含 prerelease。
 */
object UpdateChecker {
    private const val API =
        "https://api.github.com/repos/RuSheng920/WattScope-Next/releases?per_page=30"

    /**
     * 取版本最大的可升级发布。
     *
     * [includeBeta] = false 时只考虑正式版；true 时两者都算，由 [SemVer] 比较决定取舍
     * —— 若正式版号更大（例如 beta 还停在 1.2.6-beta.2 而正式版已发 1.2.6），
     * 结果就是正式版。
     */
    suspend fun fetchLatest(includeBeta: Boolean): ReleaseInfo? = withContext(Dispatchers.IO) {
        fetchReleases()
            .filter { includeBeta || !it.isBeta }
            .maxWithOrNull(
                // 同版本号时正式版优先：先比版本，再比是否 beta
                compareBy<ReleaseInfo> { it.version }.thenBy { if (it.isBeta) 0 else 1 },
            )
    }

    private fun fetchReleases(): List<ReleaseInfo> {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            // GitHub API 对无 UA 的请求直接 403
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "WattScope-Next")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parseReleases(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseReleases(body: String): List<ReleaseInfo> {
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val out = ArrayList<ReleaseInfo>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft")) continue
            val tag = obj.optString("tag_name")
            val assets = obj.optJSONArray("assets") ?: continue

            var apkName = ""
            var apkUrl = ""
            var apkSize = 0L
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val name = asset.optString("name")
                // 跳过未签名包（签名缺失时 CI 会产出 *-unsigned.apk，装了也会签名冲突）
                if (!name.endsWith(".apk") || name.contains("unsigned")) continue
                apkName = name
                apkUrl = asset.optString("browser_download_url")
                apkSize = asset.optLong("size")
                break
            }
            if (apkUrl.isEmpty()) continue

            val version = SemVer.parse(apkName) ?: SemVer.parse(tag) ?: continue
            out += ReleaseInfo(
                version = version,
                isBeta = obj.optBoolean("prerelease") || tag.startsWith("beta/"),
                tag = tag,
                apkUrl = apkUrl,
                apkSizeBytes = apkSize,
                notes = cleanNotes(obj.optString("body")),
                publishedAtMs = parseIsoMs(obj.optString("published_at")),
            )
        }
        return out
    }

    /**
     * Release 正文是 README 的 Markdown 片段（`##` 标题、`**加粗**`、`-` 列表）。
     * 更新弹窗只做纯文本渲染，这里把标记去掉，避免界面上出现一堆井号和星号。
     *
     * 正文里的 `## WattScope Next vX.Y.Z` 标题与弹窗标题重复，一并丢弃；
     * `---` 分隔线**保留** —— 它是「中文 / English」的分界，UI 层据此只展示当前语言那一半。
     */
    private fun cleanNotes(raw: String): String = raw
        // 用 [ \t] 而不是 \s：\s 会连换行一起吃掉，把「标题 + 空行 + 列表」压成一行
        .replace(Regex("""(?m)^#{1,6}[ \t]*"""), "")
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""(?m)^[ \t]*[-*][ \t]+"""), "· ")
        .replace(Regex("""`([^`]+)`"""), "$1")
        .replace(Regex("""\[(.+?)]\((.+?)\)"""), "$1")
        .lineSequence()
        .filterNot { it.startsWith("WattScope Next v") }
        .joinToString("\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    private fun parseIsoMs(text: String): Long {
        if (text.isEmpty()) return 0L
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(text)?.time ?: 0L
        }.getOrDefault(0L)
    }
}
