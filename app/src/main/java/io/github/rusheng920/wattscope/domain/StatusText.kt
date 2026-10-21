package io.github.rusheng920.wattscope.domain

import androidx.annotation.StringRes

/**
 * 运行时状态文案：只保存资源 id 与参数，真正的翻译交给 UI 侧。
 *
 * 旧实现把已经翻译好的 String 存进 [RecordingState.status]，切换语言后这条文案
 * 会一直停留在旧语言（采样协程不会因为换语言而重跑）。存资源 id 之后，
 * 界面重建即可拿到新文案，采样链路完全不受影响。
 */
data class StatusText(@StringRes val resId: Int, val args: List<Any> = emptyList())
