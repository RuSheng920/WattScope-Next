package io.github.rusheng920.wattscope.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rusheng920.wattscope.R
import io.github.rusheng920.wattscope.domain.AppPermissions
import io.github.rusheng920.wattscope.ui.components.CircularProgress
import io.github.rusheng920.wattscope.ui.components.IconBadge
import io.github.rusheng920.wattscope.ui.components.MiuixCard
import io.github.rusheng920.wattscope.ui.components.PillButton
import io.github.rusheng920.wattscope.ui.components.PillTone
import io.github.rusheng920.wattscope.ui.components.SectionTitle
import io.github.rusheng920.wattscope.ui.components.StatusPill
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 权限中心内容：根/悬浮窗/通知等授权状态与跳转。
 *
 * - 所有状态检测在 repository 侧完成，本页只呈现；进入页面后由上层每 1.5s 刷新一次，
 *   用户在 KernelSU / 系统设置里改完授权，返回即可看到变化，无需重启应用。
 * - [intro] 为 null 时不显示顶部说明（首次引导页有自己的介绍）。
 */
@Composable
fun PermissionCenter(
    permissions: AppPermissions,
    onRequestRoot: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestUsage: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onOpenAutostart: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 24.dp,
    intro: String? = stringResource(R.string.perm_intro),
) {
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
    ) {
        if (intro != null) {
            item {
                Text(
                    intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        item { PermissionProgressCard(permissions) }

        item { SectionTitle(stringResource(R.string.perm_section_core)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                PermissionRow(
                    title = stringResource(R.string.perm_root),
                    subtitle = when (permissions.root) {
                        true -> stringResource(R.string.perm_root_granted)
                        false -> stringResource(R.string.perm_root_denied)
                        null -> stringResource(R.string.perm_root_unknown)
                    },
                    icon = Icons.Rounded.Security,
                    iconTint = MaterialTheme.colorScheme.primary,
                    tone = when (permissions.root) {
                        true -> PillTone.OK
                        false -> PillTone.ERROR
                        null -> PillTone.NEUTRAL
                    },
                    statusText = when (permissions.root) {
                        true -> stringResource(R.string.state_granted)
                        false -> stringResource(R.string.state_not_granted)
                        null -> stringResource(R.string.state_undetected)
                    },
                    actionText = if (permissions.root == true) {
                        stringResource(R.string.action_recheck)
                    } else {
                        stringResource(R.string.action_request)
                    },
                    actionFilled = permissions.root != true,
                    onAction = onRequestRoot,
                )
                PermissionRow(
                    title = stringResource(R.string.perm_overlay),
                    subtitle = stringResource(R.string.perm_overlay_desc),
                    icon = Icons.Rounded.Layers,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    tone = if (permissions.overlay) PillTone.OK else PillTone.WARN,
                    statusText = if (permissions.overlay) {
                        stringResource(R.string.state_granted)
                    } else {
                        stringResource(R.string.state_not_granted)
                    },
                    actionText = if (permissions.overlay) null else stringResource(R.string.action_grant),
                    onAction = onRequestOverlay,
                    showDivider = true,
                )
                PermissionRow(
                    title = stringResource(R.string.perm_notification),
                    subtitle = stringResource(R.string.perm_notification_desc),
                    icon = Icons.Rounded.Notifications,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    tone = if (permissions.notifications) PillTone.OK else PillTone.WARN,
                    statusText = if (permissions.notifications) {
                        stringResource(R.string.state_on)
                    } else {
                        stringResource(R.string.state_off)
                    },
                    actionText = if (permissions.notifications) null else stringResource(R.string.action_turn_on),
                    onAction = onRequestNotifications,
                    showDivider = true,
                )
            }
        }

        item { SectionTitle(stringResource(R.string.perm_section_background)) }
        item {
            MiuixCard(contentPadding = 0.dp) {
                PermissionRow(
                    title = stringResource(R.string.perm_battery),
                    subtitle = stringResource(R.string.perm_battery_desc),
                    icon = Icons.Rounded.BatteryChargingFull,
                    iconTint = MaterialTheme.colorScheme.primary,
                    tone = if (permissions.batteryUnrestricted) PillTone.OK else PillTone.WARN,
                    statusText = if (permissions.batteryUnrestricted) {
                        stringResource(R.string.state_added)
                    } else {
                        stringResource(R.string.state_restricted)
                    },
                    actionText = if (permissions.batteryUnrestricted) null else stringResource(R.string.action_open_settings),
                    onAction = onRequestBattery,
                    showDivider = true,
                )
                // 无障碍宿主：不是「必需」，但它是后台/游戏里点击能响应的唯一稳定路径
                PermissionRow(
                    title = stringResource(R.string.perm_accessibility),
                    subtitle = stringResource(R.string.perm_accessibility_desc),
                    icon = Icons.Rounded.Accessibility,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    tone = if (permissions.accessibility) PillTone.OK else PillTone.NEUTRAL,
                    statusText = if (permissions.accessibility) {
                        stringResource(R.string.state_on)
                    } else {
                        stringResource(R.string.state_suggest_on)
                    },
                    actionText = if (permissions.accessibility) null else stringResource(R.string.action_open_settings),
                    onAction = onRequestAccessibility,
                    showDivider = true,
                )
                PermissionRow(
                    title = stringResource(R.string.perm_usage),
                    subtitle = stringResource(R.string.perm_usage_desc),
                    icon = Icons.Rounded.Insights,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    tone = if (permissions.usageAccess) PillTone.OK else PillTone.NEUTRAL,
                    statusText = if (permissions.usageAccess) {
                        stringResource(R.string.state_allowed)
                    } else {
                        stringResource(R.string.state_optional)
                    },
                    actionText = if (permissions.usageAccess) null else stringResource(R.string.action_grant),
                    onAction = onRequestUsage,
                    showDivider = true,
                )
                if (permissions.isMiui) {
                    PermissionRow(
                        title = stringResource(R.string.perm_autostart),
                        subtitle = stringResource(R.string.perm_autostart_desc),
                        icon = Icons.Rounded.RestartAlt,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        tone = PillTone.NEUTRAL,
                        statusText = stringResource(R.string.state_suggest_on),
                        actionText = stringResource(R.string.action_open_settings),
                        onAction = onOpenAutostart,
                        showDivider = true,
                    )
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.perm_auto_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionTitle(stringResource(R.string.perm_section_first_run)) }
        item {
            MiuixCard(contentPadding = 16.dp) {
                StepRow(
                    1,
                    stringResource(R.string.perm_step1_title),
                    stringResource(R.string.perm_step1_desc),
                    isLast = false,
                )
                Spacer(Modifier.height(8.dp))
                StepRow(
                    2,
                    stringResource(R.string.perm_step2_title),
                    stringResource(R.string.perm_step2_desc),
                    isLast = false,
                )
                Spacer(Modifier.height(8.dp))
                StepRow(
                    3,
                    stringResource(R.string.perm_step3_title),
                    stringResource(R.string.perm_step3_desc),
                    isLast = true,
                )
            }
        }
    }
}

/**
 * 权限中心二级页：从设置进入，独立于底部 Tab。
 * 预测性返回与详情页一致：按住返回键时页面跟随手势右移缩放。
 */
@Composable
fun PermissionPage(
    permissions: AppPermissions,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRequestRoot: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestUsage: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onOpenAutostart: () -> Unit,
) {
    var backProgress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { backProgress = it.progress }
        } finally {
            backProgress = 0f
        }
        onDismiss()
    }

    val scrimAlpha by animateFloatAsState(1f - backProgress * 0.35f, label = "permissionScrim")

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f * scrimAlpha))) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = size.width * 0.16f * backProgress
                    scaleX = 1f - 0.05f * backProgress
                    scaleY = 1f - 0.05f * backProgress
                }
                .clip(RoundedCornerShape(if (backProgress > 0f) 24.dp else 0.dp))
                .background(MiuixTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixIconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(
                                R.string.settings_perm_ready_fmt,
                                permissions.requiredGranted(),
                                permissions.requiredTotal(),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MiuixIconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                PermissionCenter(
                    permissions = permissions,
                    onRequestRoot = onRequestRoot,
                    onRequestOverlay = onRequestOverlay,
                    onRequestNotifications = onRequestNotifications,
                    onRequestBattery = onRequestBattery,
                    onRequestUsage = onRequestUsage,
                    onRequestAccessibility = onRequestAccessibility,
                    onOpenAutostart = onOpenAutostart,
                    bottomPadding = 28.dp,
                )
            }
        }
    }
}

