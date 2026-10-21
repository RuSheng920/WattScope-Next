package io.github.rusheng920.wattscope.ui

import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rusheng920.wattscope.BuildConfig
import io.github.rusheng920.wattscope.R
import io.github.rusheng920.wattscope.domain.AppPermissions
import io.github.rusheng920.wattscope.domain.BubbleSize
import io.github.rusheng920.wattscope.domain.GlassIntensity
import io.github.rusheng920.wattscope.domain.OsdMetric
import io.github.rusheng920.wattscope.domain.SamplingInterval
import io.github.rusheng920.wattscope.domain.ThemeMode
import io.github.rusheng920.wattscope.ui.components.ActionRow
import io.github.rusheng920.wattscope.ui.components.IconBadge
import io.github.rusheng920.wattscope.ui.components.MiuixCard
import io.github.rusheng920.wattscope.ui.components.MiuixSwitch
import io.github.rusheng920.wattscope.ui.components.PillTone
import io.github.rusheng920.wattscope.ui.components.SectionTitle
import io.github.rusheng920.wattscope.ui.components.SegmentedControl
import io.github.rusheng920.wattscope.ui.components.SelectChip
import io.github.rusheng920.wattscope.ui.components.StatusPill
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val GITHUB_URL = "https://github.com/RuSheng920/WattScope-Next"

/**
 * 设置二级页。
 *
 * 设置首页只做「分类索引」，每一类设置项进入独立页面 —— 之前所有开关挤在一条
 * 长列表里，用户需要滚动才能判断自己在哪一组。分级之后首页一屏可见全部入口，
 * 每一页只讲一件事，标题即上下文。
 *
 * [titleRes] 同时用于二级页顶栏标题与索引页行标题。
 */
enum class SettingsPage(@StringRes val titleRes: Int) {
    THEME(R.string.settings_page_theme),
    SAMPLING(R.string.settings_page_sampling),
    OVERLAY(R.string.settings_page_overlay),
    // 页标题与分组标题同义，直接复用，避免两份文案不同步
    FPS(R.string.settings_section_fps),
    SERVICE(R.string.settings_page_service),
    ABOUT(R.string.settings_page_about),
}

/** 二级页共用的列表骨架：16dp 页边距 + 12dp 卡片间距。 */
@Composable
private fun SettingsPageColumn(
    bottomPadding: Dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

// ---------- 设置首页（分类索引） ----------

@Composable
fun SettingsHub(
    themeMode: ThemeMode,
    interval: SamplingInterval,
    bubbleEnabled: Boolean,
    osdEnabled: Boolean,
    fpsLayer: String?,
    permissions: AppPermissions,
    onOpen: (SettingsPage) -> Unit,
    bottomPadding: Dp = 24.dp,
) {
    SettingsPageColumn(bottomPadding) {
        item { SectionTitle(stringResource(R.string.settings_group_preferences)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = stringResource(SettingsPage.THEME.titleRes),
                    subtitle = stringResource(R.string.settings_page_theme_desc),
                    icon = Icons.Rounded.Palette,
                    iconTint = MaterialTheme.colorScheme.primary,
                    trailing = { NavTrailing(stringResource(themeMode.labelRes)) },
                    onClick = { onOpen(SettingsPage.THEME) },
                )
                ActionRow(
                    title = stringResource(SettingsPage.SAMPLING.titleRes),
                    subtitle = stringResource(R.string.settings_page_sampling_desc),
                    icon = Icons.Rounded.Speed,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    trailing = { NavTrailing(stringResource(interval.labelRes)) },
                    onClick = { onOpen(SettingsPage.SAMPLING) },
                    showDivider = true,
                )
                ActionRow(
                    title = stringResource(SettingsPage.OVERLAY.titleRes),
                    subtitle = stringResource(R.string.settings_page_overlay_desc),
                    icon = Icons.Rounded.Layers,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    trailing = {
                        NavTrailing(
                            stringResource(
                                if (bubbleEnabled || osdEnabled) {
                                    R.string.settings_value_on
                                } else {
                                    R.string.settings_value_off
                                },
                            )
                        )
                    },
                    onClick = { onOpen(SettingsPage.OVERLAY) },
                    showDivider = true,
                )
                ActionRow(
                    title = stringResource(SettingsPage.FPS.titleRes),
                    subtitle = stringResource(R.string.settings_page_fps_desc),
                    icon = Icons.Rounded.Timer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    trailing = {
                        NavTrailing(
                            stringResource(
                                if (fpsLayer == null) {
                                    R.string.settings_value_auto
                                } else {
                                    R.string.settings_value_manual
                                },
                            )
                        )
                    },
                    onClick = { onOpen(SettingsPage.FPS) },
                    showDivider = true,
                )
            }
        }

        item { SectionTitle(stringResource(R.string.settings_group_support)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = stringResource(SettingsPage.SERVICE.titleRes),
                    subtitle = stringResource(
                        R.string.settings_perm_ready_fmt,
                        permissions.requiredGranted(),
                        permissions.requiredTotal(),
                    ),
                    icon = Icons.Rounded.Shield,
                    iconTint = if (permissions.requiredReady) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    trailing = {
                        StatusPill(
                            if (permissions.requiredReady) {
                                stringResource(R.string.state_ready)
                            } else {
                                stringResource(R.string.state_pending_auth)
                            },
                            if (permissions.requiredReady) PillTone.OK else PillTone.WARN,
                        )
                    },
                    onClick = { onOpen(SettingsPage.SERVICE) },
                )
                ActionRow(
                    title = stringResource(SettingsPage.ABOUT.titleRes),
                    subtitle = stringResource(R.string.settings_page_about_desc),
                    icon = Icons.Rounded.Info,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    trailing = { NavTrailing("v${BuildConfig.VERSION_NAME}") },
                    onClick = { onOpen(SettingsPage.ABOUT) },
                    showDivider = true,
                )
            }
        }
    }
}

