package io.github.rusheng920.wattscope.domain

import androidx.annotation.StringRes
import io.github.rusheng920.wattscope.R

/**
 * 独立浮层的指标分组。
 *
 * 与 OSD 的区别：OSD 是一块「面板」，适合并排看多个数值；独立浮层是若干个小胶囊，
 * 每个只显示一个数，可以分别开关、贴在不同角落。两者复用同一份 [OsdSample] 数据源。
 *
 * 胶囊一律 `FLAG_NOT_TOUCHABLE`：它们只用来读，不抢触摸 —— 游戏里任何多余的可触摸热区
 * 都可能吞掉技能点击，这个代价比「能拖动」大得多。
 */
enum class FloatKind(val key: String, @StringRes val labelRes: Int) {
    FPS("fps", R.string.float_kind_fps),
    CPU("cpu", R.string.float_kind_cpu),
    MEMORY("memory", R.string.float_kind_memory),
    GPU("gpu", R.string.float_kind_gpu),
    TEMP("temp", R.string.float_kind_temp),
}
