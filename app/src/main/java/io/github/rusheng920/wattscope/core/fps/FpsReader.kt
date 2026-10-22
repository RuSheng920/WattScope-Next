package io.github.rusheng920.wattscope.core.fps

import android.os.SystemClock
import android.util.Log
import io.github.rusheng920.wattscope.core.root.RootManager
import io.github.rusheng920.wattscope.core.root.RootShellPool.Lane
import io.github.rusheng920.wattscope.core.root.escapeForShell

/**
 * 帧率采集。两条路径刻意分开，口径不同、成本差一个数量级：
 *
 * **实时显示（主页 / OSD）→ 内核计帧 `measured_fps`**
 * 一次 `read` 内建调用，**零 fork**，按 1s 甚至更高频刷新也不心疼。
 * 读的是 CRTC 实际输出的帧计数，全屏游戏下等价于游戏帧率；LTPO 屏上也能反映掉帧。
 *
 * **落库记录 → `dumpsys SurfaceFlinger --latency`**
 * 能拿到每帧的 actualPresent 时间戳，窗口内平均、帧数、跨度都可回溯，
 * 适合做统计。代价是每次 fork 一个 dumpsys，只用于记录循环（1s 一次）。
 *
 * 为什么不用 gfxinfo 作主源：`Total frames rendered` 来自 HWUI/RenderThread 的帧统计，
 * 游戏走 SurfaceView + 自己的 EGL/Vulkan 线程渲染，帧根本不进这个计数器 —— 越是重负载游戏
 * 越测不到帧。SurfaceFlinger 记录的是**实际合成上屏**的帧。
 *
 * SF 读法：`dumpsys SurfaceFlinger --latency <layer>` 输出 up to 128 行 × 3 列（ns）：
 * desiredPresent / actualPresent / frameReady。取 actualPresent 有效的行，用
 * `(n-1) / (last-first)` 得到窗口内平均帧率。
 *
 * **记录路径的降级链**（依次尝试，任一成功即返回）：
 *
 * 1. `SurfaceFlinger --latency`（首选口径）
 * 2. `gfxinfo` 累计帧数差值（仅 HWUI 渲染的应用有效，SurfaceView 游戏基本拿不到）
 * 3. 内核 `measured_fps`（全局口径，全屏游戏下与图层口径等价）
 *
 * 第 3 级是兜底而非并列选项：没有它，某些 ROM（`--latency` 对 BLAST 层返回空）的
 * 整段记录会几乎没有 FPS 数据 —— 曲线断成几个点，比口径略粗更糟。
 * [Reading.source] 记录了本点实际来自哪一级。
 */