/** 索引行右侧：当前值 + 进入箭头。两者间距 4dp，合并成一个视觉单元。 */
@Composable
private fun NavTrailing(value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ---------- 外观与主题 ----------

/**
 * 主题页：主题模式 + 语言 + 液态玻璃。
 *
 * 液态玻璃（底栏开关 / 浓度 / 记录期间降级）本质上都是「外观表现」，
 * 所以从设置首页搬到这里 —— 首页不再出现「悬浮玻璃底栏」这种只在
 * 主题语境下才讲得清的分组。
 */
@Composable
fun ThemeSettingsPage(
    themeMode: ThemeMode,
    glassBottomBar: Boolean,
    glassIntensity: GlassIntensity,
    glassPauseRecording: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onGlassBottomBar: (Boolean) -> Unit,
    onGlassIntensity: (GlassIntensity) -> Unit,
    onGlassPauseRecording: (Boolean) -> Unit,
    bottomPadding: Dp = 24.dp,
) {
    SettingsPageColumn(bottomPadding) {
        item { SectionTitle(stringResource(R.string.settings_theme)) }
        item {
            MiuixCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Palette)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_theme_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                SegmentedControl(
                    options = ThemeMode.entries.map { stringResource(it.labelRes) },
                    selectedIndex = ThemeMode.entries.indexOf(themeMode),
                    onSelect = { onThemeMode(ThemeMode.entries[it]) },
                )
            }
        }

        item { SectionTitle(stringResource(R.string.settings_group_language)) }
        item { LanguageCard() }

        item { SectionTitle(stringResource(R.string.settings_group_glass)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = stringResource(R.string.settings_glass_bar),
                    subtitle = stringResource(R.string.settings_glass_bar_desc),
                    icon = Icons.Rounded.Layers,
                    trailing = { MiuixSwitch(checked = glassBottomBar, onCheckedChange = onGlassBottomBar) },
                )
                if (glassBottomBar) {
                    CardInnerDivider()
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_glass_intensity),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        SegmentedControl(
                            options = GlassIntensity.entries.map { stringResource(it.labelRes) },
                            selectedIndex = GlassIntensity.entries.indexOf(glassIntensity),
                            onSelect = { onGlassIntensity(GlassIntensity.entries[it]) },
                        )
                        Text(
                            stringResource(R.string.settings_glass_intensity_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CardInnerDivider()
                    ActionRow(
                        title = stringResource(R.string.settings_glass_pause),
                        subtitle = stringResource(R.string.settings_glass_pause_desc),
                        icon = Icons.Rounded.Timer,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        trailing = {
                            MiuixSwitch(checked = glassPauseRecording, onCheckedChange = onGlassPauseRecording)
                        },
                    )
                }
            }
        }
    }
}

