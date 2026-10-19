package com.qpt.powermonitor.core.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootManager {
    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) {
        Shell.getShell().isRoot
    }

    /** 已授权状态（null = 尚未确定，例如还没弹过授权框）。 */
    suspend fun isRootGranted(): Boolean? = withContext(Dispatchers.IO) {
        runCatching { Shell.isAppGrantedRoot() }.getOrNull()
    }

    /** 主动触发授权：会弹出 KernelSU / Magisk 的授权对话框，阻塞直到用户选择。 */
    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }

    suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val result = Shell.cmd("cat '${path.escapeForShell()}'").exec()
        result.out.joinToString("\n").takeIf { result.isSuccess }
    }

    suspend fun writeFile(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("printf '%s' '${value.escapeForShell()}' > '${path.escapeForShell()}'").exec().isSuccess
    }

    suspend fun execute(command: String): List<String> = withContext(Dispatchers.IO) {
        Shell.cmd(command).exec().out
    }
}

/**
 * 单引号包裹的 shell 参数转义。
 * 采样命令里嵌入了大量 sysfs 路径，统一走这里，避免各 reader 各写一份。
 */
internal fun String.escapeForShell(): String = replace("'", "'\\''")
