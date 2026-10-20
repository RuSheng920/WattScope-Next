package io.github.rusheng920.wattscope.data.repo

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
import io.github.rusheng920.wattscope.core.root.RootManager

data class ForegroundAppInfo(
    val appName: String,
    val packageName: String,
    val iconPackage: String?,
)

/**
 * 前台应用检测。
 *
 * 两条路径，数据源优先级刻意不同：
 *
 * - [foregroundPackage]：采样热路径（FPS 图层定位）。优先 UsageStats `ACTIVITY_RESUMED`
 *   事件流（binder 查询，毫秒级、零 dumpsys），拿不到才回退 root。
 * - [isForeground] / [currentForegroundApp]：记录的生命周期判定。优先 root
 *   `dumpsys topResumedActivity`（实时、权威），回退 UsageStats。
 *
 * 之所以要分开：UsageStats 的 `ACTIVITY_RESUMED` 事件有系统级入库延迟，刚切到前台的应用
 * 常常还没进事件流；若「开始记录」与「是否还在前台」用不同数据源，应用刚切前台的几秒内
 * 两边会给出不同包名，悬浮球记录会被误判成「已离开目标应用」而 1~2 秒内自动停止。
 * 判定路径两端都走 dumpsys 后，二者恒等。
 *
 * 两条路径各自带 TTL 缓存：采样循环每 tick 都要问一次，不能每次都起 dumpsys。
 */
class AppInspector(private val context: Context, private val rootManager: RootManager) {
    // 增量事件游标（UsageStats）：首次（或进程重启后）全量扫描 24 小时，之后只查询新增事件。
    private var lastResumedPackage: String? = null
    private var lastEventTimeMs = 0L

    // 采样路径的前台包名缓存（UsageStats 优先）
    private var cachedPackage: String? = null
    private var cachedAtMs = 0L

    // 判定路径的前台包名缓存（dumpsys 优先），与采样缓存相互独立
    private var verdictPackage: String? = null
    private var verdictAtMs = 0L

    /**
     * 当前前台包名（采样用，带 [maxAgeMs] 缓存）。UsageStats 优先，零 dumpsys 开销。
     */
    suspend fun foregroundPackage(maxAgeMs: Long = DEFAULT_TTL_MS): String? {
        val now = SystemClock.elapsedRealtime()
        cachedPackage?.let { if (now - cachedAtMs <= maxAgeMs) return it }
        val pkg = queryForegroundPackage() ?: queryTopResumedPackage() ?: return null
        cachedPackage = pkg
        cachedAtMs = now
        return pkg
    }

    /**
     * 判定用的当前前台包名：root `dumpsys` 优先（实时权威），回退 UsageStats。
     * 与 [currentForegroundApp] 同源，保证「记录开始」与「是否还在前台」判定恒等。
     */
    private suspend fun verdictForegroundPackage(maxAgeMs: Long): String? {
        val now = SystemClock.elapsedRealtime()
        verdictPackage?.let { if (now - verdictAtMs <= maxAgeMs) return it }
        val pkg = queryTopResumedPackage() ?: queryForegroundPackage() ?: return null
        verdictPackage = pkg
        verdictAtMs = now
        return pkg
    }

    /**
     * 是否为当前前台应用（判定路径）。查询结果为「未知」时返回 true，
     * 避免长前台会话被误判为离开。
     */
    suspend fun isForeground(packageName: String, maxAgeMs: Long = VERDICT_TTL_MS): Boolean {
        val current = verdictForegroundPackage(maxAgeMs) ?: return true
        return current == packageName
    }

    /** 记录开始时使用：与 [isForeground] 同源（root 前台查询 → UsageStats），保证前后判定恒等。 */
    suspend fun currentForegroundApp(): ForegroundAppInfo {
        val packageName = queryTopResumedPackage()
            ?: queryForegroundPackage()
            ?: queryLastUsedPackage24h()
            ?: context.packageName
        cachedPackage = packageName
        cachedAtMs = SystemClock.elapsedRealtime()
        // 判定缓存同步刷新：记录刚开始时 [isForeground] 若命中旧缓存可能拿到别的包名，
        // 直接写入本次判定结果，保证「开始记录」与「是否还在前台」用的是同一个包名。
        verdictPackage = packageName
        verdictAtMs = cachedAtMs
        val packageManager = context.packageManager
        val appName = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse {
            if (packageName == context.packageName) "WattScope Next" else packageName
        }
        val hasIcon = runCatching {
            packageManager.getApplicationIcon(packageName)
        }.isSuccess

        return ForegroundAppInfo(
            appName = appName,
            packageName = packageName,
            iconPackage = packageName.takeIf { hasIcon },
        )
    }

    /** root 查询当前 topResumedActivity 的包名。 */
    suspend fun queryTopResumedPackage(): String? {
        val out = rootManager.execute(
            "dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity='",
        )
        val line = out.firstOrNull() ?: return null
        val m = Regex("u\\d+\\s+([^/\\s]+)/").find(line) ?: return null
        // 不能按 com./android. 前缀过滤：tv.danmaku.bili、org.telegram.messenger 等合法包名会被误杀
        return m.groupValues[1].takeIf { it.isNotBlank() && it.contains('.') }
    }

    private fun queryForegroundPackage(): String? {
        val usageStats = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        // 首次全量 24h；之后增量查询（含 5s 重叠，避免边界事件遗漏）
        val start = if (lastEventTimeMs > 0) lastEventTimeMs - 5_000 else now - 24 * 60 * 60_000L
        val events = usageStats.queryEvents(start, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp > lastEventTimeMs) {
                lastEventTimeMs = event.timeStamp
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastResumedPackage = event.packageName
                }
            }
        }
        return lastResumedPackage
    }

    private fun queryLastUsedPackage24h(): String? {
        val usageStats = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        return usageStats
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 24 * 60 * 60_000L, now)
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    private companion object {
        /** 采样路径的缓存有效期：UsageStats 查询很便宜，但没必要每 tick 都查。 */
        const val DEFAULT_TTL_MS = 2_000L

        /**
         * 判定路径的缓存有效期。
         *
         * 走 root `dumpsys activity activities`（全量转储，比 UsageStats 的 binder 查询重），
         * 所以只在记录期间才用到，且每 2 秒查一次足够及时 —— 既能感知用户切走应用，
         * 又不至于让 dumpsys 变成常态开销。
         */
        const val VERDICT_TTL_MS = 2_000L
    }
}