// ---------- 采样与功耗 ----------

@Composable
fun SamplingSettingsPage(
    enabled: Boolean,
    interval: SamplingInterval,
    dualCellMode: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onInterval: (SamplingInterval) -> Unit,
    onDualCellMode: (Boolean) -> Unit,
    bottomPadding: Dp = 24.dp,
) {
    SettingsPageColumn(bottomPadding) {
        item { SectionTitle(stringResource(R.string.settings_section_sampling)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = stringResource(R.string.settings_qpt_switch),
                    subtitle = if (enabled) {
                        stringResource(R.string.settings_qpt_on_desc)
                    } else {
                        stringResource(R.string.settings_qpt_off_desc)
                    },
                    icon = Icons.Rounded.Speed,
                    trailing = { MiuixSwitch(checked = enabled, onCheckedChange = onToggleEnabled) },
                )
                CardInnerDivider()
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_interval),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    // 四档且带「毫秒」单位，塞进等分分段控件（每段约 84dp）会被省略号截成
                    // 「100 …」。这里用 chip 组 + FlowRow：项多、标签长时自动换行，不会藏起
                    // 第四档（原先的横向滚动就会把它推到可视区外）。
                    // 规则：2–3 项短标签用分段控件（TabRow），≥4 项或长标签用 chip 组。
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SamplingInterval.entries.forEach {
                            SelectChip(
                                text = stringResource(it.labelRes),
                                selected = it == interval,
                                onClick = { onInterval(it) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.settings_interval_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionTitle(stringResource(R.string.settings_section_calibration)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = stringResource(R.string.settings_dual_cell),
                    subtitle = stringResource(R.string.settings_dual_cell_desc),
                    icon = Icons.Rounded.BatteryChargingFull,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    trailing = { MiuixSwitch(checked = dualCellMode, onCheckedChange = onDualCellMode) },
                )
            }
        }
    }
}

// ---------- 悬浮窗与 OSD ----------

@Composable
fun OverlaySettingsPage(
    bubbleEnabled: Boolean,
    bubbleSize: BubbleSize,
    bubbleSnapToEdge: Boolean,
    osdEnabled: Boolean,
    osdMetrics: List<OsdMetric>,
    onToggleBubble: (Boolean) -> Unit,
    onBubbleSize: (BubbleSize) -> Unit,
    onBubbleSnapToEdge: (Boolean) -> Unit,
    onToggleOsd: (Boolean) -> Unit,
    onOsdMetricsChange: (List<OsdMetric>) -> Unit,
    bottomPadding: Dp = 24.dp,
) {
    SettingsPageColumn(bottomPadding) {
        item { SectionTitle(stringResource(R.string.settings_section_bubble)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = stringResource(R.string.settings_bubble),
                    subtitle = stringResource(R.string.settings_bubble_desc),
                    icon = Icons.Rounded.Layers,
                    trailing = { MiuixSwitch(checked = bubbleEnabled, onCheckedChange = onToggleBubble) },
                )
                if (bubbleEnabled) {
                    CardInnerDivider()
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_bubble_size),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.settings_bubble_size_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BubbleSize.entries.forEach { size ->
                                SelectChip(
                                    text = stringResource(size.labelRes),
                                    selected = size == bubbleSize,
                                    onClick = { onBubbleSize(size) },
                                )
                            }
                        }
                    }
                    CardInnerDivider()
                    ActionRow(
                        title = stringResource(R.string.settings_bubble_snap),
                        subtitle = stringResource(R.string.settings_bubble_snap_desc),
                        icon = Icons.Rounded.Layers,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        trailing = {
                            MiuixSwitch(checked = bubbleSnapToEdge, onCheckedChange = onBubbleSnapToEdge)
                        },
                    )
                }
            }
        }

        item { SectionTitle(stringResource(R.string.settings_section_osd)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = stringResource(R.string.settings_osd),
                    subtitle = stringResource(R.string.settings_osd_desc),
                    icon = Icons.Rounded.Notifications,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    trailing = { MiuixSwitch(checked = osdEnabled, onCheckedChange = onToggleOsd) },
                )
            }
        }

        if (osdEnabled) {
            item {
                MiuixCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Notifications, MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_osd_metrics),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(
                                    R.string.settings_osd_metrics_desc,
                                    osdMetrics.size,
                                    OsdMetric.entries.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OsdMetric.entries.forEach { metric ->
                            SelectChip(
                                text = stringResource(metric.labelRes),
                                selected = osdMetrics.contains(metric),
                                onClick = {
                                    onOsdMetricsChange(
                                        if (osdMetrics.contains(metric)) osdMetrics - metric else osdMetrics + metric,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- FPS 数据源 ----------

@Composable
fun FpsSettingsPage(
    fpsLayer: String?,
    fpsLayers: List<String>,
    onSelectFpsLayer: (String?) -> Unit,
    onLoadFpsLayers: () -> Unit,
    bottomPadding: Dp = 24.dp,
) {
    SettingsPageColumn(bottomPadding) {
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = stringResource(R.string.settings_fps_auto),
                    subtitle = stringResource(R.string.settings_fps_auto_desc),
                    icon = Icons.Rounded.Timer,
                    trailing = {
                        if (fpsLayer == null) {
                            StatusPill(stringResource(R.string.state_enabled), PillTone.OK)
                        } else {
                            MiuixTextButton(
                                text = stringResource(R.string.action_enable),
                                onClick = { onSelectFpsLayer(null) },
                            )
                        }
                    },
                    onClick = { onSelectFpsLayer(null) },
                )
                // 扫描是「动作」而不是「状态」，和上面的跟随模式分两组；
                // 扫描到的图层直接列在同一张卡里，选中项右侧挂「使用中」。
                ActionRow(
                    title = stringResource(
                        if (fpsLayers.isEmpty()) {
                            R.string.settings_scan_layers
                        } else {
                            R.string.settings_rescan_layers
                        },
                    ),
                    subtitle = if (fpsLayers.isEmpty()) {
                        stringResource(R.string.settings_fps_stack_empty)
                    } else {
                        stringResource(R.string.settings_fps_manual_fmt, fpsLayers.size)
                    },
                    icon = Icons.Rounded.Search,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onLoadFpsLayers,
                    showDivider = true,
                    // 动作行没有开关也没有胶囊时，右侧空着会被读成「纯展示文本」。
                    // 挂一个箭头明示可点。
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                fpsLayers.forEach { layer ->
                    LayerRow(
                        name = layer,
                        selected = fpsLayer == layer,
                        onClick = { onSelectFpsLayer(layer) },
                    )
                }
            }
        }
        // 空态说明放卡外：这是「怎么才能扫到」的引导，不属于某个具体设置项，
        // 塞进行内副标题会被 2:5:3 布局压成五六行。
        if (fpsLayers.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.settings_fps_none),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** 图层行：选中项右侧挂「使用中」胶囊，未选中项点击即切换。 */
@Composable
private fun LayerRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        CardInnerDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Spacer(Modifier.width(8.dp))
                StatusPill(stringResource(R.string.state_in_use), PillTone.OK)
            }
        }
    }
}

// ---------- 服务与权限 ----------

@Composable
fun ServiceSettingsPage(
    permissions: AppPermissions,
    onOpenPermissions: () -> Unit,
    onStartService: () -> Unit,
    onRequestBatteryWhitelist: () -> Unit,
    onOpenMiuiAutostart: () -> Unit,
    bottomPadding: Dp = 24.dp,
) {
    SettingsPageColumn(bottomPadding) {
        item { SectionTitle(stringResource(R.string.settings_section_permission)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = stringResource(R.string.settings_permission_center),
                    subtitle = stringResource(
                        R.string.settings_perm_ready_fmt,
                        permissions.requiredGranted(),
                        permissions.requiredTotal(),
                    ),
                    icon = Icons.Rounded.Shield,
                    iconTint = if (permissions.requiredReady) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    trailing = {
                        StatusPill(
                            if (permissions.requiredReady) {
                                stringResource(R.string.state_ready)
                            } else {
                                stringResource(R.string.state_pending_auth)
                            },
                            if (permissions.requiredReady) PillTone.OK else PillTone.WARN,
                        )
                    },
                    onClick = onOpenPermissions,
                )
            }
        }

        item { SectionTitle(stringResource(R.string.settings_section_service)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = stringResource(R.string.settings_restart_service),
                    subtitle = stringResource(R.string.settings_restart_service_desc),
                    icon = Icons.Rounded.Refresh,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = onStartService,
                )
                ActionRow(
                    title = stringResource(R.string.settings_battery),
                    // 白名单是「状态」而不是「说明」——和上面的权限中心一样用胶囊表达，
                    // 副标题固定讲价值，不会因为状态变化在两句不同性质的话之间跳。
                    subtitle = stringResource(R.string.settings_battery_desc),
                    icon = Icons.Rounded.BatteryChargingFull,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    trailing = {
                        StatusPill(
                            if (permissions.batteryUnrestricted) {
                                stringResource(R.string.settings_battery_added)
                            } else {
                                stringResource(R.string.settings_battery_missing)
                            },
                            if (permissions.batteryUnrestricted) PillTone.OK else PillTone.WARN,
                        )
                    },
                    onClick = onRequestBatteryWhitelist,
                    showDivider = true,
                )
                if (permissions.isMiui) {
                    ActionRow(
                        title = stringResource(R.string.settings_miui_autostart),
                        subtitle = stringResource(R.string.settings_miui_autostart_desc),
                        icon = Icons.Rounded.Settings,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        onClick = onOpenMiuiAutostart,
                        showDivider = true,
                    )
                }
            }
        }
    }
}

