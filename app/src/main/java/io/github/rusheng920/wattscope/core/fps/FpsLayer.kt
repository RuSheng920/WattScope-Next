package io.github.rusheng920.wattscope.core.fps

import androidx.annotation.StringRes
import io.github.rusheng920.wattscope.R

/** 图层类型。只用于设置页展示与排序，不参与采集逻辑。 */
enum class LayerKind(@StringRes val labelRes: Int) {
    /** `SurfaceView[包名/Activity]#N`：游戏与视频播放器的上屏层，帧数据最准。 */
    SURFACE_VIEW(R.string.fps_layer_kind_surface_view),

    /** `包名/Activity#0`：应用主窗口层，普通 Compose/View 应用走这里。 */
    MAIN_WINDOW(R.string.fps_layer_kind_main_window),

    /** 其余（输入法、系统浮层等）。 */
    OTHER(R.string.fps_layer_kind_other),
}

/**
 * 设置页可选图层的一项。
 *
 * [fps] 是扫描时**实际跑了一次 `--latency` 探测**得到的结果：很多层（BLAST 层、
 * Background 镜像层）名字看着对，但根本没有帧时间戳缓冲，选中后只会得到空曲线。
 * 把探测值直接摆在列表里，用户一眼就能看出该选哪个。
 */
data class LayerInfo(
    val name: String,
    val kind: LayerKind,
    /** 该层是否属于扫描时的前台应用。 */
    val isForeground: Boolean,
    /** 探测到的帧率；null = 没有有效帧时间戳。 */
    val fps: Float? = null,
    /** 探测窗口内捕获的帧数，用于区分「真没数据」与「刚好没动」。 */
    val frames: Int = 0,
    /** 是否已探测过（未探测的层不显示「无数据」）。 */
    val probed: Boolean = false,
) {
    /** 探测到帧才算可用：这决定了界面上的徽标是数值还是「无数据」。 */
    val usable: Boolean get() = (fps ?: 0f) > 0f
}
