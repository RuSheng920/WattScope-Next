package io.github.rusheng920.wattscope.service

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * 液态玻璃背景（Liquid Glass）。
 *
 * 构成：柔和外阴影 + 斜向半透明渐变 + 顶部高光 + 1dp 亮边 + 底部内阴影。
 * 全部为静态渐变，**不做逐帧模糊**：OSD 常驻显示，实时高斯模糊会让 GPU 持续工作，
 * 与「低开销功耗监控」的产品目标冲突；控制面板这类短时浮层才额外叠加系统模糊。
 *
 * @param cornerRadiusPx 圆角半径（px）
 * @param dark true = 深色玻璃（深底浅字），false = 浅色玻璃（浅底深字）
 */
class GlassDrawable(
    private val cornerRadiusPx: Float,
    private val dark: Boolean = true,
    private val shadowOffsetPx: Float = 6f,
) : Drawable() {

    private val rect = RectF()
    private val shadowRect = RectF()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (dark) Color.argb(70, 0, 0, 0) else Color.argb(38, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * 深色玻璃的底衬：没有这层，白色渐变叠在亮色游戏画面上会变成浅灰，
     * 白字直接失去对比度。深色底 + 白色高光才能在任何内容上都保持可读。
     */
    private val baseFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (dark) Color.argb(135, 9, 13, 19) else Color.argb(238, 255, 255, 255)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val innerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = if (dark) Color.argb(80, 255, 255, 255) else Color.argb(150, 255, 255, 255)
    }

    // 尺寸不变时复用渐变 shader，避免每次重绘（OSD 每次数据刷新）都分配对象
    private var shaderW = -1f
    private var shaderH = -1f

    private fun ensureShaders(w: Float, h: Float) {
        if (w == shaderW && h == shaderH) return
        shaderW = w
        shaderH = h

        val start = if (dark) Color.argb(56, 255, 255, 255) else Color.argb(242, 255, 255, 255)
        val end = if (dark) Color.argb(14, 255, 255, 255) else Color.argb(214, 255, 255, 255)
        basePaint.shader = LinearGradient(0f, 0f, w, h, start, end, Shader.TileMode.CLAMP)

        highlightPaint.shader = RadialGradient(
            w * 0.32f,
            h * 0.12f,
            w.coerceAtLeast(h) * 0.9f,
            intArrayOf(
                if (dark) Color.argb(60, 255, 255, 255) else Color.argb(255, 255, 255, 255),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )

        innerShadowPaint.shader = LinearGradient(
            0f, h - h * 0.35f, 0f, h,
            Color.TRANSPARENT, Color.argb(28, 0, 0, 0), Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val w = rect.width()
        val h = rect.height()
        if (w <= 0f || h <= 0f) return
        ensureShaders(w, h)

        // 外阴影：略微下移的同形圆角矩形（硬件层下 setShadowLayer 对形状无效，改用手绘）
        shadowRect.set(rect.left, rect.top + shadowOffsetPx, rect.right, rect.bottom + shadowOffsetPx)
        canvas.drawRoundRect(shadowRect, cornerRadiusPx, cornerRadiusPx, shadowPaint)

        // 主体：左上到右下的斜向渐变，模拟玻璃厚度带来的光程差
        canvas.save()
        canvas.translate(rect.left, rect.top)
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, baseFillPaint)
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, basePaint)
        // 顶部高光
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, highlightPaint)
        if (dark) {
            canvas.drawRoundRect(0f, 0f, w, h, cornerRadiusPx, cornerRadiusPx, innerShadowPaint)
        }
        canvas.restore()

        // 亮边：让玻璃边界在任意壁纸上都清晰（内缩半个描边，避免被 View 边界裁掉）
        val half = borderPaint.strokeWidth / 2f
        canvas.drawRoundRect(
            rect.left + half, rect.top + half, rect.right - half, rect.bottom - half,
            cornerRadiusPx - half, cornerRadiusPx - half, borderPaint,
        )
    }

    override fun setAlpha(alpha: Int) {
        baseFillPaint.alpha = alpha
        basePaint.alpha = alpha
        borderPaint.alpha = alpha
        highlightPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        basePaint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
