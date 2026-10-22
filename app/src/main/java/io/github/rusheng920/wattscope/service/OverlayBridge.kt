package io.github.rusheng920.wattscope.service

import io.github.rusheng920.wattscope.service.overlay.OverlayController

/**
 * 浮层宿主仲裁。
 *
 * 同一个应用里有两套可能的浮层宿主，**同一时刻只能有一个真正挂窗口**，否则会出现
 * 两个悬浮球、或者一个能点一个点不动：
 *
 * - [QptMonitorService]：普通前台服务，`TYPE_APPLICATION_OVERLAY`，需要悬浮窗授权。
 *   一直是兜底路径，采样与通知也由它承载。
 * - [WattAccessibilityService]：无障碍服务，`TYPE_ACCESSIBILITY_OVERLAY`，不需要悬浮窗授权。
 *   优先级更高 —— 它的进程由 system_server 常驻绑定，ROM 的墓碑 / 应用速冻不会碰它，
 *   触摸通道也不受游戏等前台应用的浮层策略影响。
 *
 * 仲裁规则：无障碍在连就归它，断开自动回落到服务宿主。
 */
object OverlayBridge {

    interface Host {
        /** 该宿主持有的浮层控制器（窗口类型在构造时已确定）。 */
        val overlay: OverlayController
        fun attachOverlay()
        fun detachOverlay()
    }

    @Volatile
    private var accessibilityHost: Host? = null

    @Volatile
    private var serviceHost: Host? = null

    /** 当前承载浮层的宿主：无障碍优先。 */
    val current: Host?
        get() = accessibilityHost ?: serviceHost

    /** 无障碍服务连上：立刻把服务宿主的窗口让出来。 */
    fun registerAccessibility(host: Host) {
        accessibilityHost = host
        serviceHost?.detachOverlay()
        host.attachOverlay()
    }

    fun unregisterAccessibility(host: Host) {
        if (accessibilityHost === host) accessibilityHost = null
        host.detachOverlay()
        serviceHost?.attachOverlay()
    }

    fun registerService(host: Host) {
        serviceHost = host
        if (accessibilityHost == null) host.attachOverlay()
    }

    fun unregisterService(host: Host) {
        if (serviceHost === host) serviceHost = null
        host.detachOverlay()
    }

    /** 无障碍服务是否正在承载浮层（供设置页展示状态）。 */
    val isAccessibilityActive: Boolean
        get() = accessibilityHost != null
}
