package io.github.rusheng920.wattscope.domain

/**
 * 权限授予状态快照。
 *
 * root 为 null 表示「尚未检测」（首次进入、或设备未安装 su 管理器），
 * UI 据此区分「未授予」与「检测中/不可用」，避免误报红色错误态。
 */
data class AppPermissions(
    val root: Boolean? = null,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val usageAccess: Boolean = false,
    val isMiui: Boolean = false,
) {
    /** 核心权限：Root、悬浮窗、通知。用于首页进度提示。 */
    fun requiredGranted(): Int = listOf(root == true, overlay, notifications).count { it }

    fun requiredTotal(): Int = 3

    /** 是否所有核心权限就绪（首页不再提示）。 */
    val requiredReady: Boolean get() = root == true && overlay && notifications
}
