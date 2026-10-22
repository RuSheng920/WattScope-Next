package io.github.rusheng920.wattscope.ui

import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.rusheng920.wattscope.BuildConfig
import io.github.rusheng920.wattscope.R
import io.github.rusheng920.wattscope.core.fps.LayerInfo
import io.github.rusheng920.wattscope.core.update.ReleaseInfo
import io.github.rusheng920.wattscope.domain.AppPermissions
import io.github.rusheng920.wattscope.domain.BubbleSize
import io.github.rusheng920.wattscope.domain.GlassIntensity
import io.github.rusheng920.wattscope.domain.OsdMetric
import io.github.rusheng920.wattscope.domain.SamplingInterval
import io.github.rusheng920.wattscope.domain.ThemeMode
import io.github.rusheng920.wattscope.domain.UpdateChannel
import io.github.rusheng920.wattscope.domain.UpdateState
import io.github.rusheng920.wattscope.ui.components.ActionRow
import io.github.rusheng920.wattscope.ui.components.IconBadge
import io.github.rusheng920.wattscope.ui.components.MiuixCard
import io.github.rusheng920.wattscope.ui.components.MiuixSwitch
import io.github.rusheng920.wattscope.ui.components.PillButton
import io.github.rusheng920.wattscope.ui.components.PillTone
import io.github.rusheng920.wattscope.ui.components.SectionTitle
import io.github.rusheng920.wattscope.ui.components.SegmentedControl
import io.github.rusheng920.wattscope.ui.components.SelectChip
import io.github.rusheng920.wattscope.ui.components.StatusPill
import io.github.rusheng920.wattscope.ui.theme.LocalStatusColors
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val GITHUB_URL = "https://github.com/RuSheng920/WattScope-Next"
private const val WEBSITE_URL = "https://watt.rusheng.top"

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
    fpsLayers: List<LayerInfo>,
    scanning: Boolean,
    autoLayer: String?,
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
                // 自动模式下告诉用户「自动」此刻到底用了哪一层。
                // 图层名很长，塞进 ActionRow 副标题会被 2:5:3 挤成孤字，单独一行更清楚。
                if (fpsLayer == null && !autoLayer.isNullOrBlank()) {
                    AutoLayerLine(autoLayer)
                }
                // 扫描是「动作」而不是「状态」，和上面的跟随模式分两组。
                ActionRow(
                    title = stringResource(
                        when {
                            scanning -> R.string.settings_fps_scanning
                            fpsLayers.isEmpty() -> R.string.settings_scan_layers
                            else -> R.string.settings_rescan_layers
                        },
                    ),
                    subtitle = when {
                        scanning -> stringResource(R.string.settings_fps_scanning_desc)
                        fpsLayers.isEmpty() -> stringResource(R.string.settings_fps_stack_empty)
                        else -> stringResource(
                            R.string.settings_fps_found_fmt,
                            fpsLayers.count { it.usable },
                            fpsLayers.size,
                        )
                    },
                    icon = Icons.Rounded.Search,
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = if (scanning) null else onLoadFpsLayers,
                    showDivider = true,
                    // 动作行没有开关也没有胶囊时，右侧空着会被读成「纯展示文本」。
                    // 扫描中转圈、其余状态挂箭头明示可点。
                    trailing = {
                        if (scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                fpsLayers.forEach { layer ->
                    LayerRow(
                        layer = layer,
                        selected = fpsLayer == layer.name,
                        onClick = { onSelectFpsLayer(layer.name) },
                    )
                }
            }
        }
        // 空态说明放卡外：这是「怎么才能扫到」的引导，不属于某个具体设置项，
        // 塞进行内副标题会被 2:5:3 布局压成五六行。
        if (fpsLayers.isEmpty() && !scanning) {
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

/** 自动模式当前实际使用的图层：缩进 + 等宽小字，与设置行区分开。 */
@Composable
private fun AutoLayerLine(layer: String) {
    Text(
        stringResource(R.string.settings_fps_current_fmt, layer),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 60.dp, end = 16.dp, bottom = 10.dp),
    )
}

/**
 * 图层行：左边层名 + 类型标签，右边实测帧率。
 *
 * 帧率是扫描时真跑了一次 `--latency` 得到的 —— 名字看起来对的层未必有帧数据，
 * 直接把数值摆出来，用户不必靠猜（选中一个 0 帧的层只会得到空曲线）。
 */
@Composable
private fun LayerRow(layer: LayerInfo, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        CardInnerDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    layer.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(layer.kind.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (layer.isForeground) {
                        Text(
                            stringResource(R.string.fps_layer_foreground),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            when {
                selected -> StatusPill(stringResource(R.string.state_in_use), PillTone.OK)
                layer.usable -> FpsBadge(layer.fps ?: 0f)
                layer.probed -> Text(
                    stringResource(R.string.settings_fps_probe_none),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 实测帧率徽标：等宽数字，绿色语义色，和「无数据」形成对比。 */
@Composable
private fun FpsBadge(fps: Float) {
    val colors = LocalStatusColors.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(colors.okBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            stringResource(R.string.settings_fps_badge_fmt, String.format(java.util.Locale.US, "%.0f", fps)),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = colors.okFg,
        )
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

/**
 * 关于页：品牌、更新、项目链接。
 *
 * 更新分三块表达：**开关**（是否在启动时自动检查）、**通道**（是否考虑测试版）、
 * **当前状态**（检查结果 / 下载进度）。三者并列而不是塞进一句话，是因为它们
 * 回答的是三个不同的问题，混在一起用户没法判断「为什么没更新」。
 */
@Composable
fun AboutSettingsPage(
    autoUpdate: Boolean,
    channel: UpdateChannel,
    updateState: UpdateState,
    pending: ReleaseInfo?,
    lastCheckMs: Long,
    onAutoUpdate: (Boolean) -> Unit,
    onChannel: (UpdateChannel) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownload: () -> Unit,
    bottomPadding: Dp = 24.dp,
) {
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
            }
        }

        // 更新独立成组：它是「会联网、会改动应用」的一组设置，和品牌信息挤在一张卡里
        // 会让人以为只是展示内容。
        item { SectionTitle(stringResource(R.string.about_section_update)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                UpdateSettingsCard(
                    autoUpdate = autoUpdate,
                    channel = channel,
                    updateState = updateState,
                    lastCheckMs = lastCheckMs,
                    onAutoUpdate = onAutoUpdate,
                    onChannel = onChannel,
                    onCheckUpdate = onCheckUpdate,
                )
            }
        }

        // 有可用更新时单独一张卡：版本号、说明、下载进度都是会变化的内容，
        // 混在上面的设置卡里会让「检查更新」那一行随下载进度不断跳动。
        if (pending != null) {
            item {
                UpdateOfferCard(
                    info = pending,
                    state = updateState,
                    onDownload = onDownload,
                )
            }
        }

        item { SectionTitle(stringResource(R.string.about_section_project)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                LinkRow(
                    title = stringResource(R.string.about_github),
                    url = GITHUB_URL,
                    icon = Icons.Rounded.Code,
                    onClick = { openUrl(context, GITHUB_URL) },
                )
                LinkRow(
                    title = stringResource(R.string.about_website),
                    url = WEBSITE_URL,
                    icon = Icons.Rounded.Language,
                    onClick = { openUrl(context, WEBSITE_URL) },
                    showDivider = true,
                )
            }
        }
    }
}

/** 更新设置：自动检查开关 + 通道 + 手动检查。 */
@Composable
private fun UpdateSettingsCard(
    autoUpdate: Boolean,
    channel: UpdateChannel,
    updateState: UpdateState,
    lastCheckMs: Long,
    onAutoUpdate: (Boolean) -> Unit,
    onChannel: (UpdateChannel) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val busy = updateState is UpdateState.Checking || updateState is UpdateState.Downloading
    ActionRow(
        title = stringResource(R.string.about_auto_update),
        subtitle = stringResource(R.string.about_auto_update_desc),
        icon = Icons.Rounded.Refresh,
        iconTint = MaterialTheme.colorScheme.primary,
        trailing = { MiuixSwitch(checked = autoUpdate, onCheckedChange = onAutoUpdate) },
    )
    CardInnerDivider()
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.about_update_channel), style = MaterialTheme.typography.titleSmall)
        // 两项短标签，用等分分段控件；Beta 的取舍写在下面，不占用控件宽度
        SegmentedControl(
            options = UpdateChannel.entries.map { stringResource(it.labelRes) },
            selectedIndex = UpdateChannel.entries.indexOf(channel),
            onSelect = { onChannel(UpdateChannel.entries[it]) },
        )
        Text(
            stringResource(R.string.about_update_channel_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CardInnerDivider()
    ActionRow(
        title = stringResource(R.string.about_check_update),
        subtitle = updateStatusText(updateState, lastCheckMs),
        icon = Icons.Rounded.SystemUpdate,
        iconTint = MaterialTheme.colorScheme.secondary,
        trailing = { UpdateStatusTrailing(updateState) },
        onClick = { if (!busy) onCheckUpdate() },
    )
}

/** 检查行的右侧：状态胶囊；空闲时用箭头明示可点。 */
@Composable
private fun UpdateStatusTrailing(state: UpdateState) {
    when (state) {
        UpdateState.Idle -> Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )

        UpdateState.Checking -> StatusPill(stringResource(R.string.about_state_checking), PillTone.NEUTRAL)
        UpdateState.UpToDate -> StatusPill(stringResource(R.string.about_state_up_to_date), PillTone.OK)
        is UpdateState.Available -> StatusPill(stringResource(R.string.about_state_new), PillTone.WARN)
        is UpdateState.Downloading -> StatusPill(stringResource(R.string.about_state_downloading), PillTone.NEUTRAL)
        is UpdateState.ReadyToInstall -> StatusPill(stringResource(R.string.about_state_ready), PillTone.OK)
        is UpdateState.Failed -> StatusPill(stringResource(R.string.about_state_failed), PillTone.ERROR)
    }
}

/** 检查行副标题：当前状态优先，没有状态时退回到「上次检查」。 */
@Composable
private fun updateStatusText(state: UpdateState, lastCheckMs: Long): String = when (state) {
    UpdateState.Idle -> if (lastCheckMs > 0) {
        stringResource(R.string.about_last_check_fmt, rememberRelativeTime(lastCheckMs))
    } else {
        stringResource(R.string.about_check_never)
    }

    UpdateState.Checking -> stringResource(R.string.about_checking)
    UpdateState.UpToDate -> stringResource(R.string.about_up_to_date)
    is UpdateState.Available -> stringResource(R.string.about_new_version_fmt, state.info.versionName)
    is UpdateState.Downloading ->
        if (state.progress >= 0f) {
            stringResource(R.string.about_downloading_fmt, (state.progress * 100).roundToInt())
        } else {
            stringResource(R.string.about_downloading_unknown)
        }

    is UpdateState.ReadyToInstall -> stringResource(R.string.about_ready_install)
    is UpdateState.Failed -> stringResource(state.messageRes)
}

@Composable
private fun rememberRelativeTime(atMs: Long): String {
    val now = System.currentTimeMillis()
    return remember(atMs) {
        DateUtils.getRelativeTimeSpanString(atMs, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}

/** 可用更新卡：版本、通道徽标、更新说明与下载入口。 */
@Composable
private fun UpdateOfferCard(info: ReleaseInfo, state: UpdateState, onDownload: () -> Unit) {
    MiuixCard(contentPadding = 0.dp) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.NewReleases, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "v${info.versionName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (info.isBeta) {
                            StatusPill(stringResource(R.string.about_update_beta_badge), PillTone.WARN)
                        }
                    }
                    val meta = listOfNotNull(
                        info.apkSizeBytes.takeIf { it > 0 }?.let(::formatBytes),
                        info.publishedAtMs.takeIf { it > 0 }?.let { formatDate(it) },
                    )
                    if (meta.isNotEmpty()) {
                        Text(
                            meta.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.about_update_notes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 整段展示，不截断：关于页本身是长列表，让用户一次看完全部更新内容
            Text(
                info.localizedNotes().ifBlank { stringResource(R.string.about_update_notes_empty) },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        CardInnerDivider()
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (state) {
                is UpdateState.Downloading -> {
                    UpdateProgressBar(state.progress)
                    Text(
                        updateStatusText(state, 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is UpdateState.ReadyToInstall -> Text(
                    stringResource(R.string.about_ready_install),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is UpdateState.Failed -> {
                    Text(
                        stringResource(state.messageRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    PillButton(
                        text = stringResource(R.string.about_update_retry),
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        height = 40.dp,
                    )
                }

                else -> PillButton(
                    text = stringResource(R.string.about_update_now),
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    height = 40.dp,
                )
            }
        }
    }
}

/** 细进度条：轨道用 surfaceContainerHigh（暗色已在主题里提亮），与 chip 同色阶。 */
@Composable
private fun UpdateProgressBar(progress: Float) {
    val fraction = if (progress >= 0f) progress.coerceIn(0f, 1f) else 0.35f
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary),
        )
    }
}

/**
 * 「发现新版本」弹窗（启动自动检测命中时弹出）。
 *
 * 下载过程中保持打开并显示进度，安装包就绪即自动关闭 —— 系统安装器会接管屏幕，
 * 留一个模态框在下面只会让人以为应用卡住了。
 */
@Composable
fun UpdateAvailableDialog(
    info: ReleaseInfo,
    state: UpdateState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(state) {
        if (state is UpdateState.ReadyToInstall) onDismiss()
    }
    val downloading = state is UpdateState.Downloading
    Dialog(
        // 下载中不允许点外部关闭：中断的是安装包下载，不是一次询问
        onDismissRequest = { if (!downloading) onDismiss() },
    ) {
        MiuixCard(contentPadding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.NewReleases, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.about_update_dialog_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "v${info.versionName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (info.isBeta) {
                            StatusPill(stringResource(R.string.about_update_beta_badge), PillTone.WARN)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 弹窗高度有限，说明区单独限高 + 内部滚动：既不截断内容，
            // 也不会因为一次大版本更新把按钮顶出屏幕。
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    info.localizedNotes().ifBlank { stringResource(R.string.about_update_notes_empty) },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state is UpdateState.Failed) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(state.messageRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(16.dp))
            when (state) {
                is UpdateState.Downloading -> {
                    UpdateProgressBar(state.progress)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        updateStatusText(state, 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(
                        text = stringResource(R.string.about_update_later),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        filled = false,
                        height = 40.dp,
                    )
                    PillButton(
                        text = stringResource(R.string.about_update_now),
                        onClick = onDownload,
                        modifier = Modifier.weight(1f),
                        height = 40.dp,
                    )
                }
            }
        }
    }
}

/**
 * Release 正文由脚本拼成「中文 → `---` → English」，两份都塞进弹窗只会更长。
 * 按当前界面语言只取对应的一半；只有单语（早期版本）时原样返回。
 */
@Composable
private fun ReleaseInfo.localizedNotes(): String {
    val notes = notes
    if (notes.isEmpty()) return notes
    val parts = notes.split(Regex("""(?m)^-{3,}[ \t]*$""")).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return notes.trim()
    val english = LocalConfiguration.current.locales[0]?.language == "en"
    return if (english) parts.last() else parts.first()
}

/** 链接行：标题 + 完整 URL（可折行）+ 箭头。URL 不截断，用户能核对域名。 */
@Composable
private fun LinkRow(
    title: String,
    url: String,
    icon: ImageVector,
    onClick: () -> Unit,
    showDivider: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) CardInnerDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

private fun formatDate(ms: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(ms))

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
