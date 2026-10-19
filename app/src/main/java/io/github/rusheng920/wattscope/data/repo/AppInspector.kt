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
 * 优先用 UsageStats `ACTIVITY_RESUMED` 事件流（binder 查询，毫秒级、零 dumpsys）；
 * 拿不到时回退 root 执行 `dumpsys activity` 解析 topResumedActivity。
 * 结果带 TTL 缓存：采样循环每 tick 都要问一次前台应用，不能每次都起 dumpsys。
 */
class AppInspector(private val context: Context, private val rootManager: RootManager) {
    // 增量事件游标（UsageStats）：首次（或进程重启后）全量扫描 24 小时，之后只查询新增事件。
    private var lastResumedPackage: String? = null
    private var lastEventTimeMs: Long = 0L

    // 前台包名缓存
    private var cachedPackage: String? = null
    private var cachedAtMs = 0L

    /**
     * 当前前台包名（带 [maxAgeMs] 缓存）。所有调用方共用同一份缓存，
     * 避免「FPS 采样解析一次 + 自动停止检查再解析一次」的重复 dumpsys。
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
     * 是否为当前前台应用。查询结果为「未知」时返回 true，避免长前台会话被误判为离开。
     */
    suspend fun isForeground(packageName: String, maxAgeMs: Long = DEFAULT_TTL_MS): Boolean {
        val current = foregroundPackage(maxAgeMs) ?: return true
        return current == packageName
    }

    /** 记录开始时使用：root 前台查询，回退 UsageStats，再回退应用自身。 */
    suspend fun currentForegroundApp(): ForegroundAppInfo {
        val packageName = queryTopResumedPackage()
            ?: queryForegroundPackage()
            ?: queryLastUsedPackage24h()
            ?: context.packageName
        cachedPackage = packageName
        cachedAtMs = SystemClock.elapsedRealtime()
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
        const val DEFAULT_TTL_MS = 2_000L
    }
}
