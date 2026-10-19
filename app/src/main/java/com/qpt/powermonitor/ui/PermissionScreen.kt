package com.qpt.powermonitor.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qpt.powermonitor.domain.AppPermissions
import com.qpt.powermonitor.ui.components.ActionRow
import com.qpt.powermonitor.ui.components.MiuixCard
import com.qpt.powermonitor.ui.components.PillButton
import com.qpt.powermonitor.ui.components.PillTone
import com.qpt.powermonitor.ui.components.SectionTitle
import com.qpt.powermonitor.ui.components.StatusPill

/**
 * 权限中心：集中展示 root、悬浮窗、通知等授权状态，并提供一键跳转授权。
 *
 * 设计考虑：
 * - 核心权限（root/悬浮窗/通知）直接影响功能可用性，置顶并给出完成度；
 * - 每行状态与操作分离：胶囊说明「现在能不能用」，按钮说明「怎么修」；
 * - 所有检测在 repository 侧做（sysfs/系统 API），本页只负责呈现，便于 onResume 刷新。
 */
@Composable
fun PermissionScreen(
    permissions: AppPermissions,
    onRequestRoot: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestUsage: () -> Unit,
    onOpenAutostart: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Text(
                "首次使用请完成以下授权，WattScope 才能读取功耗并常驻后台",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
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
                    actionText = if (permissions.root == true) null else "申请授权",
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
                    "授权后返回本页会自动刷新状态；若状态未更新，点右上角「刷新」。",
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
                        filled = tone != PillTone.OK,
                        height = 32.dp,
                        horizontalPadding = 13.dp,
                    )
                }
            }
        },
    )
}
