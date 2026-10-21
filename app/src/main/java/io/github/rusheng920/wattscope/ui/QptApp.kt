package io.github.rusheng920.wattscope.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.rusheng920.wattscope.BuildConfig
import io.github.rusheng920.wattscope.R
import io.github.rusheng920.wattscope.data.local.RecordEntity
import io.github.rusheng920.wattscope.data.local.SampleEntity
import io.github.rusheng920.wattscope.domain.AppPermissions
import io.github.rusheng920.wattscope.domain.DetailChartSection
import io.github.rusheng920.wattscope.domain.StatusText
import io.github.rusheng920.wattscope.domain.ThemeMode
import io.github.rusheng920.wattscope.domain.ZoneSample
import io.github.rusheng920.wattscope.domain.ZoneStats
import io.github.rusheng920.wattscope.domain.zoneDisplayRank
import io.github.rusheng920.wattscope.ui.components.ActionRow
import io.github.rusheng920.wattscope.ui.components.BottomItem
import io.github.rusheng920.wattscope.ui.components.EmptyState
import io.github.rusheng920.wattscope.ui.components.FloatingGlassBottomBar
import io.github.rusheng920.wattscope.ui.components.IconBadge
import io.github.rusheng920.wattscope.ui.components.MiuixBottomBar
import io.github.rusheng920.wattscope.ui.components.MiuixCard
import io.github.rusheng920.wattscope.ui.components.MiuixSwitch
import io.github.rusheng920.wattscope.ui.components.PillButton
import io.github.rusheng920.wattscope.ui.components.PillTone
import io.github.rusheng920.wattscope.ui.components.SectionTitle
import io.github.rusheng920.wattscope.ui.components.SegmentedControl
import io.github.rusheng920.wattscope.ui.components.SelectChip
import io.github.rusheng920.wattscope.ui.components.AnimatedListItem
import io.github.rusheng920.wattscope.ui.components.StatusPill
import io.github.rusheng920.wattscope.ui.components.SurfaceTile
import io.github.rusheng920.wattscope.ui.theme.ChartColors
import io.github.rusheng920.wattscope.ui.theme.LocalChartColors
import io.github.rusheng920.wattscope.ui.theme.LocalStatusColors
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
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
    val bubbleSize by viewModel.bubbleSize.collectAsState()
    val bubbleSnapToEdge by viewModel.bubbleSnapToEdge.collectAsState()
    val osdEnabled by viewModel.osdEnabled.collectAsState()
    val osdMetrics by viewModel.osdMetrics.collectAsState()
    val fpsLayer by viewModel.fpsLayer.collectAsState()
    val fpsLayers by viewModel.fpsLayers.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val glassBottomBar by viewModel.glassBottomBar.collectAsState()
    val glassIntensity by viewModel.glassIntensity.collectAsState()
    val glassPauseRecording by viewModel.glassPauseRecording.collectAsState()
    val dualCellMode by viewModel.dualCellMode.collectAsState()
    val onboardingDone by viewModel.onboardingDone.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val pendingUpdate by viewModel.pendingUpdate.collectAsState()
    val autoUpdatePrompt by viewModel.autoUpdatePrompt.collectAsState()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState()
    val updateChannel by viewModel.updateChannel.collectAsState()
    val lastUpdateCheckMs by viewModel.lastUpdateCheckMs.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var permissionsOpen by remember { mutableStateOf(false) }
    // 设置二级页：null = 停在设置索引页
    var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
    var tabBackProgress by remember { mutableFloatStateOf(0f) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 页面底色带一点点纵向冷暖过渡（顶部与 MIUIX surface 完全一致，所以顶栏没有接缝）。
    // 这不是装饰：液态玻璃需要有「可折射的内容」才像玻璃，纯灰底上的玻璃永远是灰片。
    val pageBrush = remember(isDarkTheme) {
        val colors = if (isDarkTheme) {
            listOf(Color(0xFF000000), Color(0xFF05080D), Color(0xFF0A1119))
        } else {
            listOf(Color(0xFFF7F7F7), Color(0xFFF5F7FB), Color(0xFFEEF3FA))
        }
        Brush.verticalGradient(colors)
    }

    // 液态玻璃的采样源：把整棵内容树录制到一张 GraphicsLayer，
    // 底栏的 drawBackdrop 再从这张图里取「自己身后的那一段」做模糊与折射。
    // 背景色必须画进 backdrop，否则底栏采样到的是透明像素。
    val glassBackdrop = rememberLayerBackdrop(
        onDraw = remember(pageBrush) {
            {
                drawRect(brush = pageBrush)
                drawContent()
            }
        },
    )

    // 记录期间默认关掉实时模糊：逐帧 RenderEffect 会抬高 GPU 功耗，污染测量结果
    val liveGlass = !(glassPauseRecording && state.isRecording)

    // 应用可见期间每 1.5s 刷新一次权限：root / 悬浮窗 / 通知状态变化无需重启应用
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshPermissions()
            kotlinx.coroutines.delay(1_500)
        }
    }

    // HyperOS 大标题随列表滚动折叠：同一 ScrollBehavior 同时挂到 TopAppBar 与内容区
    val topBarScroll = MiuixScrollBehavior()

    // 换 Tab / 进出设置二级页时列表都是新的（滚动位置归零），
    // 但折叠偏移属于 ScrollBehavior 而非列表，必须手动复位，否则新页面会顶着「已收起」的标题。
    LaunchedEffect(tab, settingsPage) {
        topBarScroll.state.heightOffset = 0f
    }

    // 预测性返回：二级页 → 设置索引页 → 首页。两级都要有各自的跟手动画。
    PredictiveBackHandler(enabled = detailId == null && !permissionsOpen && settingsPage != null) { progress ->
        try {
            progress.collect { tabBackProgress = it.progress }
        } finally {
            tabBackProgress = 0f
        }
        settingsPage = null
    }
    PredictiveBackHandler(
        enabled = detailId == null && !permissionsOpen && settingsPage == null && tab != 0,
    ) { progress ->
        try {
            progress.collect { tabBackProgress = it.progress }
        } finally {
            tabBackProgress = 0f
        }
        tab = 0
    }

    Box(Modifier.fillMaxSize().background(pageBrush)) {
        Scaffold(
            // 只有内容层参与玻璃采样；悬浮底栏在 Scaffold 之外，避免自引用
            modifier = Modifier.layerBackdrop(glassBackdrop),
            containerColor = Color.Transparent,
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
                                    contentDescription = stringResource(R.string.action_toggle_theme),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                            MiuixIconButton(onClick = viewModel::refresh) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.action_refresh),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        },
                    )

                    1 -> TopAppBar(
                        title = stringResource(R.string.nav_history),
                        largeTitle = stringResource(R.string.nav_history),
                        scrollBehavior = topBarScroll,
                        actions = {
                            if (records.isNotEmpty()) {
                                MiuixTextButton(text = stringResource(R.string.action_clear), onClick = viewModel::deleteAll)
                            }
                        },
                    )

                    // 设置二级页：返回箭头 + 页标题。大标题折叠行为与一级页一致，
                    // 顶栏高度不变，进出二级页时内容不会上下跳一截。
                    else -> {
                        val page = settingsPage
                        if (page == null) {
                            TopAppBar(
                                title = stringResource(R.string.nav_settings),
                                largeTitle = stringResource(R.string.nav_settings),
                                scrollBehavior = topBarScroll,
                            )
                        } else {
                            // 二级页用小标题：不参与列表折叠。
                            // 复用一级页的 scrollBehavior 会把「已收起的」大标题偏移带到新列表上，
                            // 刚进页面就看不到标题，直到滚动一下才恢复。
                            SmallTopAppBar(
                                title = stringResource(page.titleRes),
                                navigationIcon = {
                                    MiuixIconButton(onClick = { settingsPage = null }) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = stringResource(R.string.action_back),
                                            tint = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(21.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            },
            bottomBar = {
                // 二级页独占整屏：底部导航收起，返回靠顶栏箭头与系统返回手势
                if (!glassBottomBar && settingsPage == null) {
                    MiuixBottomBar(
                        items = listOf(
                            BottomItem(stringResource(R.string.nav_dashboard), Icons.Rounded.Speed),
                            BottomItem(stringResource(R.string.nav_history), Icons.Rounded.History),
                            BottomItem(stringResource(R.string.nav_settings), Icons.Rounded.Settings),
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

                    else -> {
                        // 设置：索引页 → 六个独立二级页。二级页无底栏，所以底部留白收窄。
                        val page = settingsPage
                        val pagePadding = if (glassBottomBar && page == null) 104.dp else 24.dp
                        when (page) {
                            null -> SettingsHub(
                                themeMode = themeMode,
                                interval = state.interval,
                                bubbleEnabled = bubbleEnabled,
                                osdEnabled = osdEnabled,
                                fpsLayer = fpsLayer,
                                permissions = permissions,
                                onOpen = { settingsPage = it },
                                bottomPadding = pagePadding,
                            )

                            SettingsPage.THEME -> ThemeSettingsPage(
                                themeMode = themeMode,
                                glassBottomBar = glassBottomBar,
                                glassIntensity = glassIntensity,
                                glassPauseRecording = glassPauseRecording,
                                onThemeMode = viewModel::setThemeMode,
                                onGlassBottomBar = viewModel::setGlassBottomBar,
                                onGlassIntensity = viewModel::setGlassIntensity,
                                onGlassPauseRecording = viewModel::setGlassPauseRecording,
                                bottomPadding = pagePadding,
                            )

                            SettingsPage.SAMPLING -> SamplingSettingsPage(
                                enabled = state.qptEnabled,
                                interval = state.interval,
                                dualCellMode = dualCellMode,
                                onToggleEnabled = viewModel::setEnabled,
                                onInterval = viewModel::setInterval,
                                onDualCellMode = viewModel::setDualCellMode,
                                bottomPadding = pagePadding,
                            )

                            SettingsPage.OVERLAY -> OverlaySettingsPage(
                                bubbleEnabled = bubbleEnabled,
                                bubbleSize = bubbleSize,
                                bubbleSnapToEdge = bubbleSnapToEdge,
                                osdEnabled = osdEnabled,
                                osdMetrics = osdMetrics,
                                onToggleBubble = onToggleBubble,
                                onBubbleSize = viewModel::setBubbleSize,
                                onBubbleSnapToEdge = viewModel::setBubbleSnapToEdge,
                                onToggleOsd = viewModel::setOsdEnabled,
                                onOsdMetricsChange = viewModel::setOsdMetrics,
                                bottomPadding = pagePadding,
                            )

                            SettingsPage.FPS -> FpsSettingsPage(
                                fpsLayer = fpsLayer,
                                fpsLayers = fpsLayers,
                                onSelectFpsLayer = viewModel::setFpsLayer,
                                onLoadFpsLayers = viewModel::loadFpsLayers,
                                bottomPadding = pagePadding,
                            )

                            SettingsPage.SERVICE -> ServiceSettingsPage(
                                permissions = permissions,
                                onOpenPermissions = { permissionsOpen = true },
                                onStartService = onStartService,
                                onRequestBatteryWhitelist = onRequestBatteryWhitelist,
                                onOpenMiuiAutostart = onOpenMiuiAutostart,
                                bottomPadding = pagePadding,
                            )

                            SettingsPage.ABOUT -> AboutSettingsPage(
                                autoUpdate = autoUpdateEnabled,
                                channel = updateChannel,
                                updateState = updateState,
                                pending = pendingUpdate,
                                lastCheckMs = lastUpdateCheckMs,
                                onAutoUpdate = viewModel::setAutoUpdateEnabled,
                                onChannel = viewModel::setUpdateChannel,
                                onCheckUpdate = viewModel::checkUpdate,
                                onDownload = viewModel::downloadUpdate,
                                bottomPadding = pagePadding,
                            )
                        }
                    }
                }
            }
        }

        // 悬浮玻璃底栏：覆盖在内容之上，列表滚动时会从玻璃后面穿过
        if (glassBottomBar && settingsPage == null) {
            // 外层 Box 负责贴底对齐：FloatingNavigationBar 的 modifier 落在内部 Row 上
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                FloatingGlassBottomBar(
                    items = listOf(
                        BottomItem(stringResource(R.string.nav_dashboard), Icons.Rounded.Speed),
                        BottomItem(stringResource(R.string.nav_history), Icons.Rounded.History),
                        BottomItem(stringResource(R.string.nav_settings), Icons.Rounded.Settings),
                    ),
                    selected = tab,
                    onSelect = { tab = it },
                    backdrop = glassBackdrop,
                    intensity = glassIntensity,
                    dark = isDarkTheme,
                    live = liveGlass,
                )
            }
        }

        detailId?.let { id ->
            val detail by remember(id) { viewModel.stats(id) }.collectAsState()
            val chartOrder by viewModel.detailChartOrder.collectAsState()
            DetailPage(
                detail = detail,
                chartOrder = chartOrder,
                onChartOrderChange = viewModel::setDetailChartOrder,
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

        // 启动自动检测命中新版本：弹一次模态框。手动检查不弹（结果就在关于页上）。
        // 首次引导期间不弹：引导页本身是模态的，叠两层对话框会让人不知道先点哪。
        val offer = pendingUpdate
        if (onboardingDone && autoUpdatePrompt && offer != null) {
            UpdateAvailableDialog(
                info = offer,
                state = updateState,
                onDownload = viewModel::downloadUpdate,
                onDismiss = viewModel::dismissUpdatePrompt,
            )
        }
    }
}

// ---------- 仪表盘 ----------

@Composable
private fun Dashboard(
    status: StatusText,
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
        if (permissions.hasKnownMissingRequiredPermission) {
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
                                stringResource(
                                    R.string.dash_perm_missing_fmt,
                                    permissions.requiredTotal() - permissions.requiredGranted(),
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                stringResource(R.string.dash_perm_hint),
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
                StatTile(stringResource(R.string.dash_stat_zones), samples.size.toString(), Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.dash_stat_device_power),
                    if (devicePowerAvailable) {
                        devicePowerUw?.let { uwToW(it) } ?: "--"
                    } else {
                        stringResource(R.string.state_unavailable)
                    },
                    Modifier.weight(1f),
                )
                StatTile("FPS", latestFps?.let { "%.0f".format(Locale.US, it) } ?: "--", Modifier.weight(1f))
            }
        }

        item {
            MiuixCard(contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dash_live_power), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        if (samples.isEmpty()) {
                            stringResource(R.string.state_waiting)
                        } else {
                            stringResource(
                                R.string.dash_peak_fmt,
                                uwToW(samples.maxOfOrNull { it.effectivePowerUw } ?: 0L),
                            )
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
                            stringResource(R.string.dash_qpt_hint),
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
            SectionTitle(stringResource(R.string.dash_zones_title))
        }

        item {
            if (samples.isEmpty()) {
                MiuixCard {
                    EmptyState(
                        icon = Icons.Rounded.Layers,
                        title = stringResource(R.string.dash_zones_empty_title),
                        description = stringResource(R.string.dash_zones_empty_desc),
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
                            // 必须走浮点：Long/Long 是整数除法会截断（33.33% → 33%），
                            // 最大分区甚至会显示 99% 却把进度条画满，自相矛盾。
                            percent = (sample.effectivePowerUw * 100.0 / maxPower).roundToInt().coerceIn(0, 100),
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
                        stringResource(
                            R.string.dash_zones_found_fmt,
                            zoneNames.size,
                            zoneNames.joinToString(stringResource(R.string.list_separator)),
                        ),
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
    status: StatusText,
    enabled: Boolean,
    isRecording: Boolean,
    canRecord: Boolean,
    socPowerUw: Long,
    onToggleEnabled: (Boolean) -> Unit,
    onRecordClick: () -> Unit,
) {
    val statusColors = LocalStatusColors.current
    val colors = MiuixTheme.colorScheme
    // key 必须包含色值：切换主题时 CompositionLocal 变了但 key 没变，
    // 缓存的 Brush 会继续用旧主题的颜色，渐变不跟着换。
    val heroBrush = remember(enabled, isRecording, statusColors, colors) {
        when {
            isRecording -> Brush.verticalGradient(
                listOf(
                    statusColors.okBg.copy(alpha = 0.50f),
                    statusColors.okBg.copy(alpha = 0.10f),
                )
            )
            enabled -> Brush.verticalGradient(
                listOf(
                    colors.primary.copy(alpha = 0.10f),
                    colors.primary.copy(alpha = 0.02f),
                )
            )
            else -> Brush.verticalGradient(listOf(colors.surfaceContainer, colors.surfaceContainer))
        }
    }
    MiuixCard(containerColor = Color.Transparent, contentPadding = 0.dp) {
        Box(Modifier.background(heroBrush)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "QPT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.hero_soc_power),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
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
                            status.resolve(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            if (isRecording) {
                                stringResource(R.string.state_recording)
                            } else {
                                stringResource(R.string.state_idle)
                            },
                            if (isRecording) PillTone.OK else PillTone.NEUTRAL,
                        )
                        MiuixSwitch(checked = enabled, onCheckedChange = onToggleEnabled)
                    }
                }
                Spacer(Modifier.height(18.dp))
                PillButton(
                    text = if (isRecording) {
                        stringResource(R.string.action_stop_recording)
                    } else {
                        stringResource(R.string.action_start_recording)
                    },
                    onClick = onRecordClick,
                    enabled = canRecord || isRecording,
                    filled = !isRecording,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    SurfaceTile(label = label, value = value, modifier = modifier)
}

@Composable
private fun ZoneRow(
    name: String,
    powerUw: Long,
    energyUj: Long,
    fraction: Float,
    percent: Int,
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
                    .background(MiuixTheme.colorScheme.dividerLine),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(10.dp))
                Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        uwToW(powerUw),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${percent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                stringResource(R.string.chart_samples_fmt, refSize),
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
            stringResource(R.string.chart_avg_fmt, name, value),
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
                stringResource(R.string.chart_time_fmt, relativeSeconds),
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

    // niceTicks 的末刻度是步长整数倍且 >= maxValue，用它当轴上界，曲线才不会顶出绘图区
    val leftTicks = niceTicks(maxValue, yTickCount)
    val leftAxisMax = leftTicks.last().coerceAtLeast(1e-6f)
    leftTicks.forEach { value ->
        val y = bottom - (value / leftAxisMax) * chartHeight
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
        val rightTicks = niceTicks(rightMax, rightTickCount)
        val rightAxisMax = rightTicks.last().coerceAtLeast(1e-6f)
        rightTicks.forEach { value ->
            val y = bottom - (value / rightAxisMax) * chartHeight
            drawContext.canvas.nativeCanvas.drawText(rightFormatter(value), right + 6f, y + 8f, textPaint)
        }
    }

    series.forEach { s ->
        if (s.points.size < 2) return@forEach
        val path = Path()
        s.points.forEachIndexed { index, (ts, value) ->
            val x = left + ((ts - minTs).toFloat() / durationMs) * chartWidth
            val y = bottom - (value / leftAxisMax) * chartHeight
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

/**
 * 生成「步长对齐」的刻度，并保证 [niceTicks] 的最后一个刻度 >= [max]。
 *
 * 旧实现用 `while (v <= max)` 收尾，当 max 不是步长整数倍时最后一个刻度会小于 max，
 * 于是坐标轴上界低于真实峰值，最高那根柱子被 clamp 到满高、数值标签压到柱体里。
 * 这里先向上取整到步长倍数，再按该上界铺刻度。
 */
private fun niceTicks(max: Float, targetCount: Int): List<Float> {
    if (max <= 0f) return listOf(0f)
    val slots = (targetCount - 1).coerceAtLeast(1)
    val raw = max / slots
    val mag = 10f.pow(floor(log10(raw.toDouble())).toFloat())
    val step = (ceil(raw / mag) * mag).coerceAtLeast(1e-6f)
    val axisMax = ceil(max / step) * step
    val ticks = ArrayList<Float>(slots + 2)
    var v = 0f
    while (v <= axisMax + step * 1e-3f) {
        ticks += v
        v += step
    }
    return ticks
}

/** 簇名 → 核心区间（按起始核排序），供簇频率与核心占用两个图共享配色逻辑。 */
@Composable
private fun rememberClusterRanges(detail: DetailUiState): List<Pair<String, List<IntRange>>> =
    remember(detail.clusterFreqSamples) {
        detail.clusterFreqSamples.map { it.clusterName }.distinct()
            .mapNotNull { name -> parseCpuRanges(name).takeIf { it.isNotEmpty() }?.let { name to it } }
            .sortedBy { (_, ranges) -> ranges.first().first }
    }

private fun clusterColorAt(palette: List<Color>, index: Int): Color = when (index) {
    0 -> palette[0]
    1 -> palette[2]
    else -> palette[index % palette.size]
}

// ---------- 详情页曲线区块：每个区块独立 composable，顺序由 DetailChartSection 设置驱动 ----------

@Composable
private fun SocChart(
    detail: DetailUiState,
    samples: List<SampleEntity>,
    globalTs: Long?,
    onTsChange: (Long?) -> Unit,
) {
    ZoneCharts(detail, samples.filter { it.zoneName.equals("SOC", true) }, globalTs, onTsChange)
}

@Composable
private fun FpsChart(detail: DetailUiState, globalTs: Long?, onTsChange: (Long?) -> Unit) {
    val palette = LocalChartColors.current.palette
    detail.fpsSamples.takeIf { it.isNotEmpty() }?.let { fps ->
        TimeSeriesChart(
            "FPS",
            listOf(SeriesData("FPS", palette[0], fps.map { it.timestampMs to it.fps })),
            formatter = { v -> "%.1f".format(Locale.US, v) },
            globalTs = globalTs,
            onTsChange = onTsChange,
        )
    }
}

@Composable
private fun DevicePowerChart(detail: DetailUiState, globalTs: Long?, onTsChange: (Long?) -> Unit) {
    val palette = LocalChartColors.current.palette
    detail.devicePowerSamples.takeIf { it.isNotEmpty() }?.let { powers ->
        val label = stringResource(R.string.chart_device_power)
        TimeSeriesChart(
            label,
            listOf(SeriesData(label, palette[0], powers.map { it.timestampMs to it.powerUw / 1_000_000f })),
            formatter = { v -> "%.2f W".format(Locale.US, v) },
            globalTs = globalTs,
            onTsChange = onTsChange,
        )
    }
}

@Composable
private fun ClusterFreqChart(detail: DetailUiState, globalTs: Long?, onTsChange: (Long?) -> Unit) {
    val palette = LocalChartColors.current.palette
    val clusterRanges = rememberClusterRanges(detail)
    detail.clusterFreqSamples.takeIf { it.isNotEmpty() }?.let { freqs ->
        val series = freqs.groupBy { it.clusterName }.entries
            .sortedBy { (name, _) -> clusterRanges.indexOfFirst { it.first == name }.takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .mapIndexed { i, (name, rows) ->
                SeriesData(name, clusterColorAt(palette, i), rows.map { it.timestampMs to it.freqKhz * 1000f })
            }
        TimeSeriesChart(stringResource(R.string.chart_cluster_freq), series, ::formatFreqHz, globalTs = globalTs, onTsChange = onTsChange)
    }
}

@Composable
private fun CoreUsageChart(detail: DetailUiState, globalTs: Long?, onTsChange: (Long?) -> Unit) {
    val palette = LocalChartColors.current.palette
    val clusterRanges = rememberClusterRanges(detail)
    detail.coreUsageSamples.takeIf { it.isNotEmpty() }?.let { cores ->
        val series = cores.groupBy { it.coreIndex }.entries.map { (core, rows) ->
            val clusterIdx = clusterRanges.indexOfFirst { (_, ranges) -> ranges.any { core in it } }
            val color = if (clusterIdx >= 0) clusterColorAt(palette, clusterIdx) else palette[core % palette.size]
            SeriesData("C$core", color, rows.map { it.timestampMs to it.usagePercent })
        }.sortedBy { it.name.removePrefix("C").toIntOrNull() ?: 0 }
        TimeSeriesChart(
            stringResource(R.string.chart_core_usage),
            series,
            { v -> "%.0f%%".format(Locale.US, v) },
            interactive = false,
            fixedMax = 100f,
            yTickCount = 11,
        )
    }
}

@Composable
private fun DdrFreqChart(detail: DetailUiState, globalTs: Long?, onTsChange: (Long?) -> Unit) {
    val palette = LocalChartColors.current.palette
    detail.ddrFreqSamples.takeIf { it.isNotEmpty() }?.let { ddr ->
        TimeSeriesChart(
            stringResource(R.string.chart_ddr_freq),
            listOf(SeriesData("DDR", palette[0], ddr.map { it.timestampMs to it.freqHz.toFloat() })),
            ::formatFreqHz,
            globalTs = globalTs,
            onTsChange = onTsChange,
        )
    }
}

@Composable
private fun ZonePowerSection(detail: DetailUiState, samples: List<SampleEntity>, globalTs: Long?, onTsChange: (Long?) -> Unit) {
    ZoneCharts(detail, samples.filter { !it.zoneName.equals("SOC", true) }, globalTs, onTsChange)
}

/** 按用户设定的区块顺序渲染详情页曲线（无数据的区块自动跳过）。 */
@Composable
private fun DetailCharts(
    detail: DetailUiState,
    order: List<DetailChartSection>,
    samples: List<SampleEntity>,
    globalTs: Long?,
    onTsChange: (Long?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        order.forEach { section ->
            when (section) {
                DetailChartSection.SOC -> SocChart(detail, samples, globalTs, onTsChange)
                DetailChartSection.FPS -> FpsChart(detail, globalTs, onTsChange)
                DetailChartSection.DEVICE_POWER -> DevicePowerChart(detail, globalTs, onTsChange)
                DetailChartSection.CLUSTER_FREQ -> ClusterFreqChart(detail, globalTs, onTsChange)
                DetailChartSection.CORE_USAGE -> CoreUsageChart(detail, globalTs, onTsChange)
                DetailChartSection.GPU -> GpuMergedChart(detail, globalTs, onTsChange)
                DetailChartSection.DDR -> DdrFreqChart(detail, globalTs, onTsChange)
                DetailChartSection.ZONES -> ZonePowerSection(detail, samples, globalTs, onTsChange)
                DetailChartSection.TEMP -> TempChart(detail, globalTs, onTsChange)
            }
        }
    }
}

/** 曲线排序编辑器：上移/下移调整区块顺序，改动即时持久化，对所有记录生效。 */
@Composable
private fun ChartOrderEditor(order: List<DetailChartSection>, onChange: (List<DetailChartSection>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MiuixCard(contentPadding = 0.dp) {
            Column {
                order.forEachIndexed { index, section ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}. ${stringResource(section.labelRes)}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OrderArrowButton("↑", enabled = index > 0) {
                            onChange(order.toMutableList().apply { add(index - 1, removeAt(index)) })
                        }
                        OrderArrowButton("↓", enabled = index < order.lastIndex) {
                            onChange(order.toMutableList().apply { add(index + 1, removeAt(index)) })
                        }
                    }
                    if (index < order.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp)
                                .height(1.dp)
                                .background(MiuixTheme.colorScheme.dividerLine),
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.chart_sort_hint),
            Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrderArrowButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        symbol,
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleMedium,
        color = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        },
    )
}

@Composable
private fun GpuMergedChart(
    detail: DetailUiState,
    globalTs: Long?,
    onTsChange: ((Long?) -> Unit)?,
) {
    val palette = LocalChartColors.current.palette
    detail.gpuSamples.takeIf { it.isNotEmpty() }?.let { gpus ->
        val freq = SeriesData(stringResource(R.string.chart_freq), palette[0], gpus.map { it.timestampMs to it.freqHz.toFloat() })
        val usage = gpus.mapNotNull { it.usagePercent?.let { p -> it.timestampMs to p } }
        if (usage.isNotEmpty()) {
            TimeSeriesChart(
                stringResource(R.string.chart_gpu_freq_and_usage),
                listOf(freq),
                ::formatFreqHz,
                globalTs = globalTs,
                onTsChange = onTsChange,
                rightSeries = SeriesData(stringResource(R.string.chart_usage), palette[1], usage),
                rightFormatter = { v -> "%.0f%%".format(Locale.US, v) },
                rightFixedMax = 100f,
                rightTickCount = 11,
            )
        } else {
            TimeSeriesChart(
                stringResource(R.string.chart_gpu_freq),
                listOf(freq),
                ::formatFreqHz,
                globalTs = globalTs,
                onTsChange = onTsChange,
            )
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
        TimeSeriesChart(stringResource(R.string.chart_temperature), series, formatter = { v -> "%.1f °C".format(Locale.US, v) }, globalTs = globalTs, onTsChange = onTsChange)
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
 *
 * 布局上把画布切成「柱顶数值带 + 绘图区」两段：柱子最高只画到绘图区顶部，
 * 读数永远落在柱顶之上的空白带里。旧实现让柱子顶到画布边缘、标签被
 * `coerceAtLeast` 压进柱体，再用柱身同色绘制，SOC 这种最高柱的读数就「消失」了。
 */
@Composable
private fun LiveBarChart(samples: List<ZoneSample>, palette: List<Color>) {
    val colors = LocalChartColors.current
    val density = LocalDensity.current
    val tickTextPx = with(density) { 9.sp.toPx() }
    val valueTextPx = with(density) { 10.sp.toPx() }
    val zoneTextPx = with(density) { 9.sp.toPx() }

    Canvas(Modifier.fillMaxWidth().height(214.dp)) {
        val left = 42.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = size.height - 28.dp.toPx()

        // 数值带高度跟随字号（系统字体放大时同步变高），保证读数不被裁切
        val labelBand = valueTextPx * 1.5f + 6.dp.toPx()
        val plotTop = top + labelBand
        val chartWidth = (right - left).coerceAtLeast(1f)
        val plotHeight = (bottom - plotTop).coerceAtLeast(1f)

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
            // 用主题前景色而不是柱身色：浅色主题深字、深色主题浅字，任何柱色上都可读
            color = colors.tooltipText.toArgb()
            textSize = valueTextPx
            textAlign = android.graphics.Paint.Align.CENTER
        }

        drawLine(colors.axis, Offset(left, bottom), Offset(right, bottom), strokeWidth = 2f)

        val maxW = (samples.maxOfOrNull { it.effectivePowerUw } ?: 0L)
            .coerceAtLeast(1L) / 1_000_000f
        // niceTicks 保证末刻度 >= maxW，轴上界不会再低于真实峰值
        val ticks = niceTicks(maxW, 5)
        val axisMax = ticks.last().coerceAtLeast(1e-3f)
        ticks.forEach { tick ->
            val y = bottom - (tick / axisMax) * plotHeight
            if (tick > 0f) drawLine(colors.grid, Offset(left, y), Offset(right, y), strokeWidth = 1f)
            val text = when {
                axisMax >= 100f -> "%.0f".format(Locale.US, tick)
                axisMax >= 10f -> "%.1f".format(Locale.US, tick)
                else -> "%.2f".format(Locale.US, tick)
            }
            // 右对齐到轴左侧，避免 "10.0" 这类三位以上刻度压住轴线
            val textWidth = tickPaint.measureText(text)
            drawContext.canvas.nativeCanvas.drawText(
                text,
                (left - 7.dp.toPx() - textWidth).coerceAtLeast(0f),
                y + tickTextPx * 0.36f,
                tickPaint,
            )
        }

        val slot = chartWidth / samples.size
        val barWidth = min(slot * 0.56f, 30.dp.toPx())
        var lastLabelRight = Float.NEGATIVE_INFINITY
        samples.forEachIndexed { index, sample ->
            val valueW = sample.effectivePowerUw / 1_000_000f
            val centerX = left + slot * (index + 0.5f)
            val barHeight = (valueW / axisMax * plotHeight).coerceIn(2.dp.toPx(), plotHeight)
            val color = palette[index % palette.size]

            val barLeft = centerX - barWidth / 2f
            val barTop = bottom - barHeight
            // 只圆顶部两角：柱子从基线（x 轴）长出来，底部必须是直角，
            // 四角全圆会让柱子看起来「浮」在轴上，与柱状图的语义冲突。
            val corner = min(barWidth / 2f, barHeight)
            val bar = Path().apply {
                moveTo(barLeft, bottom)
                lineTo(barLeft, barTop + corner)
                quadraticTo(barLeft, barTop, barLeft + corner, barTop)
                lineTo(barLeft + barWidth - corner, barTop)
                quadraticTo(barLeft + barWidth, barTop, barLeft + barWidth, barTop + corner)
                lineTo(barLeft + barWidth, bottom)
                close()
            }
            drawPath(
                path = bar,
                brush = Brush.verticalGradient(
                    colors = listOf(color, color.copy(alpha = 0.45f)),
                    startY = barTop,
                    endY = bottom,
                ),
            )

            // 读数画在柱顶上方；相邻标签会重叠时跳过，保证每个数字都完整可读
            val valueLabel = when {
                valueW >= 100f -> "%.0f".format(Locale.US, valueW)
                valueW >= 10f -> "%.1f".format(Locale.US, valueW)
                else -> "%.2f".format(Locale.US, valueW)
            }
            val halfTextWidth = valuePaint.measureText(valueLabel) / 2f
            val fitsSlot = slot >= valuePaint.measureText("0.00") + 3.dp.toPx()
            if (fitsSlot && centerX - halfTextWidth > lastLabelRight + 2.dp.toPx()) {
                drawContext.canvas.nativeCanvas.drawText(
                    valueLabel,
                    centerX,
                    bottom - barHeight - 5.dp.toPx(),
                    valuePaint,
                )
                lastLabelRight = centerX + halfTextWidth
            }

            val zoneLabel = fitLabel(sample.zoneName.uppercase(), slot - 2.dp.toPx(), labelPaint)
            drawContext.canvas.nativeCanvas.drawText(zoneLabel, centerX, bottom + 17.dp.toPx(), labelPaint)
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
                    stringResource(R.string.history_count_fmt, records.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        if (records.isEmpty()) {
            item {
                MiuixCard(contentPadding = 0.dp) {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.history_empty_title),
                        description = stringResource(R.string.history_empty_desc),
                    )
                }
            }
        }

        itemsIndexed(records, key = { _, it -> it.id }) { index, record ->
            AnimatedListItem(index = index) {
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
                            "${formatDate(record.startedAtMs)} · ${durationText(record)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill("SOC ${uwToW(record.avgSocPowerUw)}", PillTone.NEUTRAL)
                            StatusPill(
                                stringResource(
                                    R.string.history_fps_fmt,
                                    record.avgFps?.let { "%.1f".format(Locale.US, it) } ?: "--",
                                ),
                                PillTone.NEUTRAL,
                            )
                        }
                    }
                    MiuixIconButton(onClick = { onDelete(record.id) }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
    }
}
}

// ---------- 详情页 ----------

@Composable
private fun DetailPage(
    detail: DetailUiState,
    chartOrder: List<DetailChartSection>,
    onChartOrderChange: (List<DetailChartSection>) -> Unit,
    onDismiss: () -> Unit,
    onExport: (Boolean) -> Unit,
) {
    val visibleZones = remember { mutableStateMapOf<String, Boolean>() }
    // 记录更新会不断产生新的 stats：只补新键，保留用户已做的显隐选择
    LaunchedEffect(detail.stats) {
        detail.stats.forEach { if (!visibleZones.containsKey(it.zoneName)) visibleZones[it.zoneName] = true }
    }
    var crosshairTs by remember { mutableStateOf<Long?>(null) }
    var editingOrder by remember { mutableStateOf(false) }
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.detail_title), style = MaterialTheme.typography.titleLarge)
                        detail.record?.let { record ->
                            Text(
                                "${formatDate(record.startedAtMs)} · ${durationText(record)}",
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
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(stringResource(R.string.detail_section_charts), Modifier.weight(1f))
                            if (detail.hasChartData) {
                                MiuixTextButton(
                                    text = if (editingOrder) {
                                        stringResource(R.string.action_done)
                                    } else {
                                        stringResource(R.string.action_sort)
                                    },
                                    onClick = { editingOrder = !editingOrder },
                                )
                            }
                        }
                    }
                    if (detail.hasChartData) {
                        if (editingOrder) {
                            item { ChartOrderEditor(order = chartOrder, onChange = onChartOrderChange) }
                        }
                        // 顺序全局持久化：本页调整后，其他记录打开也是同一顺序
                        item {
                            DetailCharts(
                                detail = detail,
                                order = chartOrder,
                                samples = detail.samples.filter { visibleZones[it.zoneName] != false },
                                globalTs = crosshairTs,
                                onTsChange = { crosshairTs = it },
                            )
                        }
                    }
                    item { SectionTitle(stringResource(R.string.detail_section_filter)) }
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
                                        stringResource(R.string.detail_zone_avg_fmt, uwToW(stat.avgPowerUw)),
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
                                            .background(MiuixTheme.colorScheme.dividerLine),
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
        SectionTitle(stringResource(R.string.detail_section_avg_power))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 与图表口径一致：SOC 之外按 CPU-L → CPU-M → GPU 的顺序铺开
            detail.stats.sortedBy { zoneDisplayRank(it.zoneName) }.forEach { stat ->
                if (!stat.zoneName.equals("SOC", true)) {
                    SummaryTile(stat.zoneName.uppercase(), uwToW(stat.avgPowerUw))
                }
            }
            if (detail.avgDevicePowerUw > 0) {
                SummaryTile(stringResource(R.string.detail_device), uwToW(detail.avgDevicePowerUw))
            }
        }
    }
}

@Composable
private fun StatGrid(stats: List<ZoneStats>, sampleCount: Int, avgFps: Float) {
    val soc = stats.firstOrNull { it.zoneName.equals("SOC", true) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile(stringResource(R.string.detail_soc_max), uwToW(soc?.maxPowerUw ?: 0), Modifier.weight(1f))
            SummaryTile(stringResource(R.string.detail_soc_avg), uwToW(soc?.avgPowerUw ?: 0), Modifier.weight(1f))
            SummaryTile(stringResource(R.string.detail_soc_min), uwToW(soc?.minPowerUw ?: 0), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile(stringResource(R.string.detail_samples), sampleCount.toString(), Modifier.weight(1f))
            SummaryTile(
                stringResource(R.string.detail_avg_fps),
                if (avgFps > 0f) "%.1f".format(Locale.US, avgFps) else "--",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    SurfaceTile(label = label, value = value, modifier = modifier)
}

@Composable
private fun ZoneCharts(
    detail: DetailUiState,
    samples: List<SampleEntity>,
    globalTs: Long? = null,
    onTsChange: ((Long?) -> Unit)? = null,
) {
    val palette = LocalChartColors.current.palette
    val grouped = samples.groupBy { it.zoneName }
    // 统一按 SOC → CPU-L（大核）→ CPU-M（小核）→ GPU → NSP 排序，与首页、OSD 一致。
    // GPU 合并图不再楔在这里 —— 详情页已把它排在 GPU 频率/占用专属位置。
    val ordered = grouped.entries.sortedWith(compareBy({ zoneDisplayRank(it.key) }, { it.key }))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ordered.forEachIndexed { index, entry ->
            ZonePowerChart(
                zoneName = entry.key,
                samples = entry.value,
                lineColor = palette[index % palette.size],
                globalTs = globalTs,
                onTsChange = onTsChange,
            )
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
                stringResource(R.string.chart_samples_total_fmt, samples.size),
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

    val ticks = niceTicks(maxPower.toFloat(), yTickCount)
    val axisMaxPower = ticks.last().coerceAtLeast(1e-6f)
    ticks.forEach { value ->
        val y = bottom - (value / axisMaxPower) * chartHeight
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
        val y = bottom - (sample.effectivePowerUw().toFloat() / axisMaxPower) * chartHeight
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

    selectedIndex?.let { index ->
        val sample = samples.getOrNull(index) ?: return@let
        val x = left + ((sample.timestampMs - minTs).toFloat() / durationMs) * chartWidth
        val y = bottom - (sample.effectivePowerUw().toFloat() / axisMaxPower) * chartHeight
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
@Composable
private fun durationText(record: RecordEntity): String {
    val end = record.endedAtMs ?: System.currentTimeMillis()
    val seconds = ((end - record.startedAtMs) / 1000).coerceAtLeast(0)
    return stringResource(R.string.duration_fmt, seconds / 60, seconds % 60)
}

/** 应用内展示的项目主页。 */
