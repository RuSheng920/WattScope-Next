package io.github.rusheng920.wattscope.core.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import io.github.rusheng920.wattscope.domain.FpsMetrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记录分享图（PNG 导出）。
 *
 * 为什么自己画而不是截 Compose：截图会把当前主题、滚动位置、状态栏一起带进来，
 * 尺寸还取决于设备；报告图需要固定版式（1080×1500），在任何机器上生成的结果一致。
 *
 * 版式参考 OpenMonitor 的会话分享图，但只保留能自证的数据：标题、时间、六项指标、
 * FPS 曲线。不放二维码/水印 —— 这是给用户自己存档和发群里的图，不是推广物料。
 */
object RecordReportRenderer {

    private const val W = 1080
    private const val H = 1500
    private const val PAD = 72f

    private const val BG = 0xFF0E1116.toInt()
    private const val CARD = 0xFF171B22.toInt()
    private const val INK = 0xFFF2F4F7.toInt()
    private const val MUTED = 0xFF9AA4B2.toInt()
    private const val ACCENT = 0xFF5B9DFF.toInt()
    private const val GRID = 0xFF2A2F38.toInt()

    fun render(
        title: String,
        packageName: String,
        startedAtMs: Long,
        durationMs: Long,
        metrics: FpsMetrics?,
        avgSocPowerUw: Long,
        deviceModel: String,
        fpsPoints: List<Pair<Long, Float>>,
        versionName: String,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(BG)

        val titlePaint = text(56f, INK, bold = true)
        val subPaint = text(28f, MUTED)
        val labelPaint = text(26f, MUTED)
        val valuePaint = text(52f, INK, bold = true, mono = true)
        val footPaint = text(24f, MUTED)

        canvas.drawText(title.take(24), PAD, 150f, titlePaint)
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(startedAtMs))
        canvas.drawText("$packageName · $date · ${durationText(durationMs)}", PAD, 200f, subPaint)

        // 指标区：3 列 × 2 行
        val cells = listOf(
            "平均 FPS" to fm(metrics?.avgFps),
            "最高 FPS" to fm(metrics?.maxFps),
            "最低 FPS" to fm(metrics?.minFps),
            "流畅度" to (metrics?.let { "${it.fluencyPercent.toInt()}%" } ?: "—"),
            "5% Low" to fm(metrics?.low5Fps),
            "平均帧时间" to (metrics?.let { "%.1f ms".format(Locale.US, it.avgFrameTimeMs) } ?: "—"),
        )
        val colW = (W - PAD * 2) / 3f
        cells.forEachIndexed { index, (label, value) ->
            val col = index % 3
            val row = index / 3
            val x = PAD + col * colW
            val y = 320f + row * 150f
            canvas.drawText(label, x, y, labelPaint)
            canvas.drawText(value, x, y + 60f, valuePaint)
        }

        // 曲线卡片
        val chartTop = 640f
        val chartBottom = 1240f
        val cardPaint = Paint().apply { color = CARD }
        canvas.drawRoundRect(PAD - 24f, chartTop - 56f, W - PAD + 24f, chartBottom + 40f, 28f, 28f, cardPaint)

        canvas.drawText("FPS 曲线", PAD, chartTop - 8f, labelPaint)
        val avgPower = if (avgSocPowerUw > 0) "平均 SOC %.2f W".format(Locale.US, avgSocPowerUw / 1_000_000.0) else ""
        canvas.drawText(avgPower, W - PAD - 260f, chartTop - 8f, labelPaint)

        drawChart(canvas, fpsPoints, chartTop, chartBottom)

        canvas.drawText(
            "$deviceModel · WattScope Next v$versionName",
            PAD,
            (H - 48).toFloat(),
            footPaint,
        )
        return bmp
    }

    private fun drawChart(canvas: Canvas, points: List<Pair<Long, Float>>, top: Float, bottom: Float) {
        val left = PAD
        val right = W - PAD
        val gridPaint = Paint().apply {
            color = GRID
            strokeWidth = 2f
        }
        repeat(4) { i ->
            val y = top + (bottom - top) * i / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        if (points.isEmpty()) {
            canvas.drawText("无帧率数据", left + 16f, (top + bottom) / 2, text(30f, MUTED))
            return
        }
        val minTs = points.first().first
        val maxTs = points.last().first
        val span = (maxTs - minTs).coerceAtLeast(1L)
        val maxFps = (points.maxOf { it.second }).coerceAtLeast(60f)

        val axisPaint = text(22f, MUTED)
        canvas.drawText("%.0f".format(Locale.US, maxFps), right - 40f, top + 18f, axisPaint)
        canvas.drawText("0", right - 40f, bottom - 6f, axisPaint)

        val path = Path()
        points.forEachIndexed { i, (ts, fps) ->
            val x = left + (ts - minTs).toFloat() / span * (right - left)
            val y = bottom - (fps / maxFps).coerceIn(0f, 1f) * (bottom - top)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val linePaint = Paint().apply {
            color = ACCENT
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, linePaint)
    }

    private fun fm(value: Float?): String = value?.let { "%.1f".format(Locale.US, it) } ?: "—"

    private fun text(size: Float, color: Int, bold: Boolean = false, mono: Boolean = false) = Paint().apply {
        textSize = size
        this.color = color
        isAntiAlias = true
        typeface = when {
            mono -> Typeface.MONOSPACE
            bold -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
        if (bold && !mono) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun durationText(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