class FpsReader(
    private val rootManager: RootManager,
    /** 前台包名提供者（带 TTL 缓存，避免每次采样都 dumpsys activity）。 */
    private val foregroundPackage: suspend (maxAgeMs: Long) -> String?,
    /** 用户在设置页手工指定的图层；非空时优先于自动发现。 */
    private val layerOverride: () -> String? = { null },
) {
    data class Reading(
        val fps: Float,
        /** `surfaceflinger` / `gfxinfo`，写入记录后可用于区分口径。 */
        val source: String,
        val windowMs: Long,
        val frameCount: Int,
    )

    private var packageName: String? = null
    private var layerName: String? = null
    private var layerResolvedAtMs = 0L
    private var sfFailures = 0
    private var sfRetryAfterMs = 0L

    /** 已确认拿不到有效帧时间戳的图层。应用切换时清空。 */
    private val badLayers = mutableSetOf<String>()

    /** 上次打过日志的降级原因，用于去重（5Hz 下逐次打日志会刷屏）。 */
    private var lastNote: String? = null

    /** 内核计帧节点缓存：null = 尚未探测，"" = 探测过但不可用。 */
    private var kernelFpsPath: String? = null

    // gfxinfo 回退的累计帧数基线
    private var gfxPrevFrames = 0L
    private var gfxPrevAtMs = 0L

    /**
     * 实时帧率（主页 / OSD 专用）。
     *
     * 只读内核计帧节点，零 fork；节点不存在时才回落到 SF 口径，避免显示空白。
     * 记录落库**不要**用这个 —— 它没有帧时间戳，做不了窗口统计。
     */
    suspend fun sampleRealtime(): Float? = runCatching {
        kernelFps() ?: sampleInternal()?.fps
    }.getOrNull()

    /**
     * 记录口径帧率：SurfaceFlinger 呈现帧时间戳。
     *
     * FPS 属于展示型指标，任何异常（dumpsys 权限、层名变化、解析失败）
     * 都只应导致「本秒没有 FPS」，不能把记录协程一起带走。
     */
    suspend fun sampleForRecord(): Reading? = runCatching { sampleInternal() }.getOrNull()

    /** 兼容旧调用名，等价于 [sampleForRecord]。 */
    suspend fun sample(): Reading? = sampleForRecord()

    // ---------- 内核计帧（实时） ----------

    private suspend fun kernelFps(): Float? {
        val path = resolveKernelFpsPath() ?: return null
        // read 是 shell 内建：整条命令 0 次 fork。sysfs 的 st_size 恒为 4096，必须按内容读。
        val raw = rootManager.execute("read -r v < '$path' 2>/dev/null; echo \"\$v\"")
            .firstOrNull()
            ?.trim()
        if (raw.isNullOrEmpty()) return null
        return parseKernelFps(raw)
    }

    /**
     * 解析 measured_fps 的一行输出。
     *
     * 各内核格式不一致：`0 120`（CRTC 序号 + 帧率）、`120`、`fps: 60.0` 都见过。
     * 统一取「第一个落在合理区间内的浮点」——序号 0 会被区间过滤掉。
     */
    private fun parseKernelFps(raw: String): Float? = Regex("""\d+(?:\.\d+)?""")
        .findAll(raw)
        .mapNotNull { it.value.toFloatOrNull() }
        .firstOrNull { it > 0f && it <= MAX_PLAUSIBLE_FPS }

    private suspend fun resolveKernelFpsPath(): String? {
        kernelFpsPath?.let { return it.ifEmpty { null } }
        val found = rootManager.execute(
            "for p in $KERNEL_FPS_CANDIDATES; do [ -r \"\$p\" ] && echo \"\$p\" && break; done",
            lane = Lane.QUERY,
        ).firstOrNull()?.trim().orEmpty()
        if (found.isNotEmpty()) {
            kernelFpsPath = found
            return found
        }
        // 候选全落空才做一次全盘搜索（慢，只跑一次后缓存结果）
        val scanned = rootManager.execute(
            "find /sys -name measured_fps 2>/dev/null | grep crtc | head -1",
            lane = Lane.QUERY,
        ).firstOrNull()?.trim().orEmpty()
        kernelFpsPath = scanned
        return scanned.ifEmpty { null }
    }

    private suspend fun sampleInternal(): Reading? {
        val now = SystemClock.elapsedRealtime()
        val current = foregroundPackage(FOREGROUND_MAX_AGE_MS)
        if (current == null) {
            note("no-foreground")
            return null
        }
        if (current != packageName) {
            // 前台应用切换：层名、坏层黑名单与帧数基线全部失效
            packageName = current
            layerName = null
            badLayers.clear()
            sfFailures = 0
            sfRetryAfterMs = 0L
            gfxPrevFrames = 0L
            gfxPrevAtMs = 0L
        }
        if (now >= sfRetryAfterMs) {
            surfaceFlinger(now)?.let { return it }
        }
        gfxinfo(now)?.let { return it }
        // 最后兜底：内核计帧（全局口径）。全屏游戏下与图层口径等价，
        // 比「SF 拿不到就整段没有 FPS」有用得多 —— 曲线连续才谈得上分析。
        return kernelFps()?.let {
            note("kernel-fallback")
            Reading(fps = it, source = "kernel", windowMs = 0L, frameCount = 0)
        }
    }

    fun reset() {
        packageName = null
        layerName = null
        badLayers.clear()
        layerResolvedAtMs = 0L
        sfFailures = 0
        sfRetryAfterMs = 0L
        gfxPrevFrames = 0L
        gfxPrevAtMs = 0L
        lastNote = null
        autoPickedLayer = null
    }

    /**
     * 采样失败原因只在**变化时**打一条日志。
     *
     * 记录循环是 5Hz，逐次打日志会把 logcat 刷满、自己也会拖慢采样。排查时
     * `logcat -s FpsReader` 看到的就是「第一次进入某个失败状态」的时间点。
     */
    private fun note(reason: String) {
        if (reason == lastNote) return
        lastNote = reason
        Log.d(TAG, "FPS 降级原因: $reason (pkg=$packageName, layer=$layerName, bad=${badLayers.size})")
    }

    private suspend fun surfaceFlinger(now: Long): Reading? {
        val pkg = packageName ?: return null
        val layer = resolveLayer(pkg, now)
        if (layer == null) {
            note("no-layer")
            return null
        }
        val out = rootManager.execute(
            "dumpsys SurfaceFlinger --latency '${layer.escapeForShell()}' 2>/dev/null",
            lane = Lane.QUERY,
        )
        val reading = parseLatency(out)
        if (reading == null) {
            // 该层没有 latency 缓冲（BLAST / 部分 ROM 的 SurfaceView 层如此）。
            // 关键：把这一层记进黑名单再换下一个候选 —— 旧实现只把 layerName 置空，
            // 下一轮 resolveLayer 又按同样规则挑回同一个坏层，3 次后进入 60 秒静默，
            // 整段记录几乎没有 FPS（实测 147 个采样点里只有 4 个）。
            badLayers += layer
            layerName = null
            note("invalid-latency")
            if (++sfFailures >= SF_FAIL_LIMIT) {
                sfRetryAfterMs = now + SF_BACKOFF_MS
                sfFailures = 0
                // 退避结束后重新从第一个候选试起（ROM 可能重排了图层名）
                badLayers.clear()
            }
            return null
        }
        sfFailures = 0
        lastNote = null
        return reading
    }

    /**
     * 解析 `--latency` 输出。128 行 × 3 列（ns）：desiredPresent / actualPresent / frameReady。
     *
     * 只取 actualPresent 有效的行：0 与 [Long.MAX_VALUE] 是「这一帧还没上屏」的占位符，
     * 混进去会把窗口跨度撑成无穷大。
     */
    private fun parseLatency(out: List<String>): Reading? {
        var first = 0L
        var last = 0L
        var count = 0
        out.forEach { line ->
            val cols = line.trim().split('\t', ' ')
            if (cols.size < 3) return@forEach
            val present = cols[1].toLongOrNull() ?: return@forEach
            if (present <= 0L || present == Long.MAX_VALUE) return@forEach
            if (count == 0) first = present
            last = present
            count++
        }
        if (count < 2 || last <= first) return null
        val windowNs = last - first
        return Reading(
            fps = (count - 1) * 1_000_000_000f / windowNs,
            source = "surfaceflinger",
            windowMs = windowNs / 1_000_000L,
            frameCount = count,
        )
    }

    /** 最近一次**自动**选中的图层；手动指定时不会被更新，供设置页显示「自动模式实际在用谁」。 */
    @Volatile
    var autoPickedLayer: String? = null
        private set

    /** 列出当前 SurfaceFlinger 的全部图层，供设置页让用户手工指定。 */
    suspend fun listLayers(): List<String> =
        runCatching {
            rootManager.execute("dumpsys SurfaceFlinger --list 2>/dev/null", lane = Lane.QUERY)
                .mapNotNull { raw ->
                    val name = raw.trim().removePrefix("+").trim()
                    name.takeIf { it.isNotEmpty() }
                }
                .distinct()
                .sorted()
        }.getOrDefault(emptyList())

    /**
     * 扫描可选的采样图层，并**实测**每个候选能否拿到帧数据。
     *
     * 为什么要探测：`--list` 里看着正确的层（`Background for ...`、部分 BLAST 层）根本没有
     * latency 缓冲，选中后只会得到一条空曲线 —— 之前「147 个采样点只有 4 个 FPS」就是
     * 撞在这上面。把实测帧率摆进列表，用户不用猜。
     *
     * 成本控制：只探测排序后的前 [probeLimit] 个（前台应用相关优先），每个一次 dumpsys。
     * 用户点「扫描」才跑，且走 QUERY 通道，不干扰记录中的采样。
     */
    suspend fun scanLayers(probeLimit: Int = 10): List<LayerInfo> {
        val foreground = runCatching { foregroundPackage(FOREGROUND_MAX_AGE_MS) }.getOrNull()
        val candidates = listLayers()
            .mapNotNull { name -> classify(name)?.let { LayerInfo(name, it, foreground != null && name.contains(foreground)) } }
            .sortedWith(
                compareByDescending<LayerInfo> { it.isForeground }
                    .thenBy { it.kind.ordinal }
                    .thenBy { it.name.length },
            )
        val scanned = candidates.mapIndexed { index, info ->
            if (index < probeLimit) probeLayer(info) else info
        }
        // 排序规则与 [resolveLayer] 一致，所以扫描结果里第一个「有帧」的层就是自动模式此刻会挑的层。
        // 记下来供设置页展示 —— 用户不用猜「自动」到底用了谁。
        autoPickedLayer = scanned.firstOrNull { it.usable }?.name
        // 只留「属于前台应用」或「实测到帧」的层：一台机器上 --list 常有几十条
        // （输入法、系统浮层、别的应用），全列出来等于没筛选，用户还是要一条条试。
        val relevant = scanned.filter { it.isForeground || it.usable }
        return relevant.ifEmpty { scanned.take(probeLimit) }
    }

    /**
     * 图层分类。返回 null 表示「没有帧数据、直接隐藏」：
     *
     * - `Background for ...` 是 SurfaceView 的镜像层，永远没有独立帧时间戳，选中必得 0 帧
     * - `Sprite` / `VRI[...]` / `Display` 是合成器内部层，读数对用户没有意义
     */
    private fun classify(name: String): LayerKind? = when {
        name.startsWith("Background for ") -> null
        name.startsWith("Sprite") -> null
        name.startsWith("VRI[") -> null
        name.startsWith("Display") -> null
        name.contains("SurfaceView[") -> LayerKind.SURFACE_VIEW
        name.contains("#0") && name.contains("/") -> LayerKind.MAIN_WINDOW
        else -> LayerKind.OTHER
    }

    /** 对单个图层跑一次 `--latency`，把结果写回 [LayerInfo]。 */
    private suspend fun probeLayer(info: LayerInfo): LayerInfo {
        val out = runCatching {
            rootManager.execute(
                "dumpsys SurfaceFlinger --latency '${info.name.escapeForShell()}' 2>/dev/null",
                lane = Lane.QUERY,
            )
        }.getOrDefault(emptyList())
        val reading = parseLatency(out)
        return info.copy(
            fps = reading?.fps?.takeIf { it > 0f && it <= MAX_PLAUSIBLE_FPS },
            frames = reading?.frameCount ?: 0,
            probed = true,
        )
    }

    /**
     * 在前台包名对应的层里挑一个最可能承载上屏内容的：优先 SurfaceView（游戏），其次 #0 主层。
     *
     * 已确认拿不到帧时间戳的层会被跳过（[badLayers]），这样某个 SurfaceView 层没有
     * latency 缓冲时，下一轮会自动换到 `ActivityRecord` / `#0` 之类的候选，而不是反复撞同一堵墙。
     */
    private suspend fun resolveLayer(pkg: String, now: Long): String? {
        layerOverride()?.takeIf { it.isNotBlank() }?.let { return it }
        layerName?.let { if (now - layerResolvedAtMs < LAYER_TTL_MS) return it }
        val lines = rootManager.execute("dumpsys SurfaceFlinger --list 2>/dev/null", lane = Lane.QUERY)
        val all = lines.mapNotNull { raw ->
            val name = raw.trim().removePrefix("+").trim()
            name.takeIf { it.isNotEmpty() && it.contains(pkg) }
        }
        var candidates = all.filterNot { it in badLayers }
        if (candidates.isEmpty() && all.isNotEmpty()) {
            // 候选全被否掉：层名可能整体变了（应用重启 / ROM 重排），清空黑名单再试一轮
            badLayers.clear()
            candidates = all
        }
        val picked = candidates.lastOrNull { it.startsWith("SurfaceView[") }
            ?: candidates.lastOrNull { it.contains("#0") }
            ?: candidates.lastOrNull()
            ?: return null
        layerName = picked
        layerResolvedAtMs = now
        autoPickedLayer = picked
        return picked
    }

    /** 回退路径：gfxinfo 累计帧数差值。仅对走 HWUI 渲染的应用有效。 */
    private suspend fun gfxinfo(now: Long): Reading? {
        val pkg = packageName ?: return null
        val out = rootManager.execute(
            "dumpsys gfxinfo '${pkg.escapeForShell()}' 2>/dev/null | " +
                "awk '/Total frames rendered/{print \$NF; exit}'",
            lane = Lane.QUERY,
        )
        val frames = out.firstOrNull()?.trim()?.toLongOrNull() ?: return null
        val prevFrames = gfxPrevFrames
        val prevAt = gfxPrevAtMs
        gfxPrevFrames = frames
        gfxPrevAtMs = now
        if (prevFrames <= 0L || prevAt <= 0L || now <= prevAt) return null
        val delta = frames - prevFrames
        if (delta <= 0L) return null
        val dtSec = (now - prevAt) / 1000f
        if (dtSec < 0.5f) return null
        return Reading(
            fps = (delta / dtSec).coerceIn(0f, 240f),
            source = "gfxinfo",
            windowMs = now - prevAt,
            frameCount = delta.toInt(),
        )
    }

    private companion object {
        const val FOREGROUND_MAX_AGE_MS = 2_000L
        const val LAYER_TTL_MS = 10_000L
        const val SF_FAIL_LIMIT = 3

        /**
         * SF 全面失败后的退避时长。旧值 60 秒太长 —— 期间只剩 gfxinfo 与内核兜底，
         * 一旦 gfxinfo 也不动（SurfaceView 游戏），整分钟没有任何 FPS 数据。
         */
        const val SF_BACKOFF_MS = 15_000L

        const val TAG = "FpsReader"

        /** 超过这个值必然是解析串里混进了时间戳/序号，直接判为无效。 */
        const val MAX_PLAUSIBLE_FPS = 1_000f

        /** 内核计帧节点的常见落点，按命中概率排序；都落空才做 find 兜底。 */
        const val KERNEL_FPS_CANDIDATES =
            "/sys/class/drm/sde-crtc-0/measured_fps " +
                "/sys/class/drm/card0-CRTC-0/measured_fps " +
                "/sys/class/drm/crtc-0/measured_fps " +
                "/sys/class/graphics/fb0/measured_fps"
    }
}