// ---------- 关于 ----------

@Composable
fun AboutSettingsPage(bottomPadding: Dp = 24.dp) {
    val context = LocalContext.current
    SettingsPageColumn(bottomPadding) {
        item {
            MiuixCard(contentPadding = 0.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 直接用桌面图标的自适应图标两层资源还原品牌图（暖白底 + 红渐变焰形 W），
                    // 而不是拿一个通用 Material 图标顶替 —— 关于页是唯一的品牌露出位置。
                    // 前景层是 108dp 画布、图形只占中间 72dp 安全区，所以按 1.5 倍放大抵消留白。
                    // 图标本身是暖白底，放在浅色卡上会糊在一起。用极轻的投影托出边界，
                    // 而不是描一圈 dividerLine —— 那在白卡上会读成表格线。
                    Box(
                        Modifier
                            .size(64.dp)
                            .shadow(3.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(listOf(Color.White, Color(0xFFFFF6F3))),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "WattScope Next",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.about_version_fmt, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                    color = MiuixTheme.colorScheme.dividerLine,
                )
                ActionRow(
                    title = stringResource(R.string.about_github),
                    // 去掉 github.com 前缀：这一行在 2:5:3 布局里只有约 168dp 宽，
                    // 全写会在倒数第二个连字符处折行，把 "WattScope-Next" 甩到第二行。
                    subtitle = "RuSheng920/WattScope-Next",
                    icon = Icons.Rounded.Code,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    trailing = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                        }
                    },
                )
            }
        }
    }
}

