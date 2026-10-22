package io.github.rusheng920.wattscope.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rusheng920.wattscope.R
import io.github.rusheng920.wattscope.ui.components.ActionRow
import io.github.rusheng920.wattscope.ui.components.MiuixCard
import io.github.rusheng920.wattscope.ui.components.SectionTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 一条第三方依赖记录。[license] 以各项目仓库内的 LICENSE 文件为准。 */
private data class LicenseEntry(
    val name: String,
    val license: String,
    val url: String,
    val noteRes: Int? = null,
)

private val RUNTIME = listOf(
    LicenseEntry("Kotlin Standard Library", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
    LicenseEntry("kotlinx.coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
)

private val ANDROIDX = listOf(
    LicenseEntry("AndroidX Core · Lifecycle · Activity", "Apache-2.0", "https://github.com/androidx/androidx"),
    LicenseEntry("Jetpack Compose (UI · Material3)", "Apache-2.0", "https://github.com/androidx/androidx"),
    LicenseEntry("Material Icons Extended", "Apache-2.0", "https://github.com/google/material-design-icons"),
    LicenseEntry("Room", "Apache-2.0", "https://github.com/androidx/androidx"),
    LicenseEntry("Navigation Compose", "Apache-2.0", "https://github.com/androidx/androidx"),
    LicenseEntry("WorkManager", "Apache-2.0", "https://github.com/androidx/androidx"),
)

private val UI = listOf(
    LicenseEntry("MIUIX for Compose Multiplatform", "Apache-2.0", "https://github.com/miuix-kotlin-multiplatform/miuix"),
    LicenseEntry("Backdrop · Android Liquid Glass", "Apache-2.0", "https://github.com/Kyant0/AndroidLiquidGlass"),
)

private val PRIVILEGE = listOf(
    LicenseEntry("libsu", "Apache-2.0", "https://github.com/topjohnwu/libsu"),
)

private val REFERENCES = listOf(
    LicenseEntry(
        name = "OpenMonitor",
        license = "GPL-3.0",
        url = "https://github.com/1orz/OpenMonitor",
        noteRes = R.string.licenses_openmonitor_note,
    ),
)

/**
 * 开源软件许可页。
 *
 * 只列**实际进入 APK 的依赖**（与 `app/build.gradle.kts` 一一对应），不抄一份通用清单 ——
 * 许可证声明的价值在于可核对，列了没用到的库反而让人怀疑其余条目。
 *
 * 特别注意：OpenMonitor 是 **GPL-3.0**。本项目的概览页与进程管理只参考了它的功能结构，
 * 代码是按 Android 系统接口重新实现的，没有复制其源码 —— 否则整个项目会被传染成 GPL。
 * 这一条写在页面上，是给后来维护者的明确约束。
 */
@Composable
fun LicensesSettingsPage(bottomPadding: Dp = 24.dp) {
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MiuixCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.licenses_intro_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.licenses_intro_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        licenseGroup(R.string.licenses_group_runtime, RUNTIME, context)
        licenseGroup(R.string.licenses_group_androidx, ANDROIDX, context)
        licenseGroup(R.string.licenses_group_ui, UI, context)
        licenseGroup(R.string.licenses_group_privilege, PRIVILEGE, context)
        licenseGroup(R.string.licenses_group_reference, REFERENCES, context)

        item {
            Text(
                stringResource(R.string.licenses_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.licenseGroup(
    @androidx.annotation.StringRes titleRes: Int,
    entries: List<LicenseEntry>,
    context: android.content.Context,
) {
    item { SectionTitle(stringResource(titleRes)) }
    item {
        MiuixCard(contentPadding = 0.dp) {
            entries.forEachIndexed { index, entry ->
                ActionRow(
                    title = entry.name,
                    // 有备注才给副标题：没有备注时副标题只能是许可证名，
                    // 和右侧的许可证徽标重复一遍（第一版就是这么写的，读起来像 bug）
                    subtitle = entry.noteRes?.let { context.getString(it) },
                    icon = if (entry.noteRes != null) Icons.Rounded.Description else Icons.Rounded.Code,
                    trailing = {
                        Text(
                            entry.license,
                            style = MaterialTheme.typography.labelSmall,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    },
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(entry.url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    showDivider = index > 0,
                )
            }
        }
    }
}
