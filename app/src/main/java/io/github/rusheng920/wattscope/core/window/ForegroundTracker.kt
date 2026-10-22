package io.github.rusheng920.wattscope.core.window

/**
 * 无障碍窗口事件驱动的前台应用跟踪。
 *
 * **解决什么问题**：记录判定要回答「目标应用还在前台吗」，之前靠 root
 * `dumpsys activity activities | grep topResumedActivity` —— 一次全量转储 200~800ms，
 * 每 2 秒一次。无障碍服务本来就常驻，窗口切换事件里直接带着包名，比 dumpsys 快一个量级，
 * 且是**推送式**：应用一切前台，这里立刻知道，没有轮询延迟。
 *
 * **只读元数据**：`AccessibilityEvent.packageName` / `className` 属于事件元信息，
 * 在 `canRetrieveWindowContent=false` 下同样可得。本类不碰节点树、不读界面内容。
 *
 * **为什么不用 TTL**：窗口事件是「状态变更通知」，最后一条事件就是当前状态，
 * 不存在过期问题（用户停在同一个应用里 10 分钟，最后那条事件依然有效）。
 * 只有服务断开或还没收到过事件时才返回 null，让调用方回退 dumpsys。
 */
object ForegroundTracker {

    /** 服务是否在运行。断开后必须停止供数，否则会把断开前的旧应用当成仍在前台。 */
    @Volatile
    private var attached = false

    @Volatile
    private var packageName: String? = null

    /** 系统级窗口：它们的出现不代表用户真的离开了原应用。 */
    private val SYSTEM_PACKAGES = setOf(
        // 通知栏 / 音量条 / 锁屏 / 关机菜单，都不是应用切换
        "com.android.systemui",
        // 系统弹窗（ANR、权限说明页等），底层应用仍然在前台
        "android",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
    )

    /** 输入法包名，由服务连接时注入。 */
    @Volatile
    private var imePackages: Set<String> = emptySet()

    /** 无障碍服务连接：开始供数。清空旧值，避免服务重启后残留上一个会话的判定结果。 */
    fun attach(imePackages: Set<String> = emptySet()) {
        this.imePackages = imePackages
        packageName = null
        attached = true
    }

    /** 服务断开或销毁：立即停止供数并清空，避免用陈旧值做判定。 */
    fun detach() {
        attached = false
        packageName = null
    }

    /**
     * 收到窗口状态变更事件。
     *
     * 过滤三类：系统浮层（通知栏、权限弹窗）、输入法、空包名。其余一律视为应用切换。
     * 桌面（launcher）**不**过滤 —— 回桌面就是要停记录。
     */
    fun onWindowStateChanged(pkg: CharSequence?, className: CharSequence?) {
        if (!attached) return
        val name = pkg?.toString()?.trim().orEmpty()
        if (name.isEmpty()) return
        if (name in SYSTEM_PACKAGES) return
        if (name in imePackages) return
        // 输入法窗口的类名特征，兜住「用户后来换了输入法、包名没在连接时那份列表里」的情况
        if (className?.toString()?.contains("InputMethod", ignoreCase = true) == true) return
        packageName = name
    }

    /** 当前前台包名；无障碍服务未运行或还没收到事件时返回 null（调用方回退 dumpsys）。 */
    fun current(): String? = if (attached) packageName else null
}
