package io.github.rusheng920.wattscope.core.root

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 分层 root shell 池。
 *
 * **为什么要池化**：libsu 的 `Shell.cmd(...)` 全部落在同一个全局 Shell 实例上，该实例内部
 * 对命令是串行的。一次 `dumpsys SurfaceFlinger --latency` 或 `dumpsys activity activities`
 * 要 200~800ms，会把同一时刻 5Hz 的 QPT / 温度采样全部堵在队列里 —— 表现就是曲线抖动、
 * 掉点，极端情况下整秒拿不到样本。
 *
 * **为什么能池化**：`Shell.Builder().build()` 每次都 fork 一个**独立的 su 进程**
 * （libsu 的 `Shell.EXECUTOR` 是 cachedThreadPool，不存在全局串行队列），
 * 所以按用途拆成三条互不阻塞的通道就足够。注意下单条通道内仍然是串行的，
 * 这正是我们想要的：同通道的命令按顺序来，跨通道并行。
 *
 * 通道划分：
 * - [Lane.SAMPLE]  5Hz 热路径：sysfs 批量读取、内核计帧。命令必须短、只读。
 * - [Lane.QUERY]   dumpsys / find 这类百毫秒级命令。**绝对不能**和采样共用通道。
 * - [Lane.CONTROL] 写 sysfs、启停脚本。要求低延迟，不能被查询挡住。
 *
 * **前提**：仅在 root 已授权时建池。未授权时静默退回 libsu 的全局 Shell，保持
 * 「非用户主动操作不弹授权框」这一既有行为不变。
 *
 * 功耗说明：池里每条通道是一个空闲的 sh 进程（内核睡眠态，不占 CPU）。真正影响功耗的
 * 是 fork 次数与唤醒次数，池化减少的是**排队时间**而不是命令本身的开销。
 */
object RootShellPool {

    enum class Lane { SAMPLE, QUERY, CONTROL }

    private class Slot {
        val mutex = Mutex()
        var shell: Shell? = null
    }

    private val slots: Map<Lane, Slot> = Lane.entries.associateWith { Slot() }

    /**
     * root 在运行期被撤销时置位：此时 su 只会退回普通 sh，继续建池等于每调一次命令就
     * fork 一个废进程。置位后全部走全局 shell，等 [closeAll]（由 probeRoot 触发）复位。
     */
    @Volatile
    private var degraded = false

    /** 降级到期时刻：到点后允许再试一次建池。 */
    @Volatile
    private var degradedUntilMs = 0L

    /**
     * root 已授权才会建池。两个来源都查：前者看已建立的 shell，后者读 libsu 的静态缓存（都不 fork）。
     *
     * 降级必须**会自愈**：冷启动时如果 su 还没授权，第一次建池会拿到普通 sh 并置 degraded；
     * 若一直不重试，用户随后在 KernelSU 里点「允许」也没用 —— 界面显示 root 已授权
     * （libsu 静态状态是 true），实际所有命令仍在非 root 下跑，表现就是「每核负载一直是 0」。
     * 因此 [DEGRADED_RETRY_MS] 到期后放行一次重建尝试。
     */
    private fun rootReady(): Boolean {
        if (degraded && elapsedRealtime() >= degradedUntilMs) degraded = false
        return !degraded &&
            (Shell.getCachedShell()?.isRoot == true || Shell.isAppGrantedRoot() == true)
    }

    private fun degrade() {
        degraded = true
        degradedUntilMs = elapsedRealtime() + DEGRADED_RETRY_MS
    }

    /** 执行命令并取 stdout。失败返回空列表，绝不抛异常——调用方都是采样循环，不能因此中断。 */
    suspend fun exec(lane: Lane, command: String): List<String> =
        execResult(lane, command)?.out.orEmpty()

    /**
     * 执行命令并返回完整结果（需要 [Shell.Result.isSuccess] 时用）。
     * 池不可用时走 libsu 全局 Shell，行为与改造前一致。
     */
    suspend fun execResult(lane: Lane, command: String): Shell.Result? = withContext(Dispatchers.IO) {
        if (!rootReady()) return@withContext fallback(command)
        val slot = slots.getValue(lane)
        slot.mutex.withLock {
            val shell = obtain(slot) ?: return@withLock fallback(command)
            runCatching {
                // 必须显式 to()，libsu 默认不收集 stdout
                val out = ArrayList<String>()
                shell.newJob().add(command).to(out).exec()
            }.onFailure {
                Log.w(TAG, "lane ${lane.name} 执行失败，回收该通道 shell", it)
                recycle(slot)
            }.getOrNull()
        }
    }

    /** 进程退出 / root 状态变化时整体回收。下次调用会按需重建。 */
    fun closeAll() {
        slots.values.forEach { recycle(it) }
        degraded = false
        degradedUntilMs = 0L
    }

    private fun obtain(slot: Slot): Shell? {
        slot.shell?.let { cached ->
            if (cached.isAlive) return cached
            recycle(slot)
        }
        return runCatching {
            val built = Shell.Builder.create()
                .setTimeout(TIMEOUT_SEC)
                .build()
            // root 中途被撤销时 su 会退回普通 sh：这种 shell 毫无用处，直接关掉走兜底
            if (!built.isRoot) {
                runCatching { built.close() }
                degraded = true
                return@runCatching null
            }
            slot.shell = built
            built
        }.onFailure { Log.w(TAG, "建池失败", it) }.getOrNull()
    }

    private fun recycle(slot: Slot) {
        slot.shell?.let { runCatching { it.close() } }
        slot.shell = null
    }

    private fun fallback(command: String): Shell.Result? =
        runCatching {
            val out = ArrayList<String>()
            Shell.cmd(command).to(out).exec()
        }.onFailure { Log.w(TAG, "全局 shell 执行失败", it) }.getOrNull()

    private const val TAG = "RootShellPool"

    /** 单条命令超时（秒）。超时后 libsu 会销毁该 shell，下次调用自动重建，避免通道被挂死。 */
    private const val TIMEOUT_SEC = 15L

    /** 降级后的重试间隔：太短会频繁 fork su（可能反复弹授权框），太长用户以为坏了。 */
    private const val DEGRADED_RETRY_MS = 15_000L

    private fun elapsedRealtime(): Long = android.os.SystemClock.elapsedRealtime()
}
