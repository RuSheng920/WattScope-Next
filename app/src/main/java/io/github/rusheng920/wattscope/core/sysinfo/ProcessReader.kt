package io.github.rusheng920.wattscope.core.sysinfo

import android.content.Context
import android.content.pm.PackageManager
import android.system.Os
import android.system.OsConstants
import io.github.rusheng920.wattscope.core.root.RootManager
import io.github.rusheng920.wattscope.core.root.RootShellPool.Lane

/** 进程列表的一行。 */
data class ProcessEntry(
    val pid: Int,
    val name: String,
    val label: String,
    val packageName: String?,
    val cpuPercent: Float,
    val rssKb: Long,
) {
    val isApp: Boolean get() = packageName != null
}

/** 单个进程的详情（进程详情页用）。字段全部可空：节点缺失或权限不足时对应项不显示。 */
data class ProcessDetail(
    val pid: Int,
    val state: String? = null,
    val threads: Int? = null,
    val rssKb: Long? = null,
    val vssKb: Long? = null,
    val uid: Int? = null,
    val fdCount: Int? = null,
    val pssKb: Long? = null,
    val cmdline: String? = null,
)

/**
 * 进程读取器（对应 OpenMonitor 的 Process Manager）。
 *
 * 数据来源取舍：
 * - **CPU%** 取 `top -b -n 2` 的**第二帧**。第一帧是「开机以来均值」，没有参考价值；
 *   而 `dumpsys cpuinfo` 的统计窗口是系统级缓存，实测同一窗口内连调两次返回完全相同的数据，
 *   窗口可能停在几分钟前（`CPU usage from 459529ms to 159383ms ago`），拿它排序会误导。
 *   top 走两帧要 1 秒，但拿到的是当下值；解析失败再回落 `dumpsys cpuinfo`。
 * - **内存** 读 `/proc/[pid]/statm` 的第二个字段（resident pages）再乘页大小。
 *   早先读 `/proc/[pid]/status` 取 `VmRSS`，每个进程要解析 3–5 KB 文本，
 *   400 个进程下来页面要转十几秒；statm 只有一行几十字节，同样的循环快一个数量级。
 * - **进程名** 展示用 `/proc/[pid]/comm`（干净、15 字符封顶），
 *   应用判定用 `/proc/[pid]/cmdline` 的 argv[0]（应用进程这里就是包名，可能是 `pkg:service`）。
 *   两者分开取：cmdline 里的 NUL 会被 shell 丢掉，多参数进程会粘成一串，不能当展示名。
 * - **应用识别** 用 `包名 → 应用名` 映射（一次 `getInstalledPackages` 后缓存）。
 *   不能再按 uid 映射：system / systemui / settings 等几十个包共享 uid 1000，
 *   按 uid 取第一个会把 system_server 标成完全无关的应用名。
 *
 * 结束进程：应用走 `am force-stop`（会一并清掉同包所有进程，且不会误伤同 uid 的其它包），
 * 非应用进程才 `kill -9`。
 */
