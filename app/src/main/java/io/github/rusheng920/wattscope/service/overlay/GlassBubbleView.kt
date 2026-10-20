package io.github.rusheng920.wattscope.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.min

/**
 * 悬浮球本体：一颗自绘的深色玻璃球，替代原来「圆形底色 + ▶ 文字」的 TextView。
 *
 * 画法分五层，全部走硬件加速的渐变/路径，没有 RenderEffect、没有逐帧动画，
 * 常驻显示时只在状态变化时重绘一次 —— 本应用用于测量功耗，
 * 悬浮球自身的刷新成本必须趋近于零：
 *
 * 1. 接触阴影：边缘外侧的径向渐变，让球「浮」在壁纸上而不是贴上去；
 * 2. 球体底色：近黑冷调，保证任何壁纸上白色图标都可读；
 * 3. 球体光泽：左上光源的斜向径向渐变（高光 → 透明 → 右下暗部）；
 * 4. 边缘轮廓：1dp 线性渐变亮边（左上最亮、中间几乎消失、右下补一点环境反光）；
 * 5. 顶部内高光 + 状态环 + 图标。
 *
 * 记录中在球体外圈加一圈绿色状态环（与应用内「录制中」胶囊同色系），
 * 图标切换为停止方块；待机时是播放三角。
 */
class GlassBubbleView(context: Context) : View(context) {

    enum class Mode { IDLE, RECORDING }

    var mode: Mode = Mode.IDLE
        set(value) {
            if (field == value) return
            field = value
            contentDescription = if (value == Mode.RECORDING) "停止记录" else "开始记录"
            invalidate()
        }

    private val density = resources.displayMetrics.density

    /** 渐变需要一点出血空间，否则边缘会被 View 边界裁掉。 */
    private val shadowMargin = 5f * density
    private val rimWidth = 1.1f * density
    private val ringWidth = 2f * density

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidth
    }
    private val ringGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth * 2.6f
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringWidth
    }
    /**
     * 图标用「填充 + 同色描边 + 圆角连接」绘制：
     * 描边把三角形的三个尖角磨圆，等价于 PathEffect.cornerPathEffect，
     * 但不需要额外的 PathEffect 对象，绘制成本更低。
     */
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.6f * resources.displayMetrics.density
        color = Color.WHITE
    }
    private val glyphPath = Path()
    private val stopRect = RectF()

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private val recordingAccent = Color.rgb(0x3D, 0xD6, 0x8C)

    init {
        contentDescription = "开始记录"
        isClickable = false
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = (min(w, h) / 2f - shadowMargin).coerceAtLeast(1f)
        buildShaders()
    }

    private fun buildShaders() {
        val r = radius

        shadowPaint.shader = RadialGradient(
            cx, cy + 1.6f * density, r * 1.34f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(88, 0, 0, 0),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.70f, 0.88f, 1f),
            Shader.TileMode.CLAMP,
        )

        bodyPaint.shader = RadialGradient(
            cx - r * 0.34f, cy - r * 0.42f, r * 1.62f,
            intArrayOf(
                Color.argb(0x4A, 0xFF, 0xFF, 0xFF),
                Color.argb(0x18, 0xFF, 0xFF, 0xFF),
                Color.argb(0x06, 0xFF, 0xFF, 0xFF),
                Color.argb(0x4D, 0, 0, 0),
            ),
            floatArrayOf(0f, 0.32f, 0.64f, 1f),
            Shader.TileMode.CLAMP,
        )

        sheenPaint.shader = RadialGradient(
            cx, cy - r * 0.58f, r * 0.86f,
            intArrayOf(Color.argb(0x52, 0xFF, 0xFF, 0xFF), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )

        rimPaint.shader = LinearGradient(
            cx - r, cy - r, cx + r, cy + r,
            intArrayOf(
                Color.argb(0xC4, 0xFF, 0xFF, 0xFF),
                Color.argb(0x26, 0xFF, 0xFF, 0xFF),
                Color.argb(0x0A, 0xFF, 0xFF, 0xFF),
                Color.argb(0x52, 0xFF, 0xFF, 0xFF),
            ),
            floatArrayOf(0f, 0.38f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (radius <= 1f) return
        val r = radius

        // 1. 接触阴影
        canvas.drawCircle(cx, cy, r * 1.34f, shadowPaint)

        // 2. 球体底色（近黑冷调，91% 不透明，保证图标可读）
        basePaint.color = Color.argb(232, 0x10, 0x17, 0x1D)
        canvas.drawCircle(cx, cy, r, basePaint)

        // 3. 球体光泽
        canvas.drawCircle(cx, cy, r, bodyPaint)
        canvas.drawCircle(cx, cy, r, sheenPaint)

        // 4. 亮边
        canvas.drawCircle(cx, cy, r - rimWidth * 0.5f, rimPaint)

        // 5. 状态环（记录中）：画在亮边外侧，更醒目
        if (mode == Mode.RECORDING) {
            ringGlowPaint.color = Color.argb(0x55, 0x3D, 0xD6, 0x8C)
            ringPaint.color = recordingAccent
            val ringRadius = r + ringWidth * 0.5f
            canvas.drawCircle(cx, cy, ringRadius, ringGlowPaint)
            canvas.drawCircle(cx, cy, ringRadius, ringPaint)
        }

        // 6. 图标
        drawGlyph(canvas, r)
    }

    private fun drawGlyph(canvas: Canvas, r: Float) {
        glyphPath.reset()
        if (mode == Mode.RECORDING) {
            val side = r * 0.46f
            stopRect.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
            canvas.drawRoundRect(stopRect, r * 0.14f, r * 0.14f, glyphPaint)
        } else {
            val h = r * 0.30f
            val left = cx - r * 0.17f
            glyphPath.moveTo(left, cy - h)
            glyphPath.lineTo(left, cy + h)
            glyphPath.lineTo(cx + r * 0.31f, cy)
            glyphPath.close()
            canvas.drawPath(glyphPath, glyphPaint)
        }
    }
}
