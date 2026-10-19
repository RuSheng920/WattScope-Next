package io.github.rusheng920.wattscope.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import io.github.rusheng920.wattscope.QptApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class QptMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var osdRoot: LinearLayout? = null
    private var osdText: TextView? = null
    private var qptStatusText: TextView? = null
    private var bubbleStatusText: TextView? = null
    private var lastTap = 0L
    private var bubbleShownAtMs = 0L
    private var menuMode = false
    private var osdSampleJob: Job? = null

    private val repository by lazy { (application as QptApplication).repository }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService()!!
        createChannel()
        startForeground(1, notification("QPT 悬浮窗运行中"))
        // 先显示气泡（若偏好开启），设备状态刷新并行进行
        if (Settings.canDrawOverlays(this) && repository.bubbleEnabled) {
            showBubble()
            observeRecordingState()
        }
        scope.launch {
            repository.refreshDeviceState()
            if (Settings.canDrawOverlays(this@QptMonitorService) && repository.osdEnabled) showOsd()
            observeOsdEnabled()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> if (Settings.canDrawOverlays(this) && repository.bubbleEnabled) showBubble()
            ACTION_HIDE_BUBBLE -> bubble?.let { windowManager.removeView(it); bubble = null }
            ACTION_TOGGLE_PANEL -> togglePanel()
            ACTION_TOGGLE_OSD -> {
                repository.osdEnabled = !repository.osdEnabled
                if (repository.osdEnabled) showOsd() else hideOsd()
            }
            ACTION_SYNC_OVERLAYS -> syncOverlays()
        }
        // 不再自动跳转悬浮窗授权页：授权统一由首次引导 / 设置里的权限中心发起
        return START_STICKY
    }

    /** 从系统授权页返回后调用：把已授权的悬浮球 / OSD 补显示出来，无需重启服务。 */
    private fun syncOverlays() {
        if (!Settings.canDrawOverlays(this)) return
        if (repository.bubbleEnabled) showBubble()
        if (repository.osdEnabled) showOsd()
    }

    override fun onDestroy() {
        bubble?.let { windowManager.removeView(it) }
        panel?.let { windowManager.removeView(it) }
        osdRoot?.let { windowManager.removeView(it) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- OSD 悬浮窗（竖排、≤1/3 屏宽、不记录） ----------

    private fun showOsd() {
        if (osdRoot != null) return
        val density = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        // 宽度约 1/4 屏：中文标签 + 数值需要比纯英文更宽，过窄会换行破坏对齐
        val width = (screenW / 4.3f).toInt()
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - width - (12 * density).toInt()
            y = (100 * density).toInt()
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
        }
        root.background = roundedBackground(radiusDp = 16f, alpha = 0xE0)
        osdText = TextView(this).apply {
            textSize = 11.5f
            setTextColor(0xFFEDF4FA.toInt())
            setShadowLayer(5f, 0f, 1f, 0xB3000000.toInt())
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD,
            )
            includeFontPadding = false
            setLineSpacing(3f * density, 1f)
        }
        root.addView(osdText)
        // 可拖动：移除 FLAG_NOT_TOUCHABLE 并挂接触摸监听
        root.setOnTouchListener(OsdTouchListener(params))
        windowManager.addView(root, params)
        osdRoot = root
        observeOsdSample()
    }

    private fun hideOsd() {
        // 必须取消订阅：否则每次「关→开」都会多留一个永不退出的 collector
        osdSampleJob?.cancel()
        osdSampleJob = null
        osdRoot?.let { windowManager.removeView(it); osdRoot = null }
        osdText = null
    }

    private fun observeOsdSample() {
        osdSampleJob?.cancel()
        osdSampleJob = scope.launch {
            kotlinx.coroutines.flow.combine(repository.osdSample, repository.osdMetricsFlow) { sample, metrics ->
                sample to metrics
            }.collect { (sample, metrics) ->
                osdText?.text = renderOsd(sample, metrics)
            }
        }
    }

    private fun observeOsdEnabled() {
        scope.launch {
            repository.osdEnabledFlow.collect { enabled ->
                if (!Settings.canDrawOverlays(this@QptMonitorService)) return@collect
                if (enabled) showOsd() else hideOsd()
            }
        }
    }

    private fun renderOsd(sample: io.github.rusheng920.wattscope.domain.OsdSample?, metrics: List<io.github.rusheng920.wattscope.domain.OsdMetric>): String =
        metrics.mapNotNull { sample?.format(it) }.joinToString("\n").ifEmpty { "OSD\n等待数据…" }

    private fun showBubble() {
        if (bubble != null) return
        bubbleShownAtMs = System.currentTimeMillis()
        val density = resources.displayMetrics.density
        val size = (52 * density).toInt()
        val params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 220
        }
        bubble = TextView(this).apply {
            text = "▶"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
            background = roundedBackground(radiusDp = size / 2f / density, alpha = 0xE6)
            setOnTouchListener(BubbleTouchListener(params))
        }
        windowManager.addView(bubble, params)
    }

    /** 订阅记录状态，自动停止（离开应用）后气泡图标恢复 "▶"。 */
    private fun observeRecordingState() {
        scope.launch {
            repository.state.map { it.isRecording }.distinctUntilChanged().collect { recording ->
                bubble?.text = if (menuMode) "QPT" else if (recording) "■" else "▶"
            }
        }
    }

    private fun toggleRecording() {
        scope.launch {
            if (repository.state.value.isRecording) {
                repository.stopRecording()
                bubble?.text = if (menuMode) "QPT" else "▶"
                toast("停止记录")
            } else {
                // 悬浮球启动记录：记录当前应用，离开该应用时自动停止
                val ok = repository.startRecording(autoStopOnAppExit = true)
                if (ok) {
                    bubble?.text = if (menuMode) "QPT" else "■"
                    toast("开始记录")
                } else {
                    toast("无法开始记录：无可用监控源")
                }
            }
        }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun expandQuickMenu() {
        menuMode = bubble?.text == "QPT"
        bubble?.text = if (menuMode) {
            if (repository.state.value.isRecording) "■" else "▶"
        } else {
            "QPT"
        }
    }

    // ---------- 通知快捷控制面板（悬浮窗形式） ----------

    private fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    private fun showPanel() {
        if (panel != null) return
        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (300 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (160 * density).toInt()
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt())
        }
        root.background = roundedBackground(radiusDp = 24f, alpha = 0xE6)

        root.addView(TextView(this).apply {
            text = "QPT 控制"
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setShadowLayer(4f, 0f, 1f, 0x80000000.toInt())
        })

        qptStatusText = panelStatusText()
        root.addView(qptStatusText, lpWithMargin(density, 12))
        root.addView(glassButton("切换 QPT 开关") {
            scope.launch {
                repository.setQptEnabled(!repository.state.value.qptEnabled)
                qptStatusText?.text = if (repository.state.value.qptEnabled) "QPT：已开启" else "QPT：已关闭"
            }
        }, lpWithMargin(density, 4))

        bubbleStatusText = panelStatusText()
        root.addView(bubbleStatusText, lpWithMargin(density, 12))
        root.addView(glassButton("切换悬浮球") {
            val enabled = !repository.bubbleEnabled
            repository.bubbleEnabled = enabled
            bubbleStatusText?.text = if (enabled) "悬浮球：已开启" else "悬浮球：已关闭"
            startService(
                Intent(this@QptMonitorService, QptMonitorService::class.java)
                    .setAction(if (enabled) ACTION_SHOW_BUBBLE else ACTION_HIDE_BUBBLE),
            )
        }, lpWithMargin(density, 4))

        root.addView(glassButton("切换 OSD 悬浮窗") {
            repository.osdEnabled = !repository.osdEnabled
            if (repository.osdEnabled) showOsd() else hideOsd()
            toast(if (repository.osdEnabled) "OSD 悬浮窗已开启" else "OSD 悬浮窗已关闭")
        }, lpWithMargin(density, 12))

        root.addView(glassButton("关闭面板") { hidePanel() }, lpWithMargin(density, 10))

        // 液态玻璃只用于应用内底栏：浮层保持纯色，不做实时模糊，
        // 避免 GPU 持续工作影响功耗测量精度，也避开 OEM 对浮层模糊支持不一致的问题。
        windowManager.addView(root, params)
        panel = root
        refreshPanelState()
    }

    private fun panelStatusText() = TextView(this).apply {
        textSize = 12.5f
        gravity = Gravity.CENTER
        setTextColor(0xFFC3D1DE.toInt())
    }

    /** 面板内的胶囊按钮：与卡片同一套语言，避免混入系统默认 Holo 按钮。 */
    private fun glassButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 13.5f
        gravity = Gravity.CENTER
        setTextColor(0xFFF2F7FB.toInt())
        background = roundedBackground(radiusDp = 22f, alpha = 0xB3)
        isClickable = true
        isFocusable = true
        setPadding(0, (11 * resources.displayMetrics.density).toInt(), 0, (11 * resources.displayMetrics.density).toInt())
        setOnClickListener { onClick() }
    }

    /**
     * 浮层背景：纯色半透明 + 1dp 亮边 + 圆角。
     *
     * 液态玻璃只在应用内底栏使用；OSD/悬浮球是常驻浮层，保持纯色更省 GPU，
     * 也不会让逐帧重绘的渐变影响功耗测量。
     */
    private fun roundedBackground(radiusDp: Float, alpha: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * resources.displayMetrics.density
            setColor(Color.argb(alpha, 14, 21, 24))
            setStroke((1 * resources.displayMetrics.density).roundToInt(), Color.argb(46, 255, 255, 255))
        }

    private fun hidePanel() {
        panel?.let { windowManager.removeView(it); panel = null }
    }

    private fun refreshPanelState() {
        scope.launch {
            repository.refreshDeviceState()
            qptStatusText?.text = if (repository.state.value.qptEnabled) "QPT：已开启" else "QPT：已关闭"
            bubbleStatusText?.text = if (repository.bubbleEnabled) "悬浮窗：已开启" else "悬浮窗：已关闭"
        }
    }

    private fun lpWithMargin(density: Float, top: Int) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (top * density).toInt()
        }

    // ---------- 悬浮球触摸 ----------

    private inner class BubbleTouchListener(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private var downX = 0
        private var downY = 0
        private var rawX = 0f
        private var rawY = 0f
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = params.x
                    downY = params.y
                    rawX = event.rawX
                    rawY = event.rawY
                    moved = false
                    // 必须消费 DOWN，否则后续 MOVE/UP 不会派发给悬浮窗
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - rawX).toInt()
                    val dy = (event.rawY - rawY).toInt()
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) moved = true
                    params.x = downX + dx
                    params.y = downY + dy
                    windowManager.updateViewLayout(v, params)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val now = System.currentTimeMillis()
                        // 忽略气泡刚创建时的异常触摸（窗口添加时的幽灵事件）
                        if (now - bubbleShownAtMs < 2_000) return true
                        if (now - lastTap < 300) expandQuickMenu() else toggleRecording()
                        lastTap = now
                    }
                    return moved
                }
            }
            return false
        }
    }

    /** OSD 悬浮窗拖拽监听：仅拖动改变位置，无点击动作。 */
    private inner class OsdTouchListener(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private var downX = 0
        private var downY = 0
        private var rawX = 0f
        private var rawY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = params.x
                    downY = params.y
                    rawX = event.rawX
                    rawY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = downX + (event.rawX - rawX).toInt()
                    params.y = downY + (event.rawY - rawY).toInt()
                    windowManager.updateViewLayout(v, params)
                    return true
                }
            }
            return true
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, ChannelId)
        .setSmallIcon(android.R.drawable.ic_menu_manage)
        .setContentTitle("WattScope Next")
        .setContentText(text)
        .setOngoing(true)
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
            NotificationChannel(ChannelId, "QPT 功耗监控", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val ChannelId = "qpt_monitor"
        const val ACTION_SHOW_BUBBLE = "io.github.rusheng920.wattscope.action.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "io.github.rusheng920.wattscope.action.HIDE_BUBBLE"
        const val ACTION_TOGGLE_PANEL = "io.github.rusheng920.wattscope.action.TOGGLE_PANEL"
        const val ACTION_TOGGLE_OSD = "io.github.rusheng920.wattscope.action.TOGGLE_OSD"
        const val ACTION_SYNC_OVERLAYS = "io.github.rusheng920.wattscope.action.SYNC_OVERLAYS"
    }
}