/**
 * 应用语言：写入系统级 Per-app language（API 33+）。
 *
 * 不额外落一份本地偏好 —— 系统已经是唯一状态源，用户从系统设置里改过之后
 * 应用内显示不会与之打架。写入后系统会重建 Activity，采样服务不受影响。
 */
@Composable
private fun LanguageCard() {
    val context = LocalContext.current
    val localeManager = remember(context) { context.getSystemService(LocaleManager::class.java) }
    var currentTag by remember {
        mutableStateOf(localeManager?.applicationLocales?.get(0)?.toLanguageTag())
    }
    val selected = when {
        currentTag == null -> 0
        currentTag.orEmpty().startsWith("zh") -> 1
        else -> 2
    }
    MiuixCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Info)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.settings_language_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SegmentedControl(
            options = listOf(
                stringResource(R.string.lang_system),
                stringResource(R.string.lang_zh),
                stringResource(R.string.lang_en),
            ),
            selectedIndex = selected,
            onSelect = { index ->
                val tag = when (index) {
                    0 -> null
                    1 -> "zh-CN"
                    else -> "en"
                }
                currentTag = tag
                localeManager?.applicationLocales = if (tag == null) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(tag)
                }
            },
        )
    }
}

@Composable
private fun CardInnerDivider(startPadding: Dp = 16.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = 16.dp)
            .height(1.dp)
            // 与 MIUIX ActionRow 用同一条分隔线色，卡片内不会出现两种灰
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}