/**
 * 首次安装引导页：应用启动后先到这里，而不是直接跳系统悬浮窗授权。
 * 用户可逐项授权，也可「稍后设置」直接进入应用。
 */
@Composable
fun OnboardingScreen(
    permissions: AppPermissions,
    onRequestRoot: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestUsage: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onOpenAutostart: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
) {
    // 引导页底色与仪表盘 pageBrush 保持一致（顶栏/首屏无接缝）；
    // 纯黑在引导页上过于单调，带一点纵向冷暖过渡后，权限卡片的 surfaceContainer 才有「浮起」的参照
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBrush = remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF05080D), Color(0xFF0A1119)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFF7F7F7), Color(0xFFF5F7FB), Color(0xFFEEF3FA)))
        }
    }
    Box(Modifier.fillMaxSize().background(pageBrush)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text("WattScope Next", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.onboarding_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            PermissionCenter(
                permissions = permissions,
                onRequestRoot = onRequestRoot,
                onRequestOverlay = onRequestOverlay,
                onRequestNotifications = onRequestNotifications,
                onRequestBattery = onRequestBattery,
                onRequestUsage = onRequestUsage,
                onRequestAccessibility = onRequestAccessibility,
                onOpenAutostart = onOpenAutostart,
                modifier = Modifier.weight(1f),
                bottomPadding = 8.dp,
                intro = null,
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 两个按钮按权重分配宽度：窄屏或大字体下也不会互相挤压成畸形
                MiuixTextButton(
                    text = stringResource(R.string.action_later),
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = stringResource(R.string.action_finish),
                    onClick = onFinish,
                    modifier = Modifier.weight(1.7f),
                    horizontalPadding = 12.dp,
                )
            }
        }
    }
}

