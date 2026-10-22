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
    val accessibility: Boolean = false,
    /** 安装未知应用：应用内更新走 PackageInstaller，没有这项会直接弹不出安装框。 */
    val installPackages: Boolean = false,
    val isMiui: Boolean = false,
) {
    /** 核心权限：Root、悬浮窗、通知。用于首页进度提示。 */
    fun requiredGranted(): Int = listOf(root == true, overlay, notifications).count { it }

    fun requiredTotal(): Int = 3

    /** 是否所有核心权限就绪（首页不再提示）。 */
    val requiredReady: Boolean get() = root == true && overlay && notifications

    /**
     * 核心三项之外的「root 能代劳」的权限是否都已就绪。
     *
     * 用来决定权限中心要不要显示「一键授权」卡：全部就绪时那张卡只是噪声。
     * 注意不含自启动 —— 那是厂商私有开关，root 也代劳不了。
     */
    val allSecondaryGranted: Boolean
        get() = overlay && notifications && batteryUnrestricted && usageAccess &&
            accessibility && installPackages

    /**
     * 是否已知存在未授予的核心权限。
     *
     * [root] 为 null 表示「尚未检测」，不代表未授予；此时首页不应弹权限警告，
     * 避免冷启动时 root 授权还在异步探测中，用户看到「权限未给全」的误报。
     */
    val hasKnownMissingRequiredPermission: Boolean
        get() = root == false || !overlay || !notifications
}
