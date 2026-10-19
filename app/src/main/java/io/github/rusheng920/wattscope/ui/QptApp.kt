package io.github.rusheng920.wattscope.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.rusheng920.wattscope.BuildConfig
import io.github.rusheng920.wattscope.data.local.RecordEntity
import io.github.rusheng920.wattscope.data.local.SampleEntity
import io.github.rusheng920.wattscope.domain.AppPermissions
import io.github.rusheng920.wattscope.domain.GlassIntensity
import io.github.rusheng920.wattscope.domain.OsdMetric
import io.github.rusheng920.wattscope.domain.SamplingInterval
import io.github.rusheng920.wattscope.domain.ThemeMode
import io.github.rusheng920.wattscope.domain.ZoneSample
import io.github.rusheng920.wattscope.domain.ZoneStats
import io.github.rusheng920.wattscope.ui.components.ActionRow
import io.github.rusheng920.wattscope.ui.components.BottomItem
import io.github.rusheng920.wattscope.ui.components.FloatingGlassBottomBar
import io.github.rusheng920.wattscope.ui.components.IconBadge
import io.github.rusheng920.wattscope.ui.components.MiuixBottomBar
import io.github.rusheng920.wattscope.ui.components.MiuixCard
import io.github.rusheng920.wattscope.ui.components.MiuixSwitch
import io.github.rusheng920.wattscope.ui.components.PillButton
import io.github.rusheng920.wattscope.ui.components.PillTone
import io.github.rusheng920.wattscope.ui.components.SectionTitle
import io.github.rusheng920.wattscope.ui.components.SegmentedControl
import io.github.rusheng920.wattscope.ui.components.StatusPill
import io.github.rusheng920.wattscope.ui.theme.ChartColors
import io.github.rusheng920.wattscope.ui.theme.LocalChartColors
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private const val ChartLeftPadding = 100f
private const val ChartRightPadding = 18f
private const val ChartBottomPadding = 36f

