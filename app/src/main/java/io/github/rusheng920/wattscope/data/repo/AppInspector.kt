package io.github.rusheng920.wattscope.data.repo

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
import io.github.rusheng920.wattscope.core.root.RootManager
import io.github.rusheng920.wattscope.core.root.RootShellPool.Lane
import io.github.rusheng920.wattscope.core.window.ForegroundTracker

data class ForegroundAppInfo(
    val appName: String,
    val packageName: String,
    val iconPackage: String?,
)

/**
 * 前台应用检测。
 *
 * 三条数据源，按实时性与成本排序：
 *
 * 1. **[ForegroundTracker]**（无障碍窗口事件，推送式、零 shell）。用户开了无障碍就是它，
 *    应用一切前台立刻知道，没有轮询延迟。
 * 2. **UsageStats `ACTIVITY_RESUMED`**（binder 查询，毫秒级但**有系统入库延迟**）。
 * 3. **root `dumpsys activity activities`**（实时权威，但一次全量转储 200~800ms）。
 *
 * 为什么还要保留两条回退：无障碍是可选权限，没开的时候功能不能瞎。回退顺序在两条路径上
 * 刻意不同 —— 采样路径（[foregroundPackage]，给 FPS 图层定位用）优先 UsageStats，因为它
 * 每 tick 都可能问，绝不能起 dumpsys；判定路径（[isForeground] /
 * [currentForegroundApp]）优先 dumpsys，因为它决定「记录是否该自动停止」，宁可慢也不能错。
 *
 * 历史上两条路径曾因 UsageStats 的入库延迟给出不同包名，导致记录在应用刚切前台时
 * 被误判成「已离开」而自动停止。现在第一优先级统一为 [ForegroundTracker]，
 * 只要无障碍开着，两条路径天然恒等，这个问题不再存在。
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
     * 当前前台包名（采样用，带 [maxAgeMs] 缓存）。
     *
     * 无障碍窗口事件优先且**不看缓存年龄** —— 它是推送式的最新状态，比任何缓存都新。
     */
    suspend fun foregroundPackage(maxAgeMs: Long = DEFAULT_TTL_MS): String? {
        val now = SystemClock.elapsedRealtime()
        ForegroundTracker.current()?.let {
            cachedPackage = it
            cachedAtMs = now
            return it
        }
        cachedPackage?.let { if (now - cachedAtMs <= maxAgeMs) return it }
        val pkg = queryForegroundPackage() ?: queryTopResumedPackage() ?: return null
        cachedPackage = pkg
        cachedAtMs = now
        return pkg
    }

    /**
     * 判定用的当前前台包名：无障碍窗口事件优先，其次 root `dumpsys`，最后 UsageStats。
     * 与 [currentForegroundApp] 同源，保证「记录开始」与「是否还在前台」判定恒等。
     */
    private suspend fun verdictForegroundPackage(maxAgeMs: Long): String? {
        val now = SystemClock.elapsedRealtime()
        // 窗口事件源永远是最新状态，命中即用，不受缓存年龄限制
        ForegroundTracker.current()?.let {
            if (it != verdictPackage) {
                verdictPackage = it
                verdictAtMs = now
            }
            return it
        }
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

    /** 记录开始时使用：与 [isForeground] 同源（窗口事件 → root 前台查询 → UsageStats）。 */
    suspend fun currentForegroundApp(): ForegroundAppInfo {
        val packageName = ForegroundTracker.current()
            ?: queryTopResumedPackage()
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
            lane = Lane.QUERY,
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
         * 只用于 dumpsys 回退路径的缓存（无障碍未开启时才会走到）。全量转储比 UsageStats
         * 的 binder 查询重，每 2 秒一次足够及时：能感知用户切走应用，又不至于变成常态开销。
         */
        const val VERDICT_TTL_MS = 2_000L
    }
}
