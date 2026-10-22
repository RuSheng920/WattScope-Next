package io.github.rusheng920.wattscope.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import io.github.rusheng920.wattscope.QptApplication
import io.github.rusheng920.wattscope.core.window.ForegroundTracker
import io.github.rusheng920.wattscope.service.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 保活与浮层宿主（无障碍服务）。
 *
 * **为什么必须有它**：ColorOS / HyperOS 的墓碑（应用速冻）会把普通前台服务的应用也冻住。
 * 冻结是按 uid 写 `cgroup.freeze`，进程内再开 `:daemon` 子进程同属一个 uid，会一起被冻。
 * 而无障碍服务由 system_server 常驻绑定，进程状态永远不是 `CACHED`，冻结机制不会碰它 ——
 * 这是 Scene 一直在用的路径，也是本应用悬浮球在游戏里能持续响应点击的前提。
 *
 * 附带好处：以它为 Context 创建的窗口用 [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]，
 * **不需要** `SYSTEM_ALERT_WINDOW` 授权，窗口层级由无障碍通道托管，游戏不会吞触摸。
 *
 * **顺带的用途**：窗口切换事件里带着包名，直接供 [ForegroundTracker] 做前台应用判定，
 * 取代记录期间每 2 秒一次的 `dumpsys activity`（一次全量转储 200~800ms）。
 *
 * **隐私边界**：本服务不读取任何界面内容（`canRetrieveWindowContent=false`），
 * 不注入手势，不模拟点击。只取事件自带的 `packageName` / `className` 两个元信息字段，
 * 且仅在内存里保留「当前前台包名」，不落盘、不上传。
 */
class WattAccessibilityService : AccessibilityService(), OverlayBridge.Host {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: OverlayController? = null
    private var overlayAttached = false

    private val repository by lazy { (application as QptApplication).repository }

    override val overlay: OverlayController
        get() = controller ?: OverlayController(
            context = this,
            repository = repository,
            scope = scope,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        ).also { controller = it }

    override fun onServiceConnected() {
        super.onServiceConnected()
        OverlayBridge.registerAccessibility(this)
        // 输入法窗口不该被当成「应用切换」，连接时取一份启用列表注入跟踪器
        val imePackages = runCatching {
            getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.map { it.packageName }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())
        ForegroundTracker.attach(imePackages)
        // 无障碍可能是用户唯一开启的入口：顺手把采样服务拉起来，避免「球出来了但不记录」
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, QptMonitorService::class.java),
            )
        }
    }

    override fun attachOverlay() {
        if (overlayAttached) return
        overlayAttached = true
        overlay.attach()
    }

    override fun detachOverlay() {
        if (!overlayAttached) return
        overlayAttached = false
        controller?.detach()
    }

    /**
     * 只取窗口状态变更事件里的包名/类名喂给前台跟踪器，其余事件一律忽略。
     *
     * 不解构 `event.source`、不读节点树 —— `canRetrieveWindowContent=false` 保证了
     * 即使想读也读不到，这里刻意不申请该能力。
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        ForegroundTracker.onWindowStateChanged(event.packageName, event.className)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        OverlayBridge.unregisterAccessibility(this)
        ForegroundTracker.detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        OverlayBridge.unregisterAccessibility(this)
        ForegroundTracker.detach()
        controller = null
        scope.cancel()
        super.onDestroy()
    }
}