class ProcessReader(context: Context, private val rootManager: RootManager) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    private var labelMap: Map<String, String>? = null

    /** 页大小：Android 15 起有 16 KB 页的设备，不能写死 4。 */
    private val pageSizeKb: Long by lazy {
        runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) / 1024 }.getOrDefault(4L)
    }

    /** `  39% 3136/system_server:` —— dumpsys cpuinfo 的兜底格式。 */
    private val cpuinfoLine = Regex("""^\s*([0-9.]+)%\s+(\d+)/(\S+?):""")

    /** ` 3136  12.3  128M com.foo` —— top 的固定四列。 */
    private val topLine = Regex("""^\s*(\d+)\s+([0-9.]+)\s""")

    suspend fun load(): List<ProcessEntry> {
        val labels = packageLabels()
        val lines = rootManager.execute(statusCommand(), Lane.QUERY)
        val cpu = cpuPercent()

        return lines.mapNotNull { line ->
            if (!line.startsWith("PS|")) return@mapNotNull null
            val p = line.split('|')
            if (p.size < 5) return@mapNotNull null
            val pid = p[1].trim().toIntOrNull() ?: return@mapNotNull null
            val pages = p[2].trim().toLongOrNull() ?: return@mapNotNull null
            // 内核线程的 statm 第一屏是 0，且没有 cmdline —— 列表里只留用户态进程。
            // 少这一条的话，900 多条里有一半是 kswapd0 / crtc_commit:173 这种 0 KB 噪声。
            if (pages <= 0L) return@mapNotNull null
            val comm = p[3].trim().ifBlank { "pid $pid" }
            val argv0 = p[4].trim()
            // cmdline 的 NUL 会被 shell 吃掉，多参数进程会粘成一串；只有「完全是包名」才算命中
            val candidate = argv0.substringBefore(':')
            val pkg = candidate.takeIf { it.isNotBlank() && labels.containsKey(it) }
            ProcessEntry(
                pid = pid,
                name = comm,
                label = pkg?.let { labels[it] } ?: comm,
                packageName = pkg,
                cpuPercent = cpu[pid] ?: 0f,
                rssKb = pages * pageSizeKb,
            )
        }.sortedByDescending { it.cpuPercent }
    }

    /**
     * @return true 表示命令执行且目标进程已消失/被停止。
     *
     * `am force-stop` 与 `kill` 默认都不输出，只能靠追加的 `RC=$?` 判断退出码。
     */
    suspend fun kill(entry: ProcessEntry): Boolean {
        val cmd = if (entry.isApp && entry.packageName != null) {
            "am force-stop '${entry.packageName}' 2>/dev/null; echo RC=\$?"
        } else {
            "kill -9 ${entry.pid} 2>/dev/null; echo RC=\$?"
        }
        return rootManager.execute(cmd, Lane.CONTROL).any { it.trim() == "RC=0" }
    }

    /**
     * 读单个进程的详细状态。
     *
     * 一次 shell 往返拿五项：`status`（State/Threads/VmRSS/VmSize/Uid）、`cmdline`、
     * `fd` 目录条目数、`smaps_rollup` 的 Pss。Pss 需要 root 才能读别人的（自己可以），
     * 读不到就留空，不编造数字。
     */
    suspend fun detail(pid: Int): ProcessDetail {
        val out = rootManager.execute(detailCommand(pid), Lane.QUERY)
        val line = out.firstOrNull { it.startsWith("PD|") } ?: return ProcessDetail(pid)
        val p = line.split('|')
        fun field(i: Int): String? = p.getOrNull(i)?.trim()?.takeIf { it.isNotBlank() }
        return ProcessDetail(
            pid = pid,
            state = field(1),
            threads = field(2)?.toIntOrNull(),
            rssKb = field(3)?.toLongOrNull(),
            vssKb = field(4)?.toLongOrNull(),
            uid = field(5)?.toIntOrNull(),
            fdCount = field(6)?.toIntOrNull(),
            pssKb = field(7)?.toLongOrNull(),
            cmdline = field(8)?.takeIf { it.length > 1 },
        )
    }

    private fun detailCommand(pid: Int): String = buildString {
        append("d=/proc/$pid; st=; thr=; rss=; vsz=; uid=; ")
        append("while read -r k v rest; do case \"\$k\" in ")
        append("State:) st=\$v;; Threads:) thr=\$v;; VmRSS:) rss=\$v;; ")
        append("VmSize:) vsz=\$v;; Uid:) uid=\$v;; esac; ")
        append("done < \$d/status 2>/dev/null; ")
        append("c=; read -r c 2>/dev/null < \$d/cmdline; ")
        append("fds=0; for f in \$d/fd/*; do [ -e \"\$f\" ] && fds=\$((fds+1)); done; ")
        append("pss=; while read -r k v rest; do case \"\$k\" in Pss:) pss=\$v;; esac; ")
        append("done < \$d/smaps_rollup 2>/dev/null; ")
        append("printf 'PD|%s|%s|%s|%s|%s|%s|%s|%s\\n' \"\$st\" \"\$thr\" \"\$rss\" \"\$vsz\" \"\$uid\" \"\$fds\" \"\$pss\" \"\$c\"")
    }

    /** 刷新包名缓存（安装/卸载应用后调用）。 */
    fun invalidate() {
        labelMap = null
    }

    private fun packageLabels(): Map<String, String> {
        labelMap?.let { return it }
        val map = HashMap<String, String>()
        runCatching {
            pm.getInstalledPackages(0).forEach { info ->
                val appInfo = info.applicationInfo ?: return@forEach
                map[info.packageName] = appInfo.loadLabel(pm).toString()
            }
        }
        labelMap = map
        return map
    }

    /**
     * pid → CPU%。
     *
     * 先跑 top 取第二帧（当下值，约 1 秒）；`-o` 不被支持或输出无法解析时，
     * 回落 `dumpsys cpuinfo`（可能是几分钟前的缓存窗口，但至少能排序）。
     */
    private suspend fun cpuPercent(): Map<Int, Float> {
        val top = runCatching {
            rootManager.execute("top -b -n 2 -d 1 -o PID,%CPU,RES,ARGS 2>/dev/null", Lane.QUERY)
        }.getOrDefault(emptyList())
        parseTop(top).let { if (it.isNotEmpty()) return it }

        val result = HashMap<Int, Float>()
        runCatching {
            rootManager.execute("dumpsys cpuinfo", Lane.QUERY).forEach { line ->
                val m = cpuinfoLine.find(line) ?: return@forEach
                val percent = m.groupValues[1].toFloatOrNull() ?: return@forEach
                val pid = m.groupValues[2].toIntOrNull() ?: return@forEach
                // 同一进程可能出现多条（多线程聚合行），取最大值
                result[pid] = maxOf(result[pid] ?: 0f, percent)
            }
        }
        return result
    }

    /**
     * 只取**最后一块**表格：`top -b -n 2` 会打印两帧，第一帧是开机以来的均值。
     *
     * 表头定位不能只判 `PID` + `%CPU`：top 自己的进程行（`22237 6.0 ... top -b -n 2 -o PID,%CPU`）
     * 同时含这两个词，会被误判成表头。真正的表头以 `PID` 开头，数据行以 PID 数字开头，
     * 用「首字符不是数字」把它们区分开。
     */
    private fun parseTop(lines: List<String>): Map<Int, Float> {
        val header = lines.indexOfLast { line ->
            line.contains("PID") && line.contains("%CPU") &&
                line.trimStart().firstOrNull()?.isDigit() == false
        }
        if (header < 0) return emptyMap()
        val result = HashMap<Int, Float>()
        lines.drop(header + 1).forEach { line ->
            val m = topLine.find(line) ?: return@forEach
            val pid = m.groupValues[1].toIntOrNull() ?: return@forEach
            val percent = m.groupValues[2].toFloatOrNull() ?: return@forEach
            result[pid] = maxOf(result[pid] ?: 0f, percent)
        }
        return result
    }

    /**
     * 一次遍历所有进程，输出 `PS|pid|residentPages|comm|argv0`。
     *
     * 三个小文件各司其职，全部是几十字节级的读取：
     * - `statm` 第一行 `size resident shared ...`（单位：页），只取第二个字段算 RSS；
     * - `comm` 是内核线程名，15 字符封顶但干净 —— 用作展示名；
     * - `cmdline` 的 NUL 分隔符会被 shell 丢弃（`sh -c ...` 会粘成 `sh-cd=...`），
     *   所以它只用来判断「这是哪个包」，展示名不用它。
     */
    private fun statusCommand(): String = buildString {
        append("for d in /proc/[0-9]*; do ")
        append("p=\${d#/proc/}; ")
        append("r=; read -r _sz r _rest 2>/dev/null < \"\$d/statm\"; ")
        append("[ -n \"\$r\" ] || continue; ")
        append("n=; read -r n 2>/dev/null < \"\$d/comm\"; ")
        append("c=; read -r c 2>/dev/null < \"\$d/cmdline\"; ")
        append("printf 'PS|%s|%s|%s|%s\\n' \"\$p\" \"\$r\" \"\$n\" \"\$c\"; done")
    }
}
