package io.github.rusheng920.wattscope.core.fps

import android.os.SystemClock
import io.github.rusheng920.wattscope.core.root.RootManager
import io.github.rusheng920.wattscope.core.root.escapeForShell

/**
 * 帧率采集：以 **SurfaceFlinger** 呈现帧时间戳为主数据源，`dumpsys gfxinfo` 仅作回退。
 *
 * 为什么不用 gfxinfo 作主源：`Total frames rendered` 来自 HWUI/RenderThread 的帧统计，
 * 游戏走 SurfaceView + 自己的 EGL/Vulkan 线程渲染，帧根本不进这个计数器 —— 越是重负载游戏
 * 越测不到帧（旧实现因此只能返回 null）。SurfaceFlinger 记录的是**实际合成上屏**的帧，
 * 对全屏游戏等价于游戏帧率，且输出只有 128 行，比 gfxinfo 便宜一个数量级。
 *
 * 读法：`dumpsys SurfaceFlinger --latency <layer>` 输出 up to 128 行 × 3 列（ns）：
 * desiredPresent / actualPresent / frameReady。取 actualPresent 有效的行，用
 * `(n-1) / (last-first)` 得到窗口内平均帧率。
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

    // gfxinfo 回退的累计帧数基线
    private var gfxPrevFrames = 0L
    private var gfxPrevAtMs = 0L

    /**
     * 采样一次。FPS 属于展示型指标，任何异常（dumpsys 权限、层名变化、解析失败）
     * 都只应导致「本秒没有 FPS」，不能把记录协程一起带走。
     */
    suspend fun sample(): Reading? = runCatching { sampleInternal() }.getOrNull()

    private suspend fun sampleInternal(): Reading? {
        val now = SystemClock.elapsedRealtime()
        val current = foregroundPackage(FOREGROUND_MAX_AGE_MS) ?: return null
        if (current != packageName) {
            // 前台应用切换：层名与帧数基线全部失效
            packageName = current
            layerName = null
            gfxPrevFrames = 0L
            gfxPrevAtMs = 0L
        }
        if (now >= sfRetryAfterMs) {
            surfaceFlinger(now)?.let { return it }
        }
        return gfxinfo(now)
    }

    fun reset() {
        packageName = null
        layerName = null
        layerResolvedAtMs = 0L
        sfFailures = 0
        sfRetryAfterMs = 0L
        gfxPrevFrames = 0L
        gfxPrevAtMs = 0L
    }

    private suspend fun surfaceFlinger(now: Long): Reading? {
        val pkg = packageName ?: return null
        val layer = resolveLayer(pkg, now) ?: return null
        val out = rootManager.execute("dumpsys SurfaceFlinger --latency '$layer' 2>/dev/null")
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
        if (count < 2 || last <= first) {
            // 该层没有 latency 缓冲（部分 ROM 的 BLAST 层如此）：丢弃层缓存，连续失败后暂停尝试
            layerName = null
            if (++sfFailures >= SF_FAIL_LIMIT) sfRetryAfterMs = now + SF_BACKOFF_MS
            return null
        }
        sfFailures = 0
        val windowNs = last - first
        return Reading(
            fps = (count - 1) * 1_000_000_000f / windowNs,
            source = "surfaceflinger",
            windowMs = windowNs / 1_000_000L,
            frameCount = count,
        )
    }

    /** 列出当前 SurfaceFlinger 的全部图层，供设置页让用户手工指定。 */
    suspend fun listLayers(): List<String> =
        runCatching {
            rootManager.execute("dumpsys SurfaceFlinger --list 2>/dev/null")
                .mapNotNull { raw ->
                    val name = raw.trim().removePrefix("+").trim()
                    name.takeIf { it.isNotEmpty() }
                }
                .distinct()
                .sorted()
        }.getOrDefault(emptyList())

    /** 在前台包名对应的层里挑一个最可能承载上屏内容的：优先 SurfaceView（游戏），其次 #0 主层。 */
    private suspend fun resolveLayer(pkg: String, now: Long): String? {
        layerOverride()?.takeIf { it.isNotBlank() }?.let { return it }
        layerName?.let { if (now - layerResolvedAtMs < LAYER_TTL_MS) return it }
        val lines = rootManager.execute("dumpsys SurfaceFlinger --list 2>/dev/null")
        val candidates = lines.mapNotNull { raw ->
            val name = raw.trim().removePrefix("+").trim()
            name.takeIf { it.isNotEmpty() && it.contains(pkg) }
        }
        val picked = candidates.lastOrNull { it.startsWith("SurfaceView[") }
            ?: candidates.lastOrNull { it.contains("#0") }
            ?: candidates.lastOrNull()
            ?: return null
        layerName = picked
        layerResolvedAtMs = now
        return picked
    }

    /** 回退路径：gfxinfo 累计帧数差值。仅对走 HWUI 渲染的应用有效。 */
    private suspend fun gfxinfo(now: Long): Reading? {
        val pkg = packageName ?: return null
        val out = rootManager.execute(
            "dumpsys gfxinfo '${pkg.escapeForShell()}' 2>/dev/null | " +
                "awk '/Total frames rendered/{print \$NF; exit}'",
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
        const val SF_BACKOFF_MS = 60_000L
    }
}
