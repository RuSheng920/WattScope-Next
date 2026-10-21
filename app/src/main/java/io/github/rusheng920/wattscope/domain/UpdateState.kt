package io.github.rusheng920.wattscope.domain

import androidx.annotation.StringRes
import io.github.rusheng920.wattscope.core.update.ReleaseInfo

/**
 * 更新检查状态。
 *
 * 文案只存资源 id（[Failed]），与 [StatusText] 同理：检查结果可能早于界面重建产生，
 * 存已翻译的字符串会让切换语言后一直停在旧语言。
 */
sealed interface UpdateState {
    /** 尚未检查过（或用户关掉了提示）。 */
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** 已是最新版本。 */
    data object UpToDate : UpdateState

    /** 发现新版本，等待用户确认下载。 */
    data class Available(val info: ReleaseInfo) : UpdateState

    /** 正在下载安装包；[progress] < 0 表示总长度未知。 */
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState

    /** 安装包已就绪，已调起系统安装器。 */
    data class ReadyToInstall(val info: ReleaseInfo) : UpdateState

    data class Failed(@StringRes val messageRes: Int) : UpdateState
}
