package io.github.rusheng920.wattscope.service

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import io.github.rusheng920.wattscope.QptApplication
import io.github.rusheng920.wattscope.R
import io.github.rusheng920.wattscope.domain.BubbleSize
import io.github.rusheng920.wattscope.domain.OsdSample
import io.github.rusheng920.wattscope.service.overlay.GlassBubbleView
import io.github.rusheng920.wattscope.service.overlay.OsdCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class QptMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var bubble: GlassBubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var panelLiveText: TextView? = null
    private var panelQptText: TextView? = null
    private var panelBubbleText: TextView? = null
    private var panelOsdText: TextView? = null
    private var osdCard: OsdCardView? = null
    private var osdParams: WindowManager.LayoutParams? = null
    private var bubbleShownAtMs = 0L
    private var osdSampleJob: Job? = null
    private var panelLiveJob: Job? = null
    private var lastTap = 0L

    /** 浮层位置/透明度等纯窗口状态，不参与业务持久化，单独存一个私有偏好。 */
    private val overlayPrefs by lazy { getSharedPreferences("overlay_state", Context.MODE_PRIVATE) }

    private val repository by lazy { (application as QptApplication).repository }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService()!!
        createChannel()
        startForeground(1, notification("悬浮窗运行中"))
        // 先显示气泡（若偏好开启），设备状态刷新并行进行
        if (Settings.canDrawOverlays(this) && repository.bubbleEnabled) {
            showBubble()
        }
        observeRecordingState()
        observeBubbleSize()
        scope.launch {
            repository.refreshDeviceState()
            if (Settings.canDrawOverlays(this@QptMonitorService) && repository.osdEnabled) showOsd()
            observeOsdEnabled()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> if (Settings.canDrawOverlays(this) && repository.bubbleEnabled) showBubble()
            ACTION_HIDE_BUBBLE -> hideBubble()
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
        hideBubble(immediate = true)
        hidePanel()
        hideOsd()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- OSD 悬浮窗（两列 HUD，可拖动、轻点切换透明度、不记录） ----------

    private fun showOsd() {
        if (osdCard != null) return
        if (!Settings.canDrawOverlays(this)) {
            toast("请先在权限中心授予悬浮窗权限")
            return
        }
        val density = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        // 两列排版（标签 + 右对齐数值）比单行拼串更宽：至少 140dp，常见屏幕上取 1/3.8 屏宽
        val width = max((140 * density).toInt(), (screenW / 3.8f).toInt())
        val card = OsdCardView(this)
        card.setMetrics(repository.osdMetricsFlow.value)
        card.alpha = osdAlpha()

        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = overlayPrefs.getInt(KEY_OSD_X, screenW - width - (12 * density).toInt())
            y = overlayPrefs.getInt(KEY_OSD_Y, (100 * density).toInt())
            x = x.coerceIn(0, max(0, screenW - width))
            y = y.coerceIn(0, max(0, screenH - (120 * density).toInt()))
        }
        card.setOnTouchListener(OsdTouchListener(params))
        windowManager.addView(card, params)
        osdCard = card
        osdParams = params
        observeOsdSample()
    }

    private fun hideOsd() {
        // 必须取消订阅：否则每次「关→开」都会多留一个永不退出的 collector
        osdSampleJob?.cancel()
        osdSampleJob = null
        osdCard?.let { runCatching { windowManager.removeView(it) } }
        osdCard = null
        osdParams = null
    }

    private fun observeOsdSample() {
        osdSampleJob?.cancel()
        osdSampleJob = scope.launch {
            combine(
                repository.osdSample,
                repository.osdMetricsFlow,
                repository.state.map { it.isRecording }.distinctUntilChanged(),
            ) { sample, metrics, recording -> Triple(sample, metrics, recording) }
                .collect { (sample, metrics, recording) ->
                    val card = osdCard ?: return@collect
                    card.setMetrics(metrics)
                    card.bind(sample, recording)
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

    /** OSD 三档透明度：全不透明 / 半透 / 极淡，轻点循环切换，方便避开被遮挡的内容。 */
    private val osdAlphas = floatArrayOf(1f, 0.74f, 0.46f)

    private fun osdAlpha(): Float = osdAlphas[overlayPrefs.getInt(KEY_OSD_ALPHA, 0).coerceIn(0, osdAlphas.lastIndex)]

    private fun cycleOsdAlpha() {
        val next = (overlayPrefs.getInt(KEY_OSD_ALPHA, 0) + 1) % osdAlphas.size
        overlayPrefs.edit().putInt(KEY_OSD_ALPHA, next).apply()
        osdCard?.animate()?.alpha(osdAlphas[next])?.setDuration(160)?.start()
    }

    // ---------- 悬浮球 ----------

    private fun showBubble() {
        val existing = bubble
        if (existing != null) {
            applyBubbleSize(existing)
            return
        }
        bubbleShownAtMs = System.currentTimeMillis()
        val density = resources.displayMetrics.density
        val size = bubbleSizePx(repository.bubbleSize)
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = overlayPrefs.getInt(KEY_BUBBLE_X, (16 * density).toInt())
            y = overlayPrefs.getInt(KEY_BUBBLE_Y, (220 * density).toInt())
            x = x.coerceIn(0, max(0, screenW - size))
            y = y.coerceIn(0, max(0, screenH - size))
        }
        val view = GlassBubbleView(this).apply {
            mode = if (repository.state.value.isRecording) GlassBubbleView.Mode.RECORDING else GlassBubbleView.Mode.IDLE
            setOnTouchListener(BubbleTouchListener(params))
        }
        windowManager.addView(view, params)
        bubble = view
        bubbleParams = params
        scheduleIdleFade()
    }

    private fun hideBubble(immediate: Boolean = false) {
        val view = bubble ?: return
        bubble = null
        bubbleParams = null
        view.removeCallbacks(idleFade)
        view.animate().cancel()
        if (immediate) {
            runCatching { windowManager.removeView(view) }
        } else {
            view.animate().alpha(0f).setDuration(140).withEndAction {
                runCatching { windowManager.removeView(view) }
            }.start()
        }
    }

    private fun bubbleSizePx(size: BubbleSize): Int =
        (size.sizeDp * resources.displayMetrics.density).roundToInt()

    /** 尺寸偏好变化时原地 resize：保持球心位置不跳，重新夹取到屏幕内。 */
    private fun applyBubbleSize(view: GlassBubbleView) {
        val params = bubbleParams ?: return
        val size = bubbleSizePx(repository.bubbleSize)
        if (params.width == size && params.height == size) return
        val centerX = params.x + params.width / 2
        val centerY = params.y + params.height / 2
        params.width = size
        params.height = size
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        params.x = (centerX - size / 2).coerceIn(0, max(0, screenW - size))
        params.y = (centerY - size / 2).coerceIn(0, max(0, screenH - size))
        windowManager.updateViewLayout(view, params)
        persistBubblePosition(params)
    }

    private fun observeBubbleSize() {
        scope.launch {
            repository.bubbleSizeFlow.collect { size ->
                val view = bubble ?: return@collect
                // 立即按新档位应用（Flow 首次发射时也会执行，等价于初始化）
                val params = bubbleParams ?: return@collect
                val px = bubbleSizePx(size)
                if (params.width == px && params.height == px) return@collect
                applyBubbleSize(view)
            }
        }
    }

    private fun persistBubblePosition(params: WindowManager.LayoutParams) {
        overlayPrefs.edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply()
    }

    /** 订阅记录状态：球体图标与状态环随记录状态切换。 */
    private fun observeRecordingState() {
        scope.launch {
            repository.state.map { it.isRecording }.distinctUntilChanged().collect { recording ->
                bubble?.mode = if (recording) GlassBubbleView.Mode.RECORDING else GlassBubbleView.Mode.IDLE
                if (recording) {
                    // 记录中不淡出：状态要一直看得见
                    bubble?.animate()?.alpha(1f)?.setDuration(160)?.start()
                } else {
                    scheduleIdleFade()
                }
            }
        }
    }

    /**
     * 闲置淡出：不记录且 7 秒没有操作时把悬浮球降到 55% 不透明度，
     * 既保留可发现性，又不会在阅读/游戏时抢注意力。触摸即恢复。
     */
    private fun scheduleIdleFade() {
        val view = bubble ?: return
        view.removeCallbacks(idleFade)
        view.postDelayed(idleFade, 7_000)
    }

    private val idleFade = Runnable {
        if (repository.state.value.isRecording) return@Runnable
        bubble?.animate()?.alpha(0.55f)?.setDuration(340)?.start()
    }

    private fun wakeBubble() {
        val view = bubble ?: return
        view.removeCallbacks(idleFade)
        if (view.alpha < 1f) view.animate().alpha(1f).setDuration(140).start()
    }

    private fun toggleRecording() {
        scope.launch {
            if (repository.state.value.isRecording) {
                repository.stopRecording()
                toast("停止记录")
            } else {
                // 悬浮球启动记录：记录当前应用，离开该应用时自动停止
                val ok = repository.startRecording(autoStopOnAppExit = true)
                toast(if (ok) "开始记录" else "无法开始记录：无可用监控源")
            }
        }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    // ---------- 通知快捷控制面板（悬浮窗形式） ----------

    private fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    private fun showPanel() {
        if (panel != null) return
        // 通知点开面板时若授权已被系统回收，直接 addView 会抛 SecurityException
        if (!Settings.canDrawOverlays(this)) {
            toast("请先在权限中心授予悬浮窗权限")
            return
        }
        val density = resources.displayMetrics.density
        val width = (300 * density).toInt()
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (140 * density).toInt()
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt())
            background = cardBackground(radiusDp = 24f)
        }

        root.addView(
            TextView(this).apply {
                text = "WattScope 控制"
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(0xFFF2F6FA.toInt())
                includeFontPadding = false
            },
        )
        panelLiveText = TextView(this).apply {
            text = "读取实时数据…"
            textSize = 12f
            setTextColor(0xFF7FD9A8.toInt())
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            setPadding(0, (6 * density).toInt(), 0, 0)
        }
        root.addView(panelLiveText)

        root.addView(
            View(this).apply { setBackgroundColor(0x1FFFFFFF) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = (14 * density).toInt()
            },
        )

        panelQptText = panelRow(root, "QPT 总开关", density) {
            scope.launch {
                repository.setQptEnabled(!repository.state.value.qptEnabled)
                refreshPanelState()
            }
        }
        panelBubbleText = panelRow(root, "悬浮球", density) {
            val enabled = !repository.bubbleEnabled
            repository.bubbleEnabled = enabled
            startService(
                Intent(this@QptMonitorService, QptMonitorService::class.java)
                    .setAction(if (enabled) ACTION_SHOW_BUBBLE else ACTION_HIDE_BUBBLE),
            )
            refreshPanelState()
        }
        panelOsdText = panelRow(root, "OSD 浮层", density) {
            repository.osdEnabled = !repository.osdEnabled
            if (repository.osdEnabled) showOsd() else hideOsd()
            refreshPanelState()
        }

        root.addView(
            TextView(this).apply {
                text = "关闭面板"
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTextColor(0xFFCAD6E1.toInt())
                background = pillBackground(density, filled = false)
                isClickable = true
                isFocusable = true
                setPadding(0, (11 * density).toInt(), 0, (11 * density).toInt())
                setOnClickListener { hidePanel() }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (12 * density).toInt()
            },
        )

        // 液态玻璃只用于应用内底栏：浮层保持纯色，不做实时模糊，
        // 避免 GPU 持续工作影响功耗测量精度，也避开 OEM 对浮层模糊支持不一致的问题。
        windowManager.addView(root, params)
        panel = root
        refreshPanelState()
        bindPanelLive()
    }

    /** 一行「标题 —— 状态」的胶囊按钮，整行可点。 */
    private fun panelRow(root: LinearLayout, title: String, density: Float, onClick: () -> Unit): TextView {
        val row = TextView(this).apply {
            text = title
            textSize = 13.5f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(0xFFF2F7FB.toInt())
            background = pillBackground(density, filled = true)
            isClickable = true
            isFocusable = true
            setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            setOnClickListener { onClick() }
        }
        root.addView(
            row,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * density).toInt()
            },
        )
        return row
    }

    private fun hidePanel() {
        panelLiveJob?.cancel()
        panelLiveJob = null
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        panelLiveText = null
        panelQptText = null
        panelBubbleText = null
        panelOsdText = null
    }

    /** 面板顶部实时读数：确认 QPT 是否真的在出数，比只显示开关状态有用。 */
    private fun bindPanelLive() {
        panelLiveJob?.cancel()
        panelLiveJob = scope.launch {
            combine(
                repository.osdSample,
                repository.state.map { it.isRecording }.distinctUntilChanged(),
            ) { sample, recording -> sample to recording }
                .collect { (sample, recording) ->
                    val text = panelLiveText ?: return@collect
                    text.text = renderLiveLine(sample, recording)
                }
        }
    }

    private fun renderLiveLine(sample: OsdSample?, recording: Boolean): String {
        val prefix = if (recording) "● REC" else "○ 待机"
        val soc = sample?.socPowerUw?.let { "%.2f W".format(it / 1_000_000.0) }
        val fps = sample?.fps?.let { "%.0f fps".format(it) }
        val parts = listOfNotNull(soc, fps)
        return if (parts.isEmpty()) "$prefix · 等待数据" else "$prefix · ${parts.joinToString(" · ")}"
    }

    private fun refreshPanelState() {
        qptStatus(panelQptText, "QPT 总开关", repository.state.value.qptEnabled)
        qptStatus(panelBubbleText, "悬浮球", repository.bubbleEnabled)
        qptStatus(panelOsdText, "OSD 浮层", repository.osdEnabled)
    }

    private fun qptStatus(view: TextView?, title: String, on: Boolean) {
        view?.text = "$title  ${if (on) "已开启" else "已关闭"}"
    }

    // ---------- 背景与触摸 ----------

    private fun cardBackground(radiusDp: Float): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(0xF7, 0x12, 0x19, 0x1F), Color.argb(0xF4, 0x08, 0x0C, 0x10)),
    ).apply {
        cornerRadius = radiusDp * resources.displayMetrics.density
        setStroke(
            (1 * resources.displayMetrics.density).roundToInt(),
            Color.argb(0x2E, 0xFF, 0xFF, 0xFF),
        )
    }

    /** 面板按钮：半透明白底 + 可按压水波纹，和深色面板同一套语言。 */
    private fun pillBackground(density: Float, filled: Boolean): android.graphics.drawable.Drawable {
        val shape = GradientDrawable().apply {
            cornerRadius = 16 * density
            setColor(if (filled) Color.argb(0x1F, 0xFF, 0xFF, 0xFF) else Color.argb(0x0F, 0xFF, 0xFF, 0xFF))
            setStroke((1 * density).roundToInt(), Color.argb(0x24, 0xFF, 0xFF, 0xFF))
        }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(0x33, 0xFF, 0xFF, 0xFF)), shape, null)
    }

    private inner class BubbleTouchListener(private val params: WindowManager.LayoutParams) :
        View.OnTouchListener {
        private var downX = 0
        private var downY = 0
        private var rawX = 0f
        private var rawY = 0f
        private var moved = false
        private var downAt = 0L

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = params.x
                    downY = params.y
                    rawX = event.rawX
                    rawY = event.rawY
                    moved = false
                    downAt = System.currentTimeMillis()
                    wakeBubble()
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                    // 必须消费 DOWN，否则后续 MOVE/UP 不会派发给悬浮窗
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - rawX).toInt()
                    val dy = (event.rawY - rawY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) moved = true
                    if (moved) {
                        val screenW = resources.displayMetrics.widthPixels
                        val screenH = resources.displayMetrics.heightPixels
                        params.x = (downX + dx).coerceIn(0, max(0, screenW - params.width))
                        params.y = (downY + dy).coerceIn(0, max(0, screenH - params.height))
                        windowManager.updateViewLayout(v, params)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                    val now = System.currentTimeMillis()
                    // 忽略气泡刚创建时的异常触摸（窗口添加时的幽灵事件）
                    if (now - bubbleShownAtMs < 1_500) return true
                    if (moved) {
                        snapToEdge(v, params)
                    } else if (now - downAt >= 420) {
                        showPanel()
                    } else if (now - lastTap > 320) {
                        lastTap = now
                        toggleRecording()
                    }
                    scheduleIdleFade()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                    if (moved) snapToEdge(v, params)
                    return true
                }
            }
            return false
        }
    }

    /**
     * 松手后吸附到最近的左右边缘（仅在设置项「松手吸附边缘」开启时生效）。
     *
     * 这是「不挡内容」的一种做法：球停在边缘时，正文永远从它旁边流过。
     * 但它会强制把球拉到屏幕左右边缘，中间区域完全放不下 —— 默认关闭，
     * 让用户可以把球停在任意想要的位置；位置同样写进偏好，下次开机不会跑掉。
     */
    private fun snapToEdge(v: View, params: WindowManager.LayoutParams) {
        if (!repository.bubbleSnapToEdge) {
            // 未开启吸附：保留用户松手时的位置，仅持久化
            persistBubblePosition(params)
            return
        }
        val screenW = resources.displayMetrics.widthPixels
        val margin = (8 * resources.displayMetrics.density).roundToInt()
        val target = if (params.x + params.width / 2f < screenW / 2f) margin else screenW - params.width - margin
        if (target == params.x) {
            persistBubblePosition(params)
            return
        }
        val animator = ValueAnimator.ofInt(params.x, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                windowManager.updateViewLayout(v, params)
            }
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                persistBubblePosition(params)
            }
        })
        animator.start()
    }

    /** OSD 拖拽监听：拖动改位置，轻点循环切换透明度。 */
    private inner class OsdTouchListener(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
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
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - rawX).toInt()
                    val dy = (event.rawY - rawY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    val screenW = resources.displayMetrics.widthPixels
                    val screenH = resources.displayMetrics.heightPixels
                    params.x = (downX + dx).coerceIn(0, max(0, screenW - v.width))
                    params.y = (downY + dy).coerceIn(0, max(0, screenH - v.height))
                    windowManager.updateViewLayout(v, params)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        overlayPrefs.edit().putInt(KEY_OSD_X, params.x).putInt(KEY_OSD_Y, params.y).apply()
                    } else {
                        cycleOsdAlpha()
                    }
                    return true
                }
            }
            return true
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, ChannelId)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
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
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val KEY_OSD_X = "osd_x"
        private const val KEY_OSD_Y = "osd_y"
        private const val KEY_OSD_ALPHA = "osd_alpha"
        const val ACTION_SHOW_BUBBLE = "io.github.rusheng920.wattscope.action.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "io.github.rusheng920.wattscope.action.HIDE_BUBBLE"
        const val ACTION_TOGGLE_PANEL = "io.github.rusheng920.wattscope.action.TOGGLE_PANEL"
        const val ACTION_TOGGLE_OSD = "io.github.rusheng920.wattscope.action.TOGGLE_OSD"
        const val ACTION_SYNC_OVERLAYS = "io.github.rusheng920.wattscope.action.SYNC_OVERLAYS"
    }
}
