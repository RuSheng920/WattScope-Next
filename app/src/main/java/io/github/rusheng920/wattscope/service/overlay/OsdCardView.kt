package io.github.rusheng920.wattscope.service.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.rusheng920.wattscope.domain.OsdMetric
import io.github.rusheng920.wattscope.domain.OsdSample
import kotlin.math.roundToInt

/**
 * OSD 实时浮层：一块「两列式」HUD 面板，替代原来把所有指标拼成一个多行字符串的 TextView。
 *
 * 结构：标题行（状态点 + WATTSCOPE + REC / LIVE）+ 分隔线 + 若干「标签 ⟷ 数值」行。
 * 标签左对齐、数值右对齐并固定等宽字体，数值列不会因为位数变化而整体抖动 ——
 * 这是旧版单 TextView 最大的问题：一行里既有中文标签又有变宽的数字，永远对不齐。
 *
 * 面板底色用「白色垂直渐变 + 1dp 深色细边」，配深色文字 —— 在亮色/深色壁纸上都可读，
 * 且与应用内的浅色卡片观感一致（不再是一块突兀的黑板）。
 * 不做实时模糊：OSD 是常驻逐帧刷新的浮层，模糊会把 GPU 功耗记到被测应用头上。
 */
class OsdCardView(context: Context) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val valueViews = LinkedHashMap<OsdMetric, TextView>()
    private var currentMetrics: List<OsdMetric> = emptyList()

    /** 白玻璃面板上的深冷灰文字色（声明顺序约束：内联使用）。 */
    private val ink = Color.rgb(0x1A, 0x21, 0x2B)
    private val inkMuted = Color.argb(0x99, 0x2A, 0x32, 0x3E)

    private val statusDot = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(0x99, 0x4A, 0x55, 0x63))
        }
    }
    private val statusText = TextView(context).apply {
        text = "LIVE"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
        setTextColor(Color.argb(0x8A, 0x2A, 0x32, 0x3E))
        letterSpacing = 0.16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = false
    }
    private val emptyText = TextView(context).apply {
        text = "等待数据…"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setTextColor(Color.argb(0xCC, 0x2A, 0x32, 0x3E))
        includeFontPadding = false
    }
    private val body = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        setPadding((12 * density).roundToInt(), (10 * density).roundToInt(), (12 * density).roundToInt(), (10 * density).roundToInt())
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(0xF2, 0xFF, 0xFF, 0xFF), Color.argb(0xEE, 0xF0, 0xF5, 0xFB)),
        ).apply {
            cornerRadius = 17 * density
            setStroke((1 * density).roundToInt(), Color.argb(0x24, 0x00, 0x00, 0x00))
        }

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(statusDot, LayoutParams((6 * density).roundToInt(), (6 * density).roundToInt()))
        header.addView(
            TextView(context).apply {
                text = "WATTSCOPE"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
                setTextColor(Color.argb(0xB0, 0x2A, 0x32, 0x3E))
                letterSpacing = 0.18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                includeFontPadding = false
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = (6 * density).roundToInt()
            },
        )
        header.addView(View(context), LayoutParams(0, 1, 1f))
        header.addView(statusText)
        addView(header)

        addView(
            View(context).apply { setBackgroundColor(Color.argb(0x1A, 0x00, 0x00, 0x00)) },
            LayoutParams(LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = (7 * density).roundToInt()
                bottomMargin = (6 * density).roundToInt()
            },
        )

        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.addView(emptyText)
    }

    /**
     * 指标集合变化时重建行；数据刷新时只改数值（见 [bind]），避免每帧 measure 整棵子树。
     *
     * 显示顺序一律按 [OsdMetric] 的声明顺序（SOC → CPU-L 大核 → CPU-M 小核 → GPU → …），
     * 而不是用户点选的先后顺序 —— 否则同一组指标会因为勾选次序不同而每次排列不同。
     */
    fun setMetrics(metrics: List<OsdMetric>) {
        val ordered = metrics.sortedBy { OsdMetric.entries.indexOf(it) }
        if (ordered == currentMetrics) return
        currentMetrics = ordered
        valueViews.clear()
        body.removeAllViews()
        if (ordered.isEmpty()) {
            body.addView(emptyText)
            return
        }
        ordered.forEachIndexed { index, metric ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                TextView(context).apply {
                    text = metric.label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(inkMuted)
                    letterSpacing = 0.02f
                    includeFontPadding = false
                },
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
            )
            val value = TextView(context).apply {
                text = "--"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(ink)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                includeFontPadding = false
                gravity = Gravity.END
                isSingleLine = true
            }
            row.addView(value, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            valueViews[metric] = value
            body.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) topMargin = (3 * density).roundToInt()
            })
        }
    }

    /** 绑定一帧采样；[recording] 只影响标题行的状态点颜色。 */
    fun bind(sample: OsdSample?, recording: Boolean) {
        // 5Hz 刷新下 title/颜色只在状态翻转时改，避免每帧都触发一次 measure/layout
        val label = if (recording) "REC" else "LIVE"
        if (statusText.text != label) {
            statusText.text = label
            statusText.setTextColor(
                if (recording) Color.argb(0xF0, 0x14, 0x7A, 0x42) else Color.argb(0x8A, 0x2A, 0x32, 0x3E),
            )
            (statusDot.background as? GradientDrawable)?.setColor(
                if (recording) Color.argb(0xF0, 0x22, 0xB0, 0x62) else Color.argb(0x99, 0x4A, 0x55, 0x63),
            )
        }
        if (sample == null) {
            valueViews.values.forEach { if (it.text != "--") it.text = "--" }
            return
        }
        valueViews.forEach { (metric, view) ->
            val text = sample.value(metric) ?: "--"
            if (view.text != text) view.text = text
        }
    }
}
