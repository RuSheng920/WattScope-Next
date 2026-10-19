package io.github.rusheng920.wattscope.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rusheng920.wattscope.domain.AppPermissions
import io.github.rusheng920.wattscope.ui.components.ActionRow
import io.github.rusheng920.wattscope.ui.components.MiuixCard
import io.github.rusheng920.wattscope.ui.components.PillButton
import io.github.rusheng920.wattscope.ui.components.PillTone
import io.github.rusheng920.wattscope.ui.components.SectionTitle
import io.github.rusheng920.wattscope.ui.components.StatusPill
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
    onOpenAutostart: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 24.dp,
    intro: String? = "授权状态会实时刷新；未授予的权限可随时在这里补齐。",
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

        item { SectionTitle("核心权限") }
        item {
            MiuixCard(contentPadding = 0.dp) {
                PermissionRow(
                    title = "Root 权限",
                    subtitle = when (permissions.root) {
                        true -> "已获得超级用户授权，可以读取 QPT 功耗节点"
                        false -> "未获得授权：功耗采样不可用，请在 KernelSU / Magisk 中允许本应用"
                        null -> "尚未检测到授权状态，点击右侧按钮发起申请"
                    },
                    icon = Icons.Rounded.Security,
                    iconTint = MaterialTheme.colorScheme.primary,
                    tone = when (permissions.root) {
                        true -> PillTone.OK
                        false -> PillTone.ERROR
                        null -> PillTone.NEUTRAL
                    },
                    statusText = when (permissions.root) {
                        true -> "已授予"
                        false -> "未授予"
                        null -> "未检测"
                    },
                    actionText = if (permissions.root == true) "重新检测" else "申请授权",
                    actionFilled = permissions.root != true,
                    onAction = onRequestRoot,
                )
                PermissionRow(
                    title = "悬浮窗权限",
                    subtitle = "悬浮球与 OSD 实时功耗浮层需要显示在其他应用上层",
                    icon = Icons.Rounded.Layers,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    tone = if (permissions.overlay) PillTone.OK else PillTone.WARN,
                    statusText = if (permissions.overlay) "已授予" else "未授予",
                    actionText = if (permissions.overlay) null else "去授权",
                    onAction = onRequestOverlay,
                    showDivider = true,
                )
                PermissionRow(
                    title = "通知权限",
                    subtitle = "前台服务需要常驻通知，用于保活与快捷控制",
                    icon = Icons.Rounded.Notifications,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    tone = if (permissions.notifications) PillTone.OK else PillTone.WARN,
                    statusText = if (permissions.notifications) "已开启" else "已关闭",
                    actionText = if (permissions.notifications) null else "去开启",
                    onAction = onRequestNotifications,
                    showDivider = true,
                )
            }
        }

        item { SectionTitle("后台与识别") }
        item {
            MiuixCard(contentPadding = 0.dp) {
                PermissionRow(
                    title = "电池优化白名单",
                    subtitle = "避免系统在息屏后冻结采样与记录",
                    icon = Icons.Rounded.BatteryChargingFull,
                    iconTint = MaterialTheme.colorScheme.primary,
                    tone = if (permissions.batteryUnrestricted) PillTone.OK else PillTone.WARN,
                    statusText = if (permissions.batteryUnrestricted) "已加入" else "受限制",
                    actionText = if (permissions.batteryUnrestricted) null else "去设置",
                    onAction = onRequestBattery,
                )
                PermissionRow(
                    title = "使用情况访问",
                    subtitle = "用于识别前台应用：FPS 跟随与「退出应用自动停止记录」依赖它",
                    icon = Icons.Rounded.Insights,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    tone = if (permissions.usageAccess) PillTone.OK else PillTone.NEUTRAL,
                    statusText = if (permissions.usageAccess) "已允许" else "可选",
                    actionText = if (permissions.usageAccess) null else "去授权",
                    onAction = onRequestUsage,
                    showDivider = true,
                )
                if (permissions.isMiui) {
                    PermissionRow(
                        title = "自启动管理",
                        subtitle = "MIUI/HyperOS 会清理后台进程，建议允许自启动",
                        icon = Icons.Rounded.RestartAlt,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        tone = PillTone.NEUTRAL,
                        statusText = "建议开启",
                        actionText = "去设置",
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
                    "本页每 1.5 秒自动检测一次授权状态；Root 授权变化会在授权完成后立即生效，无需重启应用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionTitle("首次使用") }
        item {
            MiuixCard(contentPadding = 16.dp) {
                StepRow(1, "授予 Root 权限", "在 KernelSU / Magisk 弹窗中允许本应用")
                Spacer(Modifier.height(12.dp))
                StepRow(2, "开启悬浮窗与通知", "悬浮球、OSD 浮层和前台服务依赖这两项")
                Spacer(Modifier.height(12.dp))
                StepRow(3, "回到仪表盘开始记录", "也可以单击悬浮球，退出被记录应用时自动停止")
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("权限中心", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${permissions.requiredGranted()} / ${permissions.requiredTotal()} 项核心权限已就绪",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MiuixIconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "刷新",
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
    onOpenAutostart: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("WattScope Next", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "高通 QPT 实时功耗遥测。开始前先完成必要的授权，之后可在「设置 → 权限中心」随时调整。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PermissionCenter(
                permissions = permissions,
                onRequestRoot = onRequestRoot,
                onRequestOverlay = onRequestOverlay,
                onRequestNotifications = onRequestNotifications,
                onRequestBattery = onRequestBattery,
                onRequestUsage = onRequestUsage,
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
                MiuixTextButton(text = "稍后设置", onClick = onSkip)
                Spacer(Modifier.weight(1f))
                PillButton(text = "完成，进入应用", onClick = onFinish)
            }
        }
    }
}

/** 步骤行：圆形序号 + 标题/说明。 */
@Composable
private fun StepRow(index: Int, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
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
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 顶部完成度卡片：核心三项授权进度。 */
@Composable
private fun PermissionProgressCard(permissions: AppPermissions) {
    val granted = permissions.requiredGranted()
    val total = permissions.requiredTotal()
    val ready = granted >= total
    MiuixCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (ready) "权限已就绪" else "还需完成 ${total - granted} 项授权",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (ready) "所有核心功能均可正常使用" else "核心权限：Root、悬浮窗、通知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                "$granted / $total",
                if (ready) PillTone.OK else PillTone.WARN,
            )
        }
        Spacer(Modifier.height(14.dp))
        ProgressBar(fraction = granted.toFloat() / total)
    }
}

/** 细进度条（6dp），用于权限完成度。 */
@Composable
fun ProgressBar(fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

/** 单条权限行：状态胶囊 + 操作按钮，未授予时按钮高亮。 */
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
    ActionRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        showDivider = showDivider,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(statusText, tone)
                if (actionText != null) {
                    Spacer(Modifier.width(8.dp))
                    PillButton(
                        text = actionText,
                        onClick = onAction,
                        filled = actionFilled,
                        height = 32.dp,
                        horizontalPadding = 13.dp,
                    )
                }
            }
        },
    )
}
