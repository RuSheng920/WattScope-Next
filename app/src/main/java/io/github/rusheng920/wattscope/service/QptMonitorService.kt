package io.github.rusheng920.wattscope.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import io.github.rusheng920.wattscope.QptApplication
import io.github.rusheng920.wattscope.R
import io.github.rusheng920.wattscope.core.keepalive.FreezeGuard
import io.github.rusheng920.wattscope.service.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 采样与常驻宿主。
 *
 * 职责收敛为三件事：**前台服务通知**（防被杀）、**设备状态刷新**、**冻结守卫编排**。
 * 悬浮球 / OSD / 控制面板的窗口逻辑已全部搬到 [OverlayController]，由
 * [OverlayBridge] 决定当前挂在普通服务还是无障碍服务上 —— 无障碍优先。
 *
 * 窗口由谁承载不影响采样：采样协程始终跑在本服务所在的进程里。
 */
class QptMonitorService : Service(), OverlayBridge.Host {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: OverlayController? = null
    private var overlayAttached = false
    private var freezeGuardJob: Job? = null
    private var freezeGuard: FreezeGuard? = null

    private val repository by lazy { (application as QptApplication).repository }

    override val overlay: OverlayController
        get() = controller ?: OverlayController(
            context = this,
            repository = repository,
            scope = scope,
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        ).also { controller = it }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat()
        OverlayBridge.registerService(this)
        observeFreezeGuard()
        scope.launch {
            repository.refreshDeviceState()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台的兜底：START_STICKY 重启后系统可能不重发通知
        startForegroundCompat()
        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> if (repository.bubbleEnabled) OverlayBridge.current?.overlay?.showBubble()
            ACTION_HIDE_BUBBLE -> OverlayBridge.current?.overlay?.hideBubble()
            ACTION_TOGGLE_PANEL -> OverlayBridge.current?.overlay?.togglePanel()
            ACTION_TOGGLE_OSD -> repository.osdEnabled = !repository.osdEnabled
            ACTION_SYNC_OVERLAYS -> syncOverlays()
        }
        // 不再自动跳转悬浮窗授权页：授权统一由首次引导 / 设置里的权限中心发起
        return START_STICKY
    }

    /** 从系统授权页返回后调用：把已授权的悬浮球 / OSD 补显示出来，无需重启服务。 */
    private fun syncOverlays() {
        val target = OverlayBridge.current?.overlay ?: return
        if (repository.bubbleEnabled) target.showBubble()
        if (repository.osdEnabled) target.showOsd()
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
     * 订阅冻结守卫开关。
     *
     * 守卫本体是 root 侧的常驻 shell 进程（见 [FreezeGuard]），这里只做启停编排。
     * 没有 root 时 start() 静默返回 false，不会弹授权框 —— 授权入口仍然只有权限中心。
     */
    private fun observeFreezeGuard() {
        freezeGuardJob?.cancel()
        freezeGuardJob = scope.launch {
            repository.freezeGuardEnabledFlow.collect { enabled ->
                val guard = freezeGuard ?: FreezeGuard(this@QptMonitorService).also { freezeGuard = it }
                if (enabled) guard.start() else guard.stop()
            }
        }
    }

    override fun onDestroy() {
        OverlayBridge.unregisterService(this)
        // onDestroy 在主线程，root shell 调用会阻塞 —— 丢到 IO 上去收尾
        freezeGuard?.let { guard ->
            CoroutineScope(Dispatchers.IO).launch { guard.stop() }
        }
        freezeGuard = null
        controller = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 前台服务启动：Android 14 起必须显式声明并传入 foregroundServiceType。
     *
     * 这里刻意用 specialUse 而不是 dataSync —— dataSync 在 Android 15+ 有
     * 6 小时 / 24 小时的累计配额，耗尽后系统回调 onTimeout 并停掉服务。
     * specialUse 没有时长限制，代价是 manifest 里要写明子类型用途。
     */
    private fun startForegroundCompat() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(getString(R.string.notif_overlay_running)),
            type,
        )
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, ChannelId)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle("WattScope Next")
        .setContentText(text)
        .setOngoing(true)
        // 归类为 SERVICE + IMMEDIATE：让系统按「服务型前台通知」处理，
        // 后台冻结/降级判定时不会被当成普通通知随手掐掉。
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setShowWhen(false)
        // 点击通知打开悬浮窗控制面板（开/关 QPT、开/关悬浮窗）
        .setContentIntent(
            PendingIntent.getService(
                this,
                0,
                Intent(this, QptMonitorService::class.java).setAction(ACTION_TOGGLE_PANEL),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun createChannel() {
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(ChannelId, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val ChannelId = "qpt_monitor"
        private const val NOTIFICATION_ID = 1
        const val ACTION_SHOW_BUBBLE = "io.github.rusheng920.wattscope.action.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "io.github.rusheng920.wattscope.action.HIDE_BUBBLE"
        const val ACTION_TOGGLE_PANEL = "io.github.rusheng920.wattscope.action.TOGGLE_PANEL"
        const val ACTION_TOGGLE_OSD = "io.github.rusheng920.wattscope.action.TOGGLE_OSD"
        const val ACTION_SYNC_OVERLAYS = "io.github.rusheng920.wattscope.action.SYNC_OVERLAYS"
    }
}
