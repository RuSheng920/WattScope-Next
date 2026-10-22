package io.github.rusheng920.wattscope.core.keepalive

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import io.github.rusheng920.wattscope.core.root.RootShellPool
import io.github.rusheng920.wattscope.core.root.RootShellPool.Lane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 冻结守卫（root 侧守护进程）。
 *
 * 为什么 `android:process=":daemon"` 没用：ColorOS / HyperOS 的墓碑（应用速冻）
 * 按 **uid** 冻结，写的是 `/sys/fs/cgroup/uid_<uid>/cgroup.freeze`。同包名的子进程
 * 与主进程同 uid、同 cgroup，会被一起 SIGSTOP —— 把记录搬进子进程等于没搬。
 *
 * 唯一能跳出这个 cgroup 的是 root shell：`su` 出来的进程以 uid 0 运行，
 * 既能写 cgroup 冻结文件，也能把自己和应用主进程迁到根 cgroup。
 * 守卫就跑在这个 root 进程里，与应用进程的生命周期完全解耦。
 *
 * 代价：每 [LOOP_INTERVAL_SEC] 秒唤醒一次 shell 做一次探测。对功耗测量类应用属于
 * 自噪声，故只在用户显式开启（或主动解决冻结问题时）运行。
 */
class FreezeGuard(private val context: Context) {

    private val scriptFile: File get() = File(context.filesDir, SCRIPT_NAME)

    /**
     * 启动守卫。root 未授权时直接返回 false，**不会**弹授权框
     * （读的是已缓存的 shell 状态，不 fork）。
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (Shell.getCachedShell()?.isRoot != true) return@withContext false
        if (isRunningBlocking()) return@withContext true
        runCatching {
            scriptFile.writeText(buildScript(context.applicationInfo.uid, context.packageName))
            scriptFile.setReadable(true, false)
            scriptFile.setExecutable(true, false)
            // setsid：脱离会话，避免随 libsu 的持久 shell 一起退出；
            // 输出全部丢弃，否则 libsu 会一直等管道 EOF。
            // 走 CONTROL 通道：启停是用户操作，不能被采样或 dumpsys 排在前面。
            val result = RootShellPool.execResult(
                Lane.CONTROL,
                "setsid sh '${scriptFile.absolutePath}' >/dev/null 2>&1 &",
            )
            result?.isSuccess == true
        }.onFailure { Log.w(TAG, "start failed", it) }.getOrDefault(false)
    }

    /** 停止守卫。pid 与脚本路径都做过唯一化，pkill 不会误伤别的进程。 */
    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val result = RootShellPool.execResult(Lane.CONTROL, "pkill -f '${scriptFile.absolutePath}'")
            result?.isSuccess == true || !isRunningBlocking()
        }.onFailure { Log.w(TAG, "stop failed", it) }.getOrDefault(false)
    }

    suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) { isRunningBlocking() }

    private suspend fun isRunningBlocking(): Boolean = runCatching {
        if (Shell.getCachedShell()?.isRoot != true) return false
        val out = RootShellPool.exec(Lane.CONTROL, "pgrep -f '${scriptFile.absolutePath}'")
        out.any { it.isNotBlank() }
    }.getOrDefault(false)

    private fun buildScript(uid: Int, pkg: String): String =
        "#!/system/bin/sh\n" +
            "# WattScope Next 冻结守卫 —— root 拉起，独立于应用进程存活。\n" +
            "# 第一步必须做：把自己从应用的 uid cgroup 里摘出去。\n" +
            "# su / setsid 都会继承调用者的 cgroup，不先摘出去的话守卫会和应用一起\n" +
            "# 被 SIGSTOP，届时没有任何人能来解冻。\n" +
            "echo \$\$ > /sys/fs/cgroup/cgroup.procs 2>/dev/null\n" +
            "CG=/sys/fs/cgroup/uid_$uid\n" +
            "PKG=$pkg\n" +
            "while :; do\n" +
            "  # 1) uid 级冻结被拉起 -> 立刻解冻\n" +
            "  if [ -r \"\$CG/cgroup.freeze\" ]; then\n" +
            "    read -r v < \"\$CG/cgroup.freeze\" 2>/dev/null\n" +
            "    [ \"\$v\" = \"1\" ] && echo 0 > \"\$CG/cgroup.freeze\" 2>/dev/null\n" +
            "  fi\n" +
            "  # 2) 把应用进程迁到根 cgroup，让后续冻结指令根本找不到它\n" +
            "  for p in \$(pidof \"\$PKG\" 2>/dev/null); do\n" +
            "    echo \"\$p\" > /sys/fs/cgroup/cgroup.procs 2>/dev/null\n" +
            "  done\n" +
            "  sleep $LOOP_INTERVAL_SEC\n" +
            "done\n"

    companion object {
        private const val TAG = "FreezeGuard"
        private const val SCRIPT_NAME = "freeze_guard.sh"

        /** 5 秒：解冻延迟的上界。再长会让「点一下没反应」的体感回来。 */
        private const val LOOP_INTERVAL_SEC = 5
    }
}
