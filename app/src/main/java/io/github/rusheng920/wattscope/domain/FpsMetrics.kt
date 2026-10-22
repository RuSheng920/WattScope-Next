package io.github.rusheng920.wattscope.domain

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * FPS 会话的统计口径（对齐 OpenMonitor 会话详情页的六项指标）。
 *
 * 采样是「每个采样点一个瞬时帧率」（约 5Hz），不是逐帧时间戳 —— 因此：
 * - 流畅度 = 采样点中 ≥45fps 的占比，衡量的是「这段时间里有多少采样落在可玩区间」；
 * - 5% Low = 帧率序列升序的第 5 百分位，即最差那 5% 的边界值；
 * - 卡顿帧 = 帧时间超过中位数 1.5 倍的采样点数。真实 jank 统计需要逐帧数据，
 *   这里只能给出「掉帧采样点的数量」，用于横向比较同一设备的多次记录。
 */
data class FpsMetrics(
    val avgFps: Float,
    val maxFps: Float,
    val minFps: Float,
    val stdDev: Float,
    val fluencyPercent: Float,
    val low5Fps: Float,
    val avgFrameTimeMs: Float,
    val jankSamples: Int,
    val sampleCount: Int,
)

/** 由帧率序列推导会话指标；空序列返回 null。 */
fun computeFpsMetrics(values: List<Float>): FpsMetrics? {
    val valid = values.filter { it > 0f }
    if (valid.isEmpty()) return null
    val avg = valid.average().toFloat()
    val variance = valid.sumOf { v -> val d = (v - avg).toDouble(); d * d } / valid.size
    val sorted = valid.sorted()
    val lowIndex = ((sorted.size - 1) * 0.05f).roundToInt().coerceIn(0, sorted.lastIndex)
    val frameTimes = valid.map { 1000f / it }
    val medianFrameTime = frameTimes.sorted()[frameTimes.size / 2]
    return FpsMetrics(
        avgFps = avg,
        maxFps = sorted.last(),
        minFps = sorted.first(),
        stdDev = sqrt(variance).toFloat(),
        fluencyPercent = valid.count { it >= 45f } * 100f / valid.size,
        low5Fps = sorted[lowIndex],
        avgFrameTimeMs = frameTimes.average().toFloat(),
        jankSamples = frameTimes.count { it > medianFrameTime * 1.5f },
        sampleCount = valid.size,
    )
}

