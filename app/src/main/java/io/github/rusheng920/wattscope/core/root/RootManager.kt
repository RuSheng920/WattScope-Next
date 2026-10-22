package io.github.rusheng920.wattscope.core.root

import com.topjohnwu.superuser.Shell
import io.github.rusheng920.wattscope.core.root.RootShellPool.Lane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootManager {
    /**
     * 当前 root 状态（不会主动弹授权框）。
     *
     * 不能只用 [Shell.isAppGrantedRoot]：libsu 内部把「su 是否存在」缓存在静态字段里，
     * 一旦判定为「存在但授权未知」，后续永远返回 null —— 用户在 KernelSU / Magisk
     * 里重新授权后，不重启进程就永远识别不到。因此优先读已建立 shell 的真实状态。
     */
    suspend fun isRootGranted(): Boolean? = withContext(Dispatchers.IO) {
        runCatching {
            Shell.getCachedShell()?.let { return@runCatching it.isRoot }
            Shell.isAppGrantedRoot()
        }.getOrNull()
    }

    /**
     * 主动探测 root：会拉起 su（未授权时弹系统授权框），用于「申请授权」按钮，
     * 以及需要重新确认「刚在管理器里改过授权」的场景。
     *
     * 已缓存的非 root shell 必须先关闭，否则 libsu 会一直复用它，永远看不到新授权。
     * 池里的 shell 同样要回收：它们建立于旧授权状态，继续用会一直是失败结果。
     */
    suspend fun probeRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cached = Shell.getCachedShell()
            if (cached != null && cached.isRoot) return@runCatching true
            cached?.close()
            RootShellPool.closeAll()
            Shell.getShell().isRoot
        }.getOrDefault(false)
    }

    /** 采样前的能力检查：这里允许主动拉起 su，属于用户已进入主流程后的正常申请。 */
    suspend fun hasRoot(): Boolean = probeRoot()

    /** 主动申请 root：会弹出 KernelSU / Magisk 的授权对话框，阻塞直到用户选择。 */
    suspend fun requestRoot(): Boolean = probeRoot()

    /** 读 sysfs 小文件。默认走 [Lane.SAMPLE]：调用方多是采样循环。 */
    suspend fun readFile(path: String, lane: Lane = Lane.SAMPLE): String? = withContext(Dispatchers.IO) {
        RootShellPool.execResult(lane, "cat '${path.escapeForShell()}'")
            ?.takeIf { it.isSuccess }
            ?.out
            ?.joinToString("\n")
    }

    /** 写 sysfs。走 [Lane.CONTROL]：写操作不能被 dumpsys 查询堵住。 */
    suspend fun writeFile(path: String, value: String, lane: Lane = Lane.CONTROL): Boolean =
        withContext(Dispatchers.IO) {
            RootShellPool.execResult(
                lane,
                "printf '%s' '${value.escapeForShell()}' > '${path.escapeForShell()}'",
            )?.isSuccess ?: false
        }

    /**
     * 执行命令并取 stdout。
     *
     * **通道要按耗时选**：5Hz 采样留在默认的 [Lane.SAMPLE]；`dumpsys` 这类百毫秒级命令
     * 必须显式指定 [Lane.QUERY]，否则会把采样循环堵在同一个 shell 队列里。
     */
    suspend fun execute(command: String, lane: Lane = Lane.SAMPLE): List<String> =
        withContext(Dispatchers.IO) { RootShellPool.exec(lane, command) }
}

/**
 * 单引号包裹的 shell 参数转义。
 * 采样命令里嵌入了大量 sysfs 路径，统一走这里，避免各 reader 各写一份。
 */
internal fun String.escapeForShell(): String = replace("'", "'\\''")