@Composable
fun QptApp(
    viewModel: QptViewModel,
    onRequestOverlay: () -> Unit,
    onStartService: () -> Unit,
    onRequestBatteryWhitelist: () -> Unit,
    onOpenMiuiAutostart: () -> Unit,
    onToggleBubble: (Boolean) -> Unit,
    onExportRecord: (Long, Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestUsageAccess: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val records by viewModel.records.collectAsState()
    val bubbleEnabled by viewModel.bubbleEnabled.collectAsState()
    val osdEnabled by viewModel.osdEnabled.collectAsState()
    val osdMetrics by viewModel.osdMetrics.collectAsState()
    val fpsLayer by viewModel.fpsLayer.collectAsState()
    val fpsLayers by viewModel.fpsLayers.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val glassBottomBar by viewModel.glassBottomBar.collectAsState()
    val glassIntensity by viewModel.glassIntensity.collectAsState()
    val onboardingDone by viewModel.onboardingDone.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var permissionsOpen by remember { mutableStateOf(false) }
    var tabBackProgress by remember { mutableFloatStateOf(0f) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 权限页 / 首次引导可见时每 1.5s 刷新一次：root、悬浮窗、通知状态变化无需重启应用
    LaunchedEffect(permissionsOpen, onboardingDone) {
        if (!permissionsOpen && onboardingDone) return@LaunchedEffect
        while (true) {
            viewModel.refreshPermissions()
            kotlinx.coroutines.delay(1_500)
        }
    }

    // HyperOS 大标题随列表滚动折叠：同一 ScrollBehavior 同时挂到 TopAppBar 与内容区
    val topBarScroll = MiuixScrollBehavior()

    // 预测性返回：非首页时按住返回键，内容随手势缩放，松手完成返回
    PredictiveBackHandler(enabled = detailId == null && !permissionsOpen && tab != 0) { progress ->
        try {
            progress.collect { tabBackProgress = it.progress }
        } finally {
            tabBackProgress = 0f
        }
        tab = 0
    }

    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        Scaffold(
            containerColor = MiuixTheme.colorScheme.surface,
            topBar = {
                when (tab) {
                    0 -> TopAppBar(
                        title = "WattScope Next",
                        largeTitle = "WattScope Next",
                        scrollBehavior = topBarScroll,
                        actions = {
                            MiuixIconButton(onClick = {
                                viewModel.setThemeMode(if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK)
                            }) {
                                Icon(
                                    if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = "切换主题",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                            MiuixIconButton(onClick = viewModel::refresh) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "刷新",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        },
                    )

                    1 -> TopAppBar(
                        title = "记录",
                        largeTitle = "记录",
                        scrollBehavior = topBarScroll,
                        actions = {
                            if (records.isNotEmpty()) {
                                MiuixTextButton(text = "清空", onClick = viewModel::deleteAll)
                            }
                        },
                    )

                    else -> TopAppBar(title = "设置", largeTitle = "设置", scrollBehavior = topBarScroll)
                }
            },
            bottomBar = {
                // 关闭悬浮底栏时回落为贴底的 MIUIX 导航栏
                if (!glassBottomBar) {
                    MiuixBottomBar(
                        items = listOf(
                            BottomItem("仪表盘", Icons.Rounded.Speed),
                            BottomItem("记录", Icons.Rounded.History),
                            BottomItem("设置", Icons.Rounded.Settings),
                        ),
                        selected = tab,
                        onSelect = { tab = it },
                    )
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .nestedScroll(topBarScroll.nestedScrollConnection)
                    .graphicsLayer {
                        val p = tabBackProgress
                        scaleX = 1f - 0.05f * p
                        scaleY = 1f - 0.05f * p
                        alpha = 1f - 0.2f * p
                    },
            ) {
                when (tab) {
                    0 -> Dashboard(
                        status = state.status,
                        enabled = state.qptEnabled,
                        isRecording = state.isRecording,
                        canRecord = state.canRecord,
                        samples = state.latestSamples,
                        zoneNames = state.zones.map { it.name },
                        devicePowerAvailable = state.devicePowerAvailable,
                        devicePowerUw = state.latestDevicePowerUw,
                        latestFps = state.latestFps,
                        permissions = permissions,
                        bottomPadding = if (glassBottomBar) 104.dp else 24.dp,
                        onToggleEnabled = viewModel::setEnabled,
                        onRecordClick = { if (state.isRecording) viewModel.stopRecording() else viewModel.startRecording() },
                        onOpenPermissions = { permissionsOpen = true },
                    )

                    1 -> History(
                        records = records,
                        onOpen = { detailId = it },
                        onDelete = viewModel::deleteRecord,
                        bottomPadding = if (glassBottomBar) 104.dp else 24.dp,
                    )

                    else -> SettingsScreen(
                        enabled = state.qptEnabled,
                        interval = state.interval,
                        themeMode = themeMode,
                        bubbleEnabled = bubbleEnabled,
                        osdEnabled = osdEnabled,
                        osdMetrics = osdMetrics,
                        permissions = permissions,
                        glassBottomBar = glassBottomBar,
                        glassIntensity = glassIntensity,
                        bottomPadding = if (glassBottomBar) 104.dp else 24.dp,
                        onToggleEnabled = viewModel::setEnabled,
                        onInterval = viewModel::setInterval,
                        onThemeMode = viewModel::setThemeMode,
                        onGlassBottomBar = viewModel::setGlassBottomBar,
                        onGlassIntensity = viewModel::setGlassIntensity,
                        onStartService = onStartService,
                        onRequestBatteryWhitelist = onRequestBatteryWhitelist,
                        onOpenMiuiAutostart = onOpenMiuiAutostart,
                        onToggleBubble = onToggleBubble,
                        onToggleOsd = viewModel::setOsdEnabled,
                        onOsdMetricsChange = viewModel::setOsdMetrics,
                        fpsLayer = fpsLayer,
                        fpsLayers = fpsLayers,
                        onSelectFpsLayer = viewModel::setFpsLayer,
                        onLoadFpsLayers = viewModel::loadFpsLayers,
                        onOpenPermissions = { permissionsOpen = true },
                    )
                }
            }
        }

        // 悬浮玻璃底栏：覆盖在内容之上，列表滚动时会从玻璃后面穿过
        if (glassBottomBar) {
            // 外层 Box 负责贴底对齐：FloatingNavigationBar 的 modifier 落在内部 Row 上
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                FloatingGlassBottomBar(
                    items = listOf(
                        BottomItem("仪表盘", Icons.Rounded.Speed),
                        BottomItem("记录", Icons.Rounded.History),
                        BottomItem("设置", Icons.Rounded.Settings),
                    ),
                    selected = tab,
                    onSelect = { tab = it },
                    intensity = glassIntensity,
                    dark = isDarkTheme,
                )
            }
        }

        detailId?.let { id ->
            val detail by remember(id) { viewModel.stats(id) }.collectAsState()
            DetailPage(
                detail = detail,
                onDismiss = { detailId = null },
                onExport = { asJson -> onExportRecord(id, asJson) },
            )
        }

        if (permissionsOpen) {
            PermissionPage(
                permissions = permissions,
                onDismiss = { permissionsOpen = false },
                onRefresh = viewModel::refreshPermissions,
                onRequestRoot = viewModel::requestRoot,
                onRequestOverlay = onRequestOverlay,
                onRequestNotifications = onRequestNotifications,
                onRequestBattery = onRequestBatteryWhitelist,
                onRequestUsage = onRequestUsageAccess,
                onOpenAutostart = onOpenMiuiAutostart,
            )
        }

        if (!onboardingDone) {
            OnboardingScreen(
                permissions = permissions,
                onRequestRoot = viewModel::requestRoot,
                onRequestOverlay = onRequestOverlay,
                onRequestNotifications = onRequestNotifications,
                onRequestBattery = onRequestBatteryWhitelist,
                onRequestUsage = onRequestUsageAccess,
                onOpenAutostart = onOpenMiuiAutostart,
                onFinish = { viewModel.completeOnboarding(probe = true) },
                onSkip = { viewModel.completeOnboarding(probe = false) },
            )
        }
    }
}

// ---------- 仪表盘 ----------

@Composable
private fun Dashboard(
    status: String,
    enabled: Boolean,
    isRecording: Boolean,
    canRecord: Boolean,
    samples: List<ZoneSample>,
    zoneNames: List<String>,
    devicePowerAvailable: Boolean,
    devicePowerUw: Long?,
    latestFps: Float?,
    permissions: AppPermissions,
    bottomPadding: Dp = 24.dp,
    onToggleEnabled: (Boolean) -> Unit,
    onRecordClick: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val soc = samples.firstOrNull { it.zoneName.equals("SOC", true) }
    val chart = LocalChartColors.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!permissions.requiredReady) {
            item {
                MiuixCard(
                    onClick = onOpenPermissions,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentPadding = 16.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "还有 ${permissions.requiredTotal() - permissions.requiredGranted()} 项权限未授予",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "Root / 悬浮窗 / 通知，点击前往授权",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }

        item {
            HeroCard(
                status = status,
                enabled = enabled,
                isRecording = isRecording,
                canRecord = canRecord,
                socPowerUw = soc?.effectivePowerUw ?: 0L,
                onToggleEnabled = onToggleEnabled,
                onRecordClick = onRecordClick,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("分区数", samples.size.toString(), Modifier.weight(1f))
                StatTile(
                    "整机功耗",
                    if (devicePowerAvailable) devicePowerUw?.let { uwToW(it) } ?: "--" else "不可用",
                    Modifier.weight(1f),
                )
                StatTile("FPS", latestFps?.let { "%.0f".format(Locale.US, it) } ?: "--", Modifier.weight(1f))
            }
        }

        item {
            MiuixCard(contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("实时功耗", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        if (samples.isEmpty()) {
                            "等待数据"
                        } else {
                            "峰值 ${uwToW(samples.maxOfOrNull { it.effectivePowerUw } ?: 0L)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (samples.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "开启 QPT 后显示各分区功耗柱状图",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LiveBarChart(samples, chart.palette)
                }
            }
        }

        item {
            SectionTitle("功耗分区")
        }

        item {
            if (samples.isEmpty()) {
                MiuixCard {
                    Text(
                        "暂无实时数据。开启 QPT 或开始记录后，这里会按分区显示功耗。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                MiuixCard(contentPadding = 0.dp) {
                    val maxPower = max(1L, samples.maxOfOrNull { it.effectivePowerUw } ?: 1L)
                    samples.forEachIndexed { index, sample ->
                        ZoneRow(
                            name = sample.zoneName,
                            powerUw = sample.effectivePowerUw,
                            energyUj = sample.energyUj,
                            fraction = sample.effectivePowerUw.toFloat() / maxPower,
                            color = chart.palette[index % chart.palette.size],
                            showDivider = index > 0,
                        )
                    }
                }
            }
        }

        if (zoneNames.isNotEmpty()) {
            item {
                MiuixCard(contentPadding = 14.dp) {
                    Text(
                        "已识别 ${zoneNames.size} 个监控项：${zoneNames.joinToString("、")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    status: String,
    enabled: Boolean,
    isRecording: Boolean,
    canRecord: Boolean,
    socPowerUw: Long,
    onToggleEnabled: (Boolean) -> Unit,
    onRecordClick: () -> Unit,
) {
    MiuixCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "SOC 实时功耗",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.2f".format(Locale.US, socPowerUw / 1_000_000.0),
                        style = MaterialTheme.typography.displayLarge,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        " W",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    if (isRecording) "录制中" else "待机",
                    if (isRecording) PillTone.OK else PillTone.NEUTRAL,
                )
                MiuixSwitch(checked = enabled, onCheckedChange = onToggleEnabled)
                Text(
                    "QPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        PillButton(
            text = if (isRecording) "停止记录" else "开始记录",
            onClick = onRecordClick,
            enabled = canRecord || isRecording,
            filled = !isRecording,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ZoneRow(
    name: String,
    powerUw: Long,
    energyUj: Long,
    fraction: Float,
    color: Color,
    showDivider: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(10.dp))
                Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    uwToW(powerUw),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ProgressBarMini(fraction = fraction, color = color)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    ujToJ(energyUj),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProgressBarMini(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}

// ---------- 应用图标 / 名称 ----------

@Composable
private fun AppIcon(packageName: String?, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val name = packageName?.takeIf { it.isNotBlank() } ?: context.packageName
                context.packageManager.getApplicationIcon(name).toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/**
 * 应用名显示组件：若存储的 appName 是包名，显示时异步解析真实应用名；已卸载应用回退包名。
 */
@Composable
private fun AppNameText(
    appName: String,
    packageName: String,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    var display by remember(appName, packageName) { mutableStateOf(appName) }
    LaunchedEffect(appName, packageName) {
        if (appName == packageName && appName.contains('.')) {
            display = withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                }.getOrElse { appName }
            }
        }
    }
    Text(display, style = style, fontWeight = fontWeight, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

// ---------- 折线图 ----------

private data class SeriesData(
    val name: String,
    val color: Color,
    val points: List<Pair<Long, Float>>,
)

private data class CrosshairState(
    val index: Int,
    val ts: Long,
    val xPx: Float,
    val touchY: Float,
)

@Composable
private fun TimeSeriesChart(
    title: String,
    series: List<SeriesData>,
    formatter: (Float) -> String,
    interactive: Boolean = true,
    globalTs: Long? = null,
    onTsChange: ((Long?) -> Unit)? = null,
    rightSeries: SeriesData? = null,
    rightFormatter: (Float) -> String = { v -> "%.0f%%".format(Locale.US, v) },
    fixedMax: Float? = null,
    rightFixedMax: Float? = null,
    yTickCount: Int = 8,
    rightTickCount: Int = 7,
    showAverages: Boolean = true,
) {
    var touching by remember { mutableStateOf(false) }
    var touchY by remember { mutableStateOf(0f) }
    var widthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val chartHeightPx = with(density) { 160.dp.toPx() }
    val chartColors = LocalChartColors.current

    val refSeries = series.maxByOrNull { it.points.size }
    val refSize = refSeries?.points?.size ?: 0
    val refTsList = remember(series) { refSeries?.points?.map { it.first } ?: emptyList() }
    val allPoints = remember(series, rightSeries) { series.flatMap { it.points } + (rightSeries?.points ?: emptyList()) }
    val minTs = allPoints.minOfOrNull { it.first } ?: 0L
    val maxTs = allPoints.maxOfOrNull { it.first } ?: minTs
    val durationMs = (maxTs - minTs).coerceAtLeast(1L)
    val crosshairIndex = globalTs?.let { ts ->
        refTsList.indexOf(ts).takeIf { it >= 0 } ?: nearestByTs(ts, refTsList)
    }
    val showTooltip = interactive && globalTs != null && crosshairIndex != null

    MiuixCard(contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "$refSize 个采样点",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth()) {
            val canvasModifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .onSizeChanged { widthPx = it.width }
                .then(
                    if (interactive && onTsChange != null) {
                        Modifier.pointerInputChart(refTsList, onTsChange) { isTouching, y ->
                            touching = isTouching
                            touchY = y
                        }
                    } else {
                        Modifier
                    },
                )
            Canvas(canvasModifier) {
                drawSeriesChart(
                    series, formatter, crosshairIndex, minTs, durationMs, rightSeries, fixedMax,
                    rightFormatter, rightFixedMax, yTickCount, rightTickCount, chartColors,
                )
            }
            if (showTooltip) {
                val ts = globalTs ?: return@Box
                val effTouchY = if (touching) touchY else chartHeightPx * 0.4f
                val c = CrosshairState(index = crosshairIndex ?: 0, ts = ts, xPx = crosshairX(ts, minTs, durationMs, widthPx), touchY = effTouchY)
                CrosshairTooltip(
                    series = series,
                    index = c.index,
                    relativeSeconds = (c.ts - minTs) / 1000f,
                    anchorX = c.xPx,
                    touchY = c.touchY,
                    chartWidth = widthPx.toFloat(),
                    chartHeight = chartHeightPx,
                    formatter = formatter,
                    rightSeries = rightSeries,
                    rightFormatter = rightFormatter,
                )
            }
        }
        if (showAverages) {
            Spacer(Modifier.height(8.dp))
            ChartAverages(series, formatter, rightSeries, rightFormatter)
        }
    }
}

/**
 * 图表触摸手势：按下/拖动吸附采样点并上报时间戳，松手清空（十字光标全图同步消失）。
 * 独立成扩展函数避免 TimeSeriesChart 内联过深，同时保留 touchY 回调给 Tooltip 定位。
 */
private fun Modifier.pointerInputChart(
    tsList: List<Long>,
    onTsChange: (Long?) -> Unit,
    onTouchState: (touching: Boolean, y: Float) -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(tsList) {
        awaitEachGesture {
            val down = awaitFirstDown()
            var lastIdx = nearestTsIndex(down.position.x, size.width.toFloat(), tsList)
            onTsChange(tsList.getOrNull(lastIdx))
            onTouchState(true, down.position.y)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val idx = nearestTsIndex(change.position.x, size.width.toFloat(), tsList)
                if (idx != lastIdx) {
                    lastIdx = idx
                    onTsChange(tsList.getOrNull(idx))
                }
                onTouchState(true, change.position.y)
            }
            onTouchState(false, 0f)
            onTsChange(null)
        }
    },
)

@Composable
private fun ChartAverages(
    series: List<SeriesData>,
    formatter: (Float) -> String,
    rightSeries: SeriesData? = null,
    rightFormatter: (Float) -> String = { v -> "%.0f%%".format(Locale.US, v) },
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        series.forEach { s ->
            if (s.points.isNotEmpty()) {
                AvgChip(s.name, formatter(s.points.map { it.second }.average().toFloat()), s.color)
            }
        }
        rightSeries?.let { s ->
            if (s.points.isNotEmpty()) {
                AvgChip(s.name, rightFormatter(s.points.map { it.second }.average().toFloat()), s.color)
            }
        }
    }
}

@Composable
private fun AvgChip(name: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(
            "$name 均值 $value",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun nearestTsIndex(touchX: Float, width: Float, tsList: List<Long>): Int {
    if (tsList.isEmpty()) return 0
    val chartWidth = (width - ChartLeftPadding - ChartRightPadding).coerceAtLeast(1f)
    val ratio = ((touchX - ChartLeftPadding) / chartWidth).coerceIn(0f, 1f)
    return min(tsList.lastIndex, max(0, (ratio * tsList.lastIndex).roundToInt()))
}

private fun nearestByTs(ts: Long, tsList: List<Long>): Int? {
    if (tsList.isEmpty()) return null
    var best = 0
    var bestDiff = Long.MAX_VALUE
    tsList.forEachIndexed { i, t ->
        val diff = kotlin.math.abs(t - ts)
        if (diff < bestDiff) {
            bestDiff = diff
            best = i
        }
    }
    return best
}

private fun crosshairX(ts: Long, minTs: Long, durationMs: Long, widthPx: Int): Float {
    val chartWidth = (widthPx - ChartLeftPadding - ChartRightPadding).coerceAtLeast(1f)
    return ChartLeftPadding + ((ts - minTs).toFloat() / durationMs) * chartWidth
}

/** 十字光标数据卡片：玻璃质感卡片，自动避让图表边界。 */
@Composable
private fun CrosshairTooltip(
    series: List<SeriesData>,
    index: Int,
    relativeSeconds: Float,
    anchorX: Float,
    touchY: Float,
    chartWidth: Float,
    chartHeight: Float,
    formatter: (Float) -> String,
    rightSeries: SeriesData? = null,
    rightFormatter: (Float) -> String = { v -> "%.0f%%".format(Locale.US, v) },
) {
    var tooltipSize by remember { mutableStateOf(IntSize.Zero) }
    val chartColors = LocalChartColors.current
    val offsetX = if (anchorX + tooltipSize.width + 14 <= chartWidth) {
        anchorX + 14f
    } else {
        (anchorX - tooltipSize.width - 14f).coerceAtLeast(4f)
    }
    val offsetY = if (touchY - tooltipSize.height - 12 >= 0f) {
        touchY - tooltipSize.height - 12f
    } else {
        (touchY + 12f).coerceAtMost(chartHeight - tooltipSize.height - 4f)
    }
    Box(
        Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .onSizeChanged { tooltipSize = it },
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(chartColors.tooltipBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "时间 %.2f 秒".format(Locale.US, relativeSeconds),
                color = chartColors.tooltipMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            series.forEach { s ->
                val value = s.points.getOrNull(index)?.second
                if (value != null) {
                    TooltipRow(s.name, formatter(value), s.color, chartColors.tooltipText)
                }
            }
            rightSeries?.let { s ->
                val value = s.points.getOrNull(index)?.second
                if (value != null) {
                    TooltipRow(s.name, rightFormatter(value), s.color, chartColors.tooltipText)
                }
            }
        }
    }
}

@Composable
private fun TooltipRow(name: String, value: String, color: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(name, color = textColor, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun nearestSeriesIndex(touchX: Float, width: Float, count: Int): Int? {
    if (count <= 0) return null
    val chartWidth = (width - ChartLeftPadding - ChartRightPadding).coerceAtLeast(1f)
    val ratio = ((touchX - ChartLeftPadding) / chartWidth).coerceIn(0f, 1f)
    return min(count - 1, max(0, (ratio * (count - 1)).roundToInt()))
}

private fun DrawScope.drawSeriesChart(
    series: List<SeriesData>,
    formatter: (Float) -> String,
    selectedIndex: Int?,
    minTs: Long,
    durationMs: Long,
    rightSeries: SeriesData? = null,
    fixedMax: Float? = null,
    rightFormatter: (Float) -> String = { v -> "%.0f%%".format(Locale.US, v) },
    rightFixedMax: Float? = null,
    yTickCount: Int = 8,
    rightTickCount: Int = 7,
    colors: ChartColors,
) {
    val left = ChartLeftPadding
    val right = size.width - ChartRightPadding
    val top = 18f
    val bottom = size.height - ChartBottomPadding
    val chartWidth = (right - left).coerceAtLeast(1f)
    val chartHeight = (bottom - top).coerceAtLeast(1f)
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.label.toArgb()
        textSize = 25f
    }

    drawLine(colors.axis, Offset(left, top), Offset(left, bottom), strokeWidth = 2f)
    drawLine(colors.axis, Offset(left, bottom), Offset(right, bottom), strokeWidth = 2f)

    val allPoints = series.flatMap { it.points } + (rightSeries?.points ?: emptyList())
    if (allPoints.isEmpty()) return

    val maxValue = max(1f, fixedMax ?: allPoints.maxOf { it.second })
    val rightMax = if (rightSeries != null) max(1f, rightFixedMax ?: (rightSeries.points.maxOfOrNull { it.second } ?: 1f)) else 0f

    niceTicks(maxValue, yTickCount).forEach { value ->
        val y = bottom - (value / maxValue) * chartHeight
        drawLine(colors.grid, Offset(left, y), Offset(right, y), strokeWidth = 1f)
        drawContext.canvas.nativeCanvas.drawText(formatter(value), 0f, y + 8f, textPaint)
    }
    repeat(5) { i ->
        val ratio = i / 4f
        val x = left + ratio * chartWidth
        drawLine(colors.grid, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
        drawContext.canvas.nativeCanvas.drawText("${((durationMs * ratio) / 1000f).roundToInt()}s", x - 18f, size.height - 8f, textPaint)
    }
    if (rightSeries != null) {
        niceTicks(rightMax, rightTickCount).forEach { value ->
            val y = bottom - (value / rightMax) * chartHeight
            drawContext.canvas.nativeCanvas.drawText(rightFormatter(value), right + 6f, y + 8f, textPaint)
        }
    }

    series.forEach { s ->
        if (s.points.size < 2) return@forEach
        val path = Path()
        s.points.forEachIndexed { index, (ts, value) ->
            val x = left + ((ts - minTs).toFloat() / durationMs) * chartWidth
            val y = bottom - (value / maxValue) * chartHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, s.color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
    rightSeries?.let { rs ->
        if (rs.points.size >= 2) {
            val path = Path()
            rs.points.forEachIndexed { index, (ts, value) ->
                val x = left + ((ts - minTs).toFloat() / durationMs) * chartWidth
                val y = bottom - (value / rightMax) * chartHeight
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, rs.color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        }
    }

    selectedIndex?.let { idx ->
        val ref = series.maxByOrNull { it.points.size } ?: return@let
        val point = ref.points.getOrNull(idx) ?: return@let
        val x = left + ((point.first - minTs).toFloat() / durationMs) * chartWidth
        drawLine(colors.crosshair, Offset(x, top), Offset(x, bottom), strokeWidth = 1.5f)
        series.forEach { s ->
            s.points.getOrNull(idx)?.let { p ->
                val y = bottom - (p.second / maxValue) * chartHeight
                drawCircle(s.color, 6f, Offset(x, y))
                drawCircle(Color(0xAAFFFFFF), 6f, Offset(x, y), style = Stroke(width = 2f))
            }
        }
        rightSeries?.points?.getOrNull(idx)?.let { p ->
            val y = bottom - (p.second / rightMax) * chartHeight
            drawCircle(rightSeries.color, 6f, Offset(x, y))
            drawCircle(Color(0xAAFFFFFF), 6f, Offset(x, y), style = Stroke(width = 2f))
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt().coerceIn(0, 255),
    (red * 255).roundToInt().coerceIn(0, 255),
    (green * 255).roundToInt().coerceIn(0, 255),
    (blue * 255).roundToInt().coerceIn(0, 255),
)

private fun formatFreqHz(f: Float): String =
    if (f >= 1_000_000_000f) "%.2f GHz".format(Locale.US, f / 1_000_000_000f)
    else if (f >= 1_000_000f) "%.0f MHz".format(Locale.US, f / 1_000_000f)
    else "%.0f kHz".format(Locale.US, f / 1_000f)

private fun niceTicks(max: Float, targetCount: Int): List<Float> {
    if (max <= 0f) return listOf(0f)
    val slots = (targetCount - 1).coerceAtLeast(1)
    val raw = max / slots
    val mag = 10f.pow(floor(log10(raw.toDouble())).toFloat())
    val step = (ceil(raw / mag) * mag).coerceAtLeast(1e-6f)
    val ticks = ArrayList<Float>(slots + 1)
    var v = 0f
    while (v <= max + step * 1e-3f) {
        ticks += v
        v += step
    }
    return ticks
}

@Composable
private fun AuxCharts(
    detail: DetailUiState,
    globalTs: Long?,
    onTsChange: (Long?) -> Unit,
) {
    val palette = LocalChartColors.current.palette
    val clusterRanges = remember(detail.clusterFreqSamples) {
        detail.clusterFreqSamples.map { it.clusterName }.distinct()
            .mapNotNull { name -> parseCpuRanges(name).takeIf { it.isNotEmpty() }?.let { name to it } }
            .sortedBy { (_, ranges) -> ranges.first().first }
    }
    val clusterColor = { index: Int ->
        when (index) {
            0 -> palette[0]
            1 -> palette[2]
            else -> palette[index % palette.size]
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        detail.fpsSamples.takeIf { it.isNotEmpty() }?.let { fps ->
            TimeSeriesChart(
                "FPS",
                listOf(SeriesData("FPS", palette[0], fps.map { it.timestampMs to it.fps })),
                formatter = { v -> "%.1f".format(Locale.US, v) },
                globalTs = globalTs,
                onTsChange = onTsChange,
            )
        }
        detail.clusterFreqSamples.takeIf { it.isNotEmpty() }?.let { freqs ->
            val series = freqs.groupBy { it.clusterName }.entries
                .sortedBy { (name, _) -> clusterRanges.indexOfFirst { it.first == name }.takeIf { it >= 0 } ?: Int.MAX_VALUE }
                .mapIndexed { i, (name, rows) ->
                    SeriesData(name, clusterColor(i), rows.map { it.timestampMs to it.freqKhz * 1000f })
                }
            TimeSeriesChart("CPU 簇频率", series, ::formatFreqHz, globalTs = globalTs, onTsChange = onTsChange)
        }
        detail.coreUsageSamples.takeIf { it.isNotEmpty() }?.let { cores ->
            val series = cores.groupBy { it.coreIndex }.entries.map { (core, rows) ->
                val clusterIdx = clusterRanges.indexOfFirst { (_, ranges) -> ranges.any { core in it } }
                val color = if (clusterIdx >= 0) clusterColor(clusterIdx) else palette[core % palette.size]
                SeriesData("C$core", color, rows.map { it.timestampMs to it.usagePercent })
            }.sortedBy { it.name.removePrefix("C").toIntOrNull() ?: 0 }
            TimeSeriesChart(
                "CPU 核心占用",
                series,
                { v -> "%.0f%%".format(Locale.US, v) },
                interactive = false,
                fixedMax = 100f,
                yTickCount = 11,
            )
        }
        detail.devicePowerSamples.takeIf { it.isNotEmpty() }?.let { powers ->
            TimeSeriesChart(
                "整机功耗",
                listOf(SeriesData("整机功耗", palette[0], powers.map { it.timestampMs to it.powerUw / 1_000_000f })),
                formatter = { v -> "%.2f W".format(Locale.US, v) },
                globalTs = globalTs,
                onTsChange = onTsChange,
            )
        }
        detail.ddrFreqSamples.takeIf { it.isNotEmpty() }?.let { ddr ->
            TimeSeriesChart(
                "DDR 频率",
                listOf(SeriesData("DDR", palette[0], ddr.map { it.timestampMs to it.freqHz.toFloat() })),
                ::formatFreqHz,
                globalTs = globalTs,
                onTsChange = onTsChange,
            )
        }
    }
}

@Composable
private fun GpuMergedChart(
    detail: DetailUiState,
    globalTs: Long?,
    onTsChange: ((Long?) -> Unit)?,
) {
    val palette = LocalChartColors.current.palette
    detail.gpuSamples.takeIf { it.isNotEmpty() }?.let { gpus ->
        val freq = SeriesData("频率", palette[0], gpus.map { it.timestampMs to it.freqHz.toFloat() })
        val usage = gpus.mapNotNull { it.usagePercent?.let { p -> it.timestampMs to p } }
        if (usage.isNotEmpty()) {
            TimeSeriesChart(
                "GPU 频率与占用",
                listOf(freq),
                ::formatFreqHz,
                globalTs = globalTs,
                onTsChange = onTsChange,
                rightSeries = SeriesData("占用", palette[1], usage),
                rightFormatter = { v -> "%.0f%%".format(Locale.US, v) },
                rightFixedMax = 100f,
                rightTickCount = 11,
            )
        } else {
            TimeSeriesChart("GPU 频率", listOf(freq), ::formatFreqHz, globalTs = globalTs, onTsChange = onTsChange)
        }
    }
}

@Composable
private fun TempChart(
    detail: DetailUiState,
    globalTs: Long?,
    onTsChange: (Long?) -> Unit,
) {
    val palette = LocalChartColors.current.palette
    detail.tempSamples.takeIf { it.isNotEmpty() }?.let { temps ->
        val series = temps.groupBy { it.sensor }.entries.mapIndexed { i, (sensor, rows) ->
            SeriesData(sensor.uppercase(), palette[i % palette.size], rows.map { it.timestampMs to it.tempC })
        }
        TimeSeriesChart("温度", series, formatter = { v -> "%.1f °C".format(Locale.US, v) }, globalTs = globalTs, onTsChange = onTsChange)
    }
}

/** 解析 "CPU0-5"/"CPU0,2,4" 形式的簇名为连续核心区间列表。 */
private fun parseCpuRanges(name: String): List<IntRange> {
    val body = name.removePrefix("CPU").trim()
    if (body.isEmpty()) return emptyList()
    val nums = mutableListOf<Int>()
    body.split(',', ' ', '\t').forEach { seg ->
        val s = seg.trim()
        if (s.isEmpty()) return@forEach
        if ('-' in s) {
            val parts = s.split('-')
            val a = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@forEach
            val b = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return@forEach
            nums += (min(a, b)..max(a, b))
        } else {
            s.toIntOrNull()?.let { nums += it }
        }
    }
    if (nums.isEmpty()) return emptyList()
    val sorted = nums.sorted().distinct()
    val ranges = mutableListOf<IntRange>()
    var start = sorted[0]
    var prev = sorted[0]
    for (i in 1 until sorted.size) {
        if (sorted[i] == prev + 1) {
            prev = sorted[i]
        } else {
            ranges += start..prev
            start = sorted[i]
            prev = sorted[i]
        }
    }
    ranges += start..prev
    return ranges
}

/**
 * 实时功耗柱状图：每个分区一根柱子，底部对齐、顶部圆角 + 渐变填充。
 * 相比折线/散点，柱状更适合「同一时刻各分区谁更耗电」的横向比较。
 */
@Composable
private fun LiveBarChart(samples: List<ZoneSample>, palette: List<Color>) {
    val colors = LocalChartColors.current
    val density = LocalDensity.current
    val tickTextPx = with(density) { 9.sp.toPx() }
    val valueTextPx = with(density) { 9.5.sp.toPx() }
    val zoneTextPx = with(density) { 9.sp.toPx() }

    Canvas(Modifier.fillMaxWidth().height(206.dp)) {
        val left = 40.dp.toPx()
        val right = size.width - 2.dp.toPx()
        val top = 26.dp.toPx()
        val bottom = size.height - 26.dp.toPx()
        val chartWidth = (right - left).coerceAtLeast(1f)
        val chartHeight = (bottom - top).coerceAtLeast(1f)

        val tickPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.label.toArgb()
            textSize = tickTextPx
        }
        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.label.toArgb()
            textSize = zoneTextPx
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val valuePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = valueTextPx
            textAlign = android.graphics.Paint.Align.CENTER
        }

        drawLine(colors.axis, Offset(left, bottom), Offset(right, bottom), strokeWidth = 2f)

        val maxW = max(1L, samples.maxOfOrNull { it.effectivePowerUw } ?: 1L) / 1_000_000f
        val ticks = niceTicks(maxW * 1.08f, 5)
        val axisMax = max(0.001f, ticks.last())
        ticks.forEach { tick ->
            if (tick > axisMax + 1e-4f) return@forEach
            val y = bottom - (tick / axisMax) * chartHeight
            if (tick > 0f) drawLine(colors.grid, Offset(left, y), Offset(right, y), strokeWidth = 1f)
            val text = when {
                axisMax >= 10f -> "%.0f".format(Locale.US, tick)
                axisMax >= 1f -> "%.1f".format(Locale.US, tick)
                else -> "%.2f".format(Locale.US, tick)
            }
            drawContext.canvas.nativeCanvas.drawText(text, 0f, y + tickTextPx * 0.36f, tickPaint)
        }

        val slot = chartWidth / samples.size
        val barWidth = min(slot * 0.56f, 30.dp.toPx())
        samples.forEachIndexed { index, sample ->
            val valueW = sample.effectivePowerUw / 1_000_000f
            val centerX = left + slot * (index + 0.5f)
            val barHeight = (valueW / axisMax * chartHeight).coerceIn(2.dp.toPx(), chartHeight)
            val color = palette[index % palette.size]

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(color, color.copy(alpha = 0.45f)),
                    startY = bottom - barHeight,
                    endY = bottom,
                ),
                topLeft = Offset(centerX - barWidth / 2f, bottom - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )

            if (slot >= 26.dp.toPx()) {
                valuePaint.color = color.toArgb()
                val valueY = (bottom - barHeight - 6.dp.toPx()).coerceAtLeast(top + valueTextPx)
                drawContext.canvas.nativeCanvas.drawText(
                    "%.2f".format(Locale.US, valueW),
                    centerX,
                    valueY,
                    valuePaint,
                )
            }
            val zoneLabel = fitLabel(sample.zoneName.uppercase(), slot - 2.dp.toPx(), labelPaint)
            drawContext.canvas.nativeCanvas.drawText(zoneLabel, centerX, bottom + 16.dp.toPx(), labelPaint)
        }
    }
}

/** 按可用宽度截断标签，超出时补省略号。 */
private fun fitLabel(text: String, maxWidth: Float, paint: android.graphics.Paint): String {
    if (paint.measureText(text) <= maxWidth) return text
    var trimmed = text
    while (trimmed.isNotEmpty() && paint.measureText("$trimmed…") > maxWidth) {
        trimmed = trimmed.dropLast(1)
    }
    return if (trimmed.isEmpty()) "" else "$trimmed…"
}

// ---------- 历史 ----------

@Composable
private fun History(
    records: List<RecordEntity>,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    bottomPadding: Dp = 24.dp,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (records.isNotEmpty()) {
            item {
                Text(
                    "共 ${records.size} 条记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        if (records.isEmpty()) {
            item {
                MiuixCard {
                    Text(
                        "点击仪表盘的「开始记录」，或使用悬浮球开始记录当前应用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(records, key = { it.id }) { record ->
            MiuixCard(onClick = { onOpen(record.id) }, contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(record.iconPackage ?: record.packageName, Modifier.size(46.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        AppNameText(
                            record.appName,
                            record.packageName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                        )
                        Text(
                            "${formatDate(record.startedAtMs)} · ${duration(record)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill("SOC ${uwToW(record.avgSocPowerUw)}", PillTone.NEUTRAL)
                            StatusPill(
                                "帧率 ${record.avgFps?.let { "%.1f".format(Locale.US, it) } ?: "--"}",
                                PillTone.NEUTRAL,
                            )
                        }
                    }
                    MiuixIconButton(onClick = { onDelete(record.id) }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------- 设置 ----------

@Composable
private fun SettingsScreen(
    enabled: Boolean,
    interval: SamplingInterval,
    themeMode: ThemeMode,
    bubbleEnabled: Boolean,
    osdEnabled: Boolean,
    osdMetrics: List<OsdMetric>,
    permissions: AppPermissions,
    glassBottomBar: Boolean,
    glassIntensity: GlassIntensity,
    bottomPadding: Dp = 24.dp,
    onToggleEnabled: (Boolean) -> Unit,
    onInterval: (SamplingInterval) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onGlassBottomBar: (Boolean) -> Unit,
    onGlassIntensity: (GlassIntensity) -> Unit,
    onStartService: () -> Unit,
    onRequestBatteryWhitelist: () -> Unit,
    onOpenMiuiAutostart: () -> Unit,
    onToggleBubble: (Boolean) -> Unit,
    onToggleOsd: (Boolean) -> Unit,
    onOsdMetricsChange: (List<OsdMetric>) -> Unit,
    fpsLayer: String?,
    fpsLayers: List<String>,
    onSelectFpsLayer: (String?) -> Unit,
    onLoadFpsLayers: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("外观") }
        item {
            MiuixCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Palette)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("主题", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "深色模式在夜间更省电、更易读",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                SegmentedControl(
                    options = ThemeMode.entries.map { it.label },
                    selectedIndex = ThemeMode.entries.indexOf(themeMode),
                    onSelect = { onThemeMode(ThemeMode.entries[it]) },
                )
            }
        }
        item {
            MiuixCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Layers)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("悬浮玻璃底栏", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "底栏浮在内容之上，液态玻璃样式仅在应用内使用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MiuixSwitch(checked = glassBottomBar, onCheckedChange = onGlassBottomBar)
                }
                if (glassBottomBar) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Opacity, MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(14.dp))
                        Text("液态玻璃浓度", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    SegmentedControl(
                        options = GlassIntensity.entries.map { it.label },
                        selectedIndex = GlassIntensity.entries.indexOf(glassIntensity),
                        onSelect = { onGlassIntensity(GlassIntensity.entries[it]) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "轻盈更通透，浓郁更强调底栏轮廓；三档都保持内容可读。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionTitle("采样") }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = "QPT 总开关",
                    subtitle = if (enabled) "开启后读取各功耗分区" else "关闭后停止采样",
                    icon = Icons.Rounded.Speed,
                    trailing = { MiuixSwitch(checked = enabled, onCheckedChange = onToggleEnabled) },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "采样间隔",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SamplingInterval.entries.forEach {
                            SelectChip(
                                text = it.label,
                                selected = it == interval,
                                onClick = { onInterval(it) },
                            )
                        }
                    }
                    Text(
                        "间隔越短数据越细，功耗与存储开销也越高；建议 200 毫秒。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionTitle("悬浮窗") }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = "悬浮球",
                    subtitle = "点击记录 / 双击展开快捷菜单",
                    icon = Icons.Rounded.Layers,
                    trailing = { MiuixSwitch(checked = bubbleEnabled, onCheckedChange = onToggleBubble) },
                )
                ActionRow(
                    title = "OSD 实时浮层",
                    subtitle = "纯色半透明浮层，可拖动、不写入记录",
                    icon = Icons.Rounded.Notifications,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    trailing = { MiuixSwitch(checked = osdEnabled, onCheckedChange = onToggleOsd) },
                    showDivider = true,
                )
            }
        }

        if (osdEnabled) {
            item {
                MiuixCard(contentPadding = 0.dp) {
                    Text(
                        "OSD 显示项",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp),
                    )
                    osdMetrics.let { selected ->
                        OsdMetric.entries.forEach { metric ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOsdMetricsChange(
                                            if (selected.contains(metric)) selected - metric else selected + metric,
                                        )
                                    }
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MiuixCheckbox(
                                    state = ToggleableState(selected.contains(metric)),
                                    onClick = {
                                        onOsdMetricsChange(
                                            if (selected.contains(metric)) selected - metric else selected + metric,
                                        )
                                    },
                                )
                                Text(metric.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("FPS 数据源") }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = "自动跟随前台应用",
                    subtitle = "优先 SurfaceView 图层，失败回退 gfxinfo",
                    icon = Icons.Rounded.Timer,
                    trailing = {
                        if (fpsLayer == null) {
                            StatusPill("已启用", PillTone.OK)
                        } else {
                            MiuixTextButton(text = "启用", onClick = { onSelectFpsLayer(null) })
                        }
                    },
                    onClick = { onSelectFpsLayer(null) },
                )
                Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()) {
                    PillButton(
                        text = if (fpsLayers.isEmpty()) "扫描 SurfaceFlinger 图层" else "重新扫描图层",
                        onClick = onLoadFpsLayers,
                        filled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (fpsLayers.isNotEmpty()) {
                    Text(
                        "手动指定图层（${fpsLayers.size}）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    fpsLayers.forEach { layer ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectFpsLayer(layer) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                layer,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (fpsLayer == layer) StatusPill("使用中", PillTone.OK)
                        }
                    }
                } else {
                    Text(
                        "未扫描到图层：确认已授予 root；游戏需在前台运行时扫描才能看到其 SurfaceView 层。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        item { SectionTitle("服务与保活") }
        item {
            MiuixCard(contentPadding = 0.dp) {
                ActionRow(
                    title = "权限中心",
                    subtitle = "${permissions.requiredGranted()} / ${permissions.requiredTotal()} 项核心权限已就绪",
                    icon = Icons.Rounded.Shield,
                    iconTint = if (permissions.requiredReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    trailing = {
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = onOpenPermissions,
                )
                ActionRow(
                    title = "重启前台服务",
                    subtitle = "通知被系统清理后可手动恢复",
                    icon = Icons.Rounded.Refresh,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = onStartService,
                    showDivider = true,
                )
                ActionRow(
                    title = "电池优化白名单",
                    subtitle = if (permissions.batteryUnrestricted) "已加入白名单" else "避免息屏后停止采样",
                    icon = Icons.Rounded.BatteryChargingFull,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onClick = onRequestBatteryWhitelist,
                    showDivider = true,
                )
                if (permissions.isMiui) {
                    ActionRow(
                        title = "MIUI 自启动管理",
                        subtitle = "HyperOS 建议允许自启动",
                        icon = Icons.Rounded.Settings,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        onClick = onOpenMiuiAutostart,
                        showDivider = true,
                    )
                }
            }
        }

        item {
            val context = LocalContext.current
            MiuixCard(contentPadding = 0.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Icons.Rounded.Info, MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("WattScope Next", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "版本 ${BuildConfig.VERSION_NAME} · 高通 QPT 功耗遥测",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ActionRow(
                    title = "GitHub 项目主页",
                    subtitle = "github.com/RuSheng920/WattScope-Next",
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
                    showDivider = true,
                )
            }
        }
    }
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ---------- 详情页 ----------

@Composable
private fun DetailPage(
    detail: DetailUiState,
    onDismiss: () -> Unit,
    onExport: (Boolean) -> Unit,
) {
    val visibleZones = remember { mutableStateMapOf<String, Boolean>() }
    // 记录更新会不断产生新的 stats：只补新键，保留用户已做的显隐选择
    LaunchedEffect(detail.stats) {
        detail.stats.forEach { if (!visibleZones.containsKey(it.zoneName)) visibleZones[it.zoneName] = true }
    }
    var crosshairTs by remember { mutableStateOf<Long?>(null) }
    var backProgress by remember { mutableFloatStateOf(0f) }

    // 预测性返回：手势进行时页面跟随位移/缩放，松手完成后关闭
    PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { backProgress = it.progress }
        } finally {
            backProgress = 0f
        }
        onDismiss()
    }

    val scrimAlpha by animateFloatAsState(1f - backProgress * 0.35f, label = "scrim")

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f * scrimAlpha))) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = backProgress
                    translationX = size.width * 0.16f * p
                    scaleX = 1f - 0.05f * p
                    scaleY = 1f - 0.05f * p
                }
                .clip(RoundedCornerShape(if (backProgress > 0f) 24.dp else 0.dp))
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixIconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("记录详情", style = MaterialTheme.typography.titleLarge)
                        detail.record?.let { record ->
                            Text(
                                "${formatDate(record.startedAtMs)} · ${duration(record)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    MiuixTextButton(text = "JSON", onClick = { onExport(true) })
                    MiuixTextButton(text = "CSV", onClick = { onExport(false) })
                }

                LazyColumn(
                    Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    detail.record?.let { record ->
                        item {
                            MiuixCard(contentPadding = 14.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(record.iconPackage ?: record.packageName, Modifier.size(44.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        AppNameText(
                                            record.appName,
                                            record.packageName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                        )
                                        Text(
                                            record.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { StatGrid(detail.stats, detail.samples.size, detail.avgFps) }
                    item { AvgPowerSection(detail) }
                    item { SectionTitle("曲线") }
                    if (detail.hasChartData) {
                        item { AuxCharts(detail, crosshairTs, { crosshairTs = it }) }
                    }
                    item {
                        ZoneCharts(detail, detail.samples.filter { visibleZones[it.zoneName] != false }, crosshairTs, { crosshairTs = it })
                    }
                    item { TempChart(detail, crosshairTs, { crosshairTs = it }) }
                    item { SectionTitle("分区筛选") }
                    item {
                        MiuixCard(contentPadding = 0.dp) {
                            detail.stats.forEachIndexed { index, stat ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { visibleZones[stat.zoneName] = visibleZones[stat.zoneName] == false }
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MiuixCheckbox(
                                        state = ToggleableState(visibleZones[stat.zoneName] != false),
                                        onClick = { visibleZones[stat.zoneName] = visibleZones[stat.zoneName] == false },
                                    )
                                    Text(stat.zoneName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "均值 ${uwToW(stat.avgPowerUw)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                }
                                if (index < detail.stats.lastIndex) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 56.dp)
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvgPowerSection(detail: DetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("平均功耗")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            detail.stats.forEach { stat ->
                if (!stat.zoneName.equals("SOC", true)) {
                    SummaryTile(stat.zoneName.uppercase(), uwToW(stat.avgPowerUw))
                }
            }
            if (detail.avgDevicePowerUw > 0) {
                SummaryTile("整机", uwToW(detail.avgDevicePowerUw))
            }
        }
    }
}

@Composable
private fun StatGrid(stats: List<ZoneStats>, sampleCount: Int, avgFps: Float) {
    val soc = stats.firstOrNull { it.zoneName.equals("SOC", true) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("SOC 最高", uwToW(soc?.maxPowerUw ?: 0), Modifier.weight(1f))
            SummaryTile("SOC 均值", uwToW(soc?.avgPowerUw ?: 0), Modifier.weight(1f))
            SummaryTile("SOC 最低", uwToW(soc?.minPowerUw ?: 0), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("采样点", sampleCount.toString(), Modifier.weight(1f))
            SummaryTile("平均帧率", if (avgFps > 0f) "%.1f".format(Locale.US, avgFps) else "--", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ZoneCharts(
    detail: DetailUiState,
    samples: List<SampleEntity>,
    globalTs: Long? = null,
    onTsChange: ((Long?) -> Unit)? = null,
) {
    val palette = LocalChartColors.current.palette
    val order = listOf("soc", "cpu-m", "cpu-l", "gpu", "nsp", "debug-0", "debug-2", "debug-4")
    val grouped = samples.groupBy { it.zoneName }
    val ordered = grouped.entries.sortedBy { (name, _) -> order.indexOf(name).takeIf { it >= 0 } ?: Int.MAX_VALUE }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ordered.forEachIndexed { index, entry ->
            ZonePowerChart(
                zoneName = entry.key,
                samples = entry.value,
                lineColor = palette[index % palette.size],
                globalTs = globalTs,
                onTsChange = onTsChange,
            )
            if (entry.key.equals("cpu-l", ignoreCase = true)) {
                GpuMergedChart(detail, globalTs, onTsChange)
            }
        }
    }
}

@Composable
private fun ZonePowerChart(
    zoneName: String,
    samples: List<SampleEntity>,
    lineColor: Color,
    globalTs: Long? = null,
    onTsChange: ((Long?) -> Unit)? = null,
) {
    var touching by remember { mutableStateOf(false) }
    var touchY by remember { mutableStateOf(0f) }
    var widthPx by remember { mutableStateOf(0) }
    val tsList = remember(samples) { samples.map { it.timestampMs } }
    val crosshairIndex = globalTs?.let { ts ->
        tsList.indexOf(ts).takeIf { it >= 0 } ?: nearestByTs(ts, tsList)
    }
    val showTooltip = crosshairIndex != null && globalTs != null
    val chartColors = LocalChartColors.current
    val density = LocalDensity.current
    val chartHeightPx = with(density) { 210.dp.toPx() }

    MiuixCard(contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(lineColor))
            Spacer(Modifier.width(8.dp))
            Text(zoneName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "共 ${samples.size} 个采样点",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth()) {
            val minTs = tsList.minOrNull() ?: 0L
            val maxTs = tsList.maxOrNull() ?: minTs
            val durationMs = (maxTs - minTs).coerceAtLeast(1L)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .onSizeChanged { widthPx = it.width }
                    .then(
                        if (onTsChange != null) {
                            Modifier.pointerInputChart(tsList, onTsChange) { isTouching, y ->
                                touching = isTouching
                                touchY = y
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                drawSingleZoneChart(
                    samples = samples,
                    lineColor = lineColor,
                    selectedIndex = crosshairIndex,
                    minTs = minTs,
                    durationMs = durationMs,
                    colors = chartColors,
                )
            }
            if (showTooltip) {
                val ts = globalTs ?: return@Box
                val effTouchY = if (touching) touchY else chartHeightPx * 0.4f
                val chartWidth = (widthPx - ChartLeftPadding - ChartRightPadding).coerceAtLeast(1f)
                val x = ChartLeftPadding + ((ts - minTs).toFloat() / durationMs) * chartWidth
                CrosshairTooltip(
                    series = listOf(
                        SeriesData(zoneName, lineColor, samples.map { it.timestampMs to it.effectivePowerUw() / 1_000_000f }),
                    ),
                    index = crosshairIndex ?: 0,
                    relativeSeconds = (ts - minTs) / 1000f,
                    anchorX = x,
                    touchY = effTouchY,
                    chartWidth = widthPx.toFloat(),
                    chartHeight = chartHeightPx,
                    formatter = { v -> uwToW((v * 1_000_000f).toLong()) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ChartAverages(
            listOf(SeriesData(zoneName, lineColor, samples.map { it.timestampMs to it.effectivePowerUw() / 1_000_000f })),
            { v -> uwToW((v * 1_000_000f).toLong()) },
        )
    }
}

private fun DrawScope.drawSingleZoneChart(
    samples: List<SampleEntity>,
    lineColor: Color,
    selectedIndex: Int?,
    minTs: Long = samples.minOfOrNull { it.timestampMs } ?: 0L,
    durationMs: Long = (samples.maxOfOrNull { it.timestampMs } ?: minTs).let { (it - minTs).coerceAtLeast(1L) },
    yTickCount: Int = 8,
    colors: ChartColors,
) {
    val left = ChartLeftPadding
    val right = size.width - ChartRightPadding
    val top = 18f
    val bottom = size.height - ChartBottomPadding
    val chartWidth = (right - left).coerceAtLeast(1f)
    val chartHeight = (bottom - top).coerceAtLeast(1f)
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.label.toArgb()
        textSize = 25f
    }

    drawLine(colors.axis, Offset(left, top), Offset(left, bottom), strokeWidth = 2f)
    drawLine(colors.axis, Offset(left, bottom), Offset(right, bottom), strokeWidth = 2f)

    val maxPower = max(1L, samples.maxOfOrNull { it.effectivePowerUw() } ?: 1L)

    niceTicks(maxPower.toFloat(), yTickCount).forEach { value ->
        val y = bottom - (value / maxPower) * chartHeight
        drawLine(colors.grid, Offset(left, y), Offset(right, y), strokeWidth = 1f)
        drawContext.canvas.nativeCanvas.drawText(uwToW(value.toLong()), 0f, y + 8f, textPaint)
    }
    repeat(5) { i ->
        val ratio = i / 4f
        val x = left + ratio * chartWidth
        drawLine(colors.grid, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
        drawContext.canvas.nativeCanvas.drawText("${((durationMs * ratio) / 1000f).roundToInt()}s", x - 18f, size.height - 8f, textPaint)
    }

    if (samples.isEmpty()) return

    val path = Path()
    samples.forEachIndexed { index, sample ->
        val x = left + ((sample.timestampMs - minTs).toFloat() / durationMs) * chartWidth
        val y = bottom - (sample.effectivePowerUw().toFloat() / maxPower) * chartHeight
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

    selectedIndex?.let { index ->
        val sample = samples.getOrNull(index) ?: return@let
        val x = left + ((sample.timestampMs - minTs).toFloat() / durationMs) * chartWidth
        val y = bottom - (sample.effectivePowerUw().toFloat() / maxPower) * chartHeight
        drawLine(colors.crosshair, Offset(x, top), Offset(x, bottom), strokeWidth = 1.5f)
        drawLine(colors.crosshair, Offset(left, y), Offset(right, y), strokeWidth = 1.5f)
        drawCircle(lineColor, 7f, Offset(x, y))
        drawContext.canvas.nativeCanvas.drawText(
            "${timeLabel(sample.timestampMs)}  ${uwToW(sample.effectivePowerUw())}  ${ujToJ(sample.energyUj)}",
            left + 8f,
            top + 30f,
            textPaint,
        )
    }
}

private fun nearestSampleIndex(samples: List<SampleEntity>, touchX: Float, width: Float, left: Float, rightPadding: Float): Int? {
    if (samples.isEmpty()) return null
    val chartWidth = (width - left - rightPadding).coerceAtLeast(1f)
    val ratio = ((touchX - left) / chartWidth).coerceIn(0f, 1f)
    return min(samples.lastIndex, max(0, (ratio * samples.lastIndex).roundToInt()))
}

private fun SampleEntity.effectivePowerUw(): Long = powerUw ?: computedPowerUw ?: 0L
private fun uwToW(uw: Long): String = "%.2f W".format(Locale.US, uw / 1_000_000.0)
private fun ujToJ(uj: Long): String = if (uj >= 1_000_000) "%.3f J".format(Locale.US, uj / 1_000_000.0) else "${uj} µJ"
private fun formatDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
private fun timeLabel(ms: Long): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(ms))
private fun duration(record: RecordEntity): String {
    val end = record.endedAtMs ?: System.currentTimeMillis()
    val seconds = ((end - record.startedAtMs) / 1000).coerceAtLeast(0)
    return "${seconds / 60} 分 ${seconds % 60} 秒"
}

/** 应用内展示的项目主页。 */
private const val GITHUB_URL = "https://github.com/RuSheng920/WattScope-Next"
