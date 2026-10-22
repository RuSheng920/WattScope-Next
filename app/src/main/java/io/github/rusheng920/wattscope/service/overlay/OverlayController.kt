package io.github.rusheng920.wattscope.service.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.getSystemService
import io.github.rusheng920.wattscope.R
import io.github.rusheng920.wattscope.data.repo.MonitorRepository
import io.github.rusheng920.wattscope.domain.BubbleSize
import io.github.rusheng920.wattscope.domain.OsdSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 悬浮层控制器：悬浮球 / OSD / 通知面板三块窗口的完整生命周期。
 *
 * 从 [io.github.rusheng920.wattscope.service.QptMonitorService] 里抽出来，是为了让**两套宿主**
 * 共用同一份实现：
 *
 * - 普通前台服务（无障碍未开启时的兜底）→ [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 * - 无障碍服务（推荐路径）→ [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]
 *
 * 为什么无障碍宿主更能扛冻结：无障碍服务由 system_server 绑定，进程状态永远不是 `CACHED`，
 * ROM 的墓碑 / 应用速冻不会碰它；而且 ACCESSIBILITY_OVERLAY 层级由无障碍通道托管，
 * 在游戏里不会被吞掉触摸事件。另外这一层窗口**不需要** SYSTEM_ALERT_WINDOW 授权。
 *
 * @param windowType 窗口类型。决定是否需要悬浮窗权限，也决定触摸通道。
 */
class OverlayController(
    private val context: Context,
    private val repository: MonitorRepository,
    private val scope: CoroutineScope,
    private val windowType: Int,
) {

    private val windowManager: WindowManager = context.getSystemService<WindowManager>()!!

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
    private var recordingJob: Job? = null
    private var bubbleSizeJob: Job? = null
    private var osdEnabledJob: Job? = null
    private var lastTap = 0L

    /** 浮层位置/透明度等纯窗口状态，不参与业务持久化，单独存一个私有偏好。 */
    private val overlayPrefs by lazy { context.getSharedPreferences("overlay_state", Context.MODE_PRIVATE) }

    /** 应用叠加层需要悬浮窗授权；无障碍叠加层不需要。 */
    private val needsOverlayPermission: Boolean
        get() = windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun canShow(): Boolean = !needsOverlayPermission || Settings.canDrawOverlays(context)

    private val density: Float get() = context.resources.displayMetrics.density
    private val screenW: Int get() = context.resources.displayMetrics.widthPixels
    private val screenH: Int get() = context.resources.displayMetrics.heightPixels

    /** 宿主就绪后调用：拉起偏好订阅并补显示已开启的浮层。 */
    fun attach() {
        observeRecordingState()
        observeBubbleSize()
        if (canShow() && repository.bubbleEnabled) showBubble()
        observeOsdEnabled()
        if (canShow() && repository.osdEnabled) showOsd()
    }

    /** 宿主销毁时调用：撤掉全部窗口与订阅。 */
    fun detach() {
        recordingJob?.cancel()
        bubbleSizeJob?.cancel()
        osdEnabledJob?.cancel()
        hideBubble(immediate = true)
        hidePanel()
        hideOsd()
    }

    // ---------- OSD 悬浮窗（两列 HUD，可拖动、轻点切换透明度、不记录） ----------

    fun showOsd() {
        if (osdCard != null) return
        if (!canShow()) {
            toast(R.string.toast_need_overlay)
            return
        }
        // 两列排版（标签 + 右对齐数值）比单行拼串更宽：至少 140dp，常见屏幕上取 1/3.8 屏宽
        val width = max((140 * density).toInt(), (screenW / 3.8f).toInt())
        val card = OsdCardView(context)
        card.setMetrics(repository.osdMetricsFlow.value)
        card.alpha = osdAlpha()

        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
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
        runCatching { windowManager.addView(card, params) }.onFailure { return }
        osdCard = card
        osdParams = params
        observeOsdSample()
    }

    fun hideOsd() {
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
                    // 计时以记录真实起始时刻为基准：先开记录再开 OSD 时不会从零重算
                    val elapsed = if (recording) {
                        repository.activeRecordingStartMs()?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) } ?: 0L
                    } else {
                        0L
                    }
                    card.bind(sample, recording, elapsed)
                }
        }
    }

    private fun observeOsdEnabled() {
        osdEnabledJob?.cancel()
        osdEnabledJob = scope.launch {
            repository.osdEnabledFlow.collect { enabled ->
                if (!canShow()) return@collect
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

    fun showBubble() {
        val existing = bubble
        if (existing != null) {
            applyBubbleSize(existing)
            return
        }
        if (!canShow()) return
        bubbleShownAtMs = System.currentTimeMillis()
        val size = bubbleSizePx(repository.bubbleSize)
        val params = WindowManager.LayoutParams(
            size,
            size,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = overlayPrefs.getInt(KEY_BUBBLE_X, (16 * density).toInt())
            y = overlayPrefs.getInt(KEY_BUBBLE_Y, (220 * density).toInt())
            x = x.coerceIn(0, max(0, screenW - size))
            y = y.coerceIn(0, max(0, screenH - size))
        }
        val view = GlassBubbleView(context).apply {
            mode = if (repository.state.value.isRecording) GlassBubbleView.Mode.RECORDING else GlassBubbleView.Mode.IDLE
            setOnTouchListener(BubbleTouchListener(params))
        }
        runCatching { windowManager.addView(view, params) }.onFailure { return }
        bubble = view
        bubbleParams = params
        scheduleIdleFade()
    }

    fun hideBubble(immediate: Boolean = false) {
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

    private fun bubbleSizePx(size: BubbleSize): Int = (size.sizeDp * density).roundToInt()

    /** 尺寸偏好变化时原地 resize：保持球心位置不跳，重新夹取到屏幕内。 */
    private fun applyBubbleSize(view: GlassBubbleView) {
        val params = bubbleParams ?: return
        val size = bubbleSizePx(repository.bubbleSize)
        if (params.width == size && params.height == size) return
        val centerX = params.x + params.width / 2
        val centerY = params.y + params.height / 2
        params.width = size
        params.height = size
        params.x = (centerX - size / 2).coerceIn(0, max(0, screenW - size))
        params.y = (centerY - size / 2).coerceIn(0, max(0, screenH - size))
        windowManager.updateViewLayout(view, params)
        persistBubblePosition(params)
    }

    private fun observeBubbleSize() {
        bubbleSizeJob?.cancel()
        bubbleSizeJob = scope.launch {
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
        recordingJob?.cancel()
        recordingJob = scope.launch {
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
                toast(R.string.action_stop_recording)
            } else {
                // 悬浮球启动记录：记录当前应用，离开该应用时自动停止
                val ok = repository.startRecording(autoStopOnAppExit = true)
                toast(if (ok) R.string.action_start_recording else R.string.toast_cannot_start)
            }
        }
    }

    // ---------- 通知快捷控制面板（悬浮窗形式） ----------

    fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    fun showPanel() {
        if (panel != null) return
        // 通知点开面板时若授权已被系统回收，直接 addView 会抛 SecurityException
        if (!canShow()) {
            toast(R.string.toast_need_overlay)
            return
        }
        val width = (300 * density).toInt()
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (140 * density).toInt()
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt())
            background = cardBackground(radiusDp = 24f)
        }

        root.addView(
            TextView(context).apply {
                text = context.getString(R.string.panel_title)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(0xFFF2F6FA.toInt())
                includeFontPadding = false
            },
        )
        panelLiveText = TextView(context).apply {
            text = context.getString(R.string.panel_reading)
            textSize = 12f
            setTextColor(0xFF7FD9A8.toInt())
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            setPadding(0, (6 * density).toInt(), 0, 0)
        }
        root.addView(panelLiveText)

        root.addView(
            View(context).apply { setBackgroundColor(0x1FFFFFFF) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = (14 * density).toInt()
            },
        )

        panelQptText = panelRow(root, context.getString(R.string.settings_qpt_switch)) {
            scope.launch {
                repository.setQptEnabled(!repository.state.value.qptEnabled)
                refreshPanelState()
            }
        }
        panelBubbleText = panelRow(root, context.getString(R.string.settings_bubble)) {
            val enabled = !repository.bubbleEnabled
            repository.bubbleEnabled = enabled
            if (enabled) showBubble() else hideBubble()
            refreshPanelState()
        }
        panelOsdText = panelRow(root, context.getString(R.string.panel_osd)) {
            // 走偏好赋值，让 observeOsdEnabled 统一裁决显示/隐藏
            repository.osdEnabled = !repository.osdEnabled
            refreshPanelState()
        }

        root.addView(
            TextView(context).apply {
                text = context.getString(R.string.panel_close)
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTextColor(0xFFCAD6E1.toInt())
                background = pillBackground(filled = false)
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
        runCatching { windowManager.addView(root, params) }.onFailure { return }
        panel = root
        refreshPanelState()
        bindPanelLive()
    }

    /** 一行「标题 —— 状态」的胶囊按钮，整行可点。 */
    private fun panelRow(root: LinearLayout, title: String, onClick: () -> Unit): TextView {
        val row = TextView(context).apply {
            text = title
            textSize = 13.5f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(0xFFF2F7FB.toInt())
            background = pillBackground(filled = true)
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

    fun hidePanel() {
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
        val prefix = if (recording) "● REC" else context.getString(R.string.panel_idle)
        val soc = sample?.socPowerUw?.let { "%.2f W".format(it / 1_000_000.0) }
        val fps = sample?.fps?.let { "%.0f fps".format(it) }
        val parts = listOfNotNull(soc, fps)
        return if (parts.isEmpty()) {
            context.getString(R.string.panel_waiting_fmt, prefix)
        } else {
            "$prefix · ${parts.joinToString(" · ")}"
        }
    }

    private fun refreshPanelState() {
        qptStatus(panelQptText, context.getString(R.string.settings_qpt_switch), repository.state.value.qptEnabled)
        qptStatus(panelBubbleText, context.getString(R.string.settings_bubble), repository.bubbleEnabled)
        qptStatus(panelOsdText, context.getString(R.string.panel_osd), repository.osdEnabled)
    }

    private fun qptStatus(view: TextView?, title: String, on: Boolean) {
        view?.text = "$title  ${context.getString(if (on) R.string.state_on else R.string.state_off)}"
    }

    // ---------- 背景与触摸 ----------

    private fun cardBackground(radiusDp: Float): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(0xF7, 0x12, 0x19, 0x1F), Color.argb(0xF4, 0x08, 0x0C, 0x10)),
    ).apply {
        cornerRadius = radiusDp * density
        setStroke(
            (1 * density).roundToInt(),
            Color.argb(0x2E, 0xFF, 0xFF, 0xFF),
        )
    }

    /** 面板按钮：半透明白底 + 可按压水波纹，和深色面板同一套语言。 */
    private fun pillBackground(filled: Boolean): android.graphics.drawable.Drawable {
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
        val margin = (8 * density).roundToInt()
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

    /** OSD 拖拽监听：拖动改位置，轻点循环切换透明度，长按开始/停止记录。 */
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
                    params.x = (downX + dx).coerceIn(0, max(0, screenW - v.width))
                    params.y = (downY + dy).coerceIn(0, max(0, screenH - v.height))
                    windowManager.updateViewLayout(v, params)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        overlayPrefs.edit().putInt(KEY_OSD_X, params.x).putInt(KEY_OSD_Y, params.y).apply()
                    } else if (event.eventTime - event.downTime >= LONG_PRESS_MS) {
                        // 长按＝开始/停止记录。借 Scene 的做法：悬浮面板本身就是记录开关，
                        // 不用回应用里点。轻点仍保留透明度循环，两者不冲突。
                        toggleRecording()
                    } else {
                        cycleOsdAlpha()
                    }
                    return true
                }
            }
            return true
        }
    }

    private fun toast(resId: Int) {
        android.widget.Toast.makeText(context, resId, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val KEY_OSD_X = "osd_x"
        private const val KEY_OSD_Y = "osd_y"
        private const val KEY_OSD_ALPHA = "osd_alpha"

        /** 长按判定阈值：轻点切透明度、长按切记录，中间留足够余量避免误触。 */
        private const val LONG_PRESS_MS = 500L
    }
}