/** 步骤行：竖向时间线 + 圆形序号 + 标题/说明。 */
@Composable
private fun StepRow(index: Int, title: String, description: String, isLast: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 顶部完成度卡片：核心三项授权进度的环形指示器。 */
@Composable
private fun PermissionProgressCard(permissions: AppPermissions) {
    val granted = permissions.requiredGranted()
    val total = permissions.requiredTotal()
    val ready = granted >= total
    val fraction = granted.toFloat() / total.coerceAtLeast(1)
    MiuixCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgress(fraction = fraction)
                Text(
                    "$granted/$total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (ready) {
                        stringResource(R.string.perm_progress_ready)
                    } else {
                        stringResource(R.string.perm_progress_left_fmt, total - granted)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (ready) {
                        stringResource(R.string.perm_progress_ready_desc)
                    } else {
                        stringResource(R.string.perm_progress_left_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


/**
 * 单条权限行：图标 + 标题/副标题 + 状态胶囊 + 操作按钮。
 *
 * 这里刻意不再复用 `ActionRow`（MIUIX BasicComponent）：它把 endActions 的宽度
 * 按行宽 30% 分配，状态胶囊和按钮一起塞进去必然被压缩 —— 按钮被压扁成
 * 「圆角矩形」、文字被裁断，正是之前 DPI / 字体缩放下的变形来源。
 *
 * 现在按钮是 Row 的固定尺寸子项（完全按自身内容测量，不参与权重分配），
 * 文本列用 `weight(1f)` 让出空间；极窄屏时状态胶囊下移到副标题下，标题不被挤掉。
 */
@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    tone: PillTone,
    statusText: String,
    actionText: String?,
    onAction: () -> Unit,
    actionFilled: Boolean = tone != PillTone.OK,
    showDivider: Boolean = false,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 行宽不足 350dp（约 360dp 屏幕扣掉页边距，或大字体）时把胶囊换行，
        // 保证标题与按钮都拿到足够宽度，而不是互相挤压
        val compact = maxWidth < 350.dp
        Column(Modifier.fillMaxWidth()) {
            if (showDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 60.dp, end = 16.dp),
                    color = MiuixTheme.colorScheme.dividerLine,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon, iconTint)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!compact) {
                            Spacer(Modifier.width(6.dp))
                            StatusPill(statusText, tone)
                        }
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (compact) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (compact) {
                        Spacer(Modifier.height(2.dp))
                        StatusPill(statusText, tone)
                    }
                }
                if (actionText != null) {
                    Spacer(Modifier.width(10.dp))
                    PillButton(
                        text = actionText,
                        onClick = onAction,
                        filled = actionFilled,
                        height = 34.dp,
                        horizontalPadding = 12.dp,
                        modifier = Modifier.widthIn(min = 76.dp),
                    )
                }
            }
        }
    }
}
