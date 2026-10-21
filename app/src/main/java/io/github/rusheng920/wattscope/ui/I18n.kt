package io.github.rusheng920.wattscope.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.rusheng920.wattscope.domain.StatusText

/** 按当前语言渲染状态文案（Compose 侧）。 */
@Composable
fun StatusText.resolve(): String = stringResource(resId, *args.toTypedArray())

/** 按当前语言渲染状态文案（非 Compose 侧，如 Service / 自绘 View）。 */
fun StatusText.resolve(context: Context): String = context.getString(resId, *args.toTypedArray())
