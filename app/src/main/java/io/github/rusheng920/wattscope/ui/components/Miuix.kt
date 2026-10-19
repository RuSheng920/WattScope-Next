package io.github.rusheng920.wattscope.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rusheng920.wattscope.domain.GlassIntensity
import io.github.rusheng920.wattscope.ui.theme.LocalStatusColors
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 项目内对官方 MIUIX 组件的薄封装。
 *
 * 目的只有一个：把「样式参数」收敛到一处，页面代码只描述结构。
 * 所有视觉（圆角、配色、按压反馈、TabRow 指示器、导航栏字体）都来自
 * `top.yukonga.miuix.kmp`，不再自绘仿制。
 */

/** MIUIX 卡片：16dp 圆角、surfaceContainer 底色、可选点击反馈。 */
@Composable
fun MiuixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.defaultColors(
        color = containerColor ?: MiuixTheme.colorScheme.surfaceContainer,
    )
    val inside = PaddingValues(contentPadding)
    if (onClick != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = inside,
            colors = colors,
            pressFeedbackType = PressFeedbackType.Sink,
            onClick = onClick,
            content = content,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = inside,
            colors = colors,
            content = content,
        )
    }
}

/** MIUIX 分组标题（SmallTitle）。页面已有 16dp 边距，这里覆盖默认内边距避免双重缩进。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    SmallTitle(
        text = text,
        modifier = modifier,
        insideMargin = PaddingValues(start = 2.dp, top = 10.dp, end = 2.dp, bottom = 4.dp),
    )
}

/**
 * 设置/权限行：MIUIX BasicComponent。
 * 分隔线用 MIUIX HorizontalDivider，左缩进对齐标题，和 HyperOS 设置页一致。
 */
@Composable
fun ActionRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MiuixTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp, end = 16.dp),
                color = MiuixTheme.colorScheme.dividerLine,
            )
        }
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            title = title,
            summary = subtitle,
            startAction = icon?.let { { IconBadge(it, iconTint) } },
            endActions = trailing?.let { { it() } },
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            onClick = onClick,
        )
    }
}

/** 36dp 圆角图标徽标，底色为主色 12% 透明。 */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = MiuixTheme.colorScheme.primary, size: Dp = 36.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** MIUIX 开关。 */
@Composable
fun MiuixSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
}

/** MIUIX 分段控件（TabRow）。 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        tabs = options,
        selectedTabIndex = selectedIndex,
        onTabSelected = onSelect,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 主按钮用 MIUIX Button；次要操作用 MIUIX TextButton。 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    horizontalPadding: Dp = 20.dp,
) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            cornerRadius = height / 2,
            minWidth = 56.dp,
            minHeight = height,
            insideMargin = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    } else {
        TextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            minWidth = 48.dp,
            minHeight = height,
            insideMargin = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
        )
    }
}

enum class PillTone { OK, WARN, ERROR, NEUTRAL }

/** 状态胶囊：语义色来自主题扩展（MIUIX 无内置成功/警告色）。 */
@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    val (bg, fg) = when (tone) {
        PillTone.OK -> colors.okBg to colors.okFg
        PillTone.WARN -> colors.warnBg to colors.warnFg
        PillTone.ERROR -> colors.errorBg to colors.errorFg
        PillTone.NEUTRAL -> colors.neutralBg to colors.neutralFg
    }
    Box(
        modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Medium)
    }
}

/** 底部导航项。 */
data class BottomItem(val label: String, val icon: ImageVector)

/** MIUIX 底部导航栏（自带系统栏 inset 与分隔线）。 */
@Composable
fun MiuixBottomBar(
    items: List<BottomItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selected,
                onClick = { onSelect(index) },
                icon = item.icon,
                label = item.label,
            )
        }
    }
}

/**
 * 悬浮液态玻璃底栏。
 *
 * 以 MIUIX [FloatingNavigationBar] 为骨架（HyperOS 原生悬浮 Dock），
 * 背景换成自绘的「底色 + 斜向高光 + 亮边」三层玻璃：
 * 底色由 [intensity] 控制不透明度（三档），高光只加一层静态渐变，
 * 不做实时高斯模糊 —— 底栏常驻，逐帧模糊会让 GPU 持续工作，污染功耗测量。
 *
 * @param dark 深色主题用近黑玻璃 + 白色高光，浅色主题用白玻璃 + 淡淡投影
 */
@Composable
fun FloatingGlassBottomBar(
    items: List<BottomItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    intensity: GlassIntensity,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (dark) {
        Color(0xFF0E1518).copy(alpha = intensity.tintAlpha)
    } else {
        Color(0xFFF8FBFF).copy(alpha = intensity.tintAlpha)
    }
    val highlight = when (intensity) {
        GlassIntensity.LIGHT -> 0.10f
        GlassIntensity.STANDARD -> 0.16f
        GlassIntensity.STRONG -> 0.24f
    }
    // 浅色玻璃用冷色高光 + 灰蓝亮边，纯白在浅色页面上会完全看不见边界
    val sheen = if (dark) Color.White else Color(0xFFCFE3FF)
    val border = if (dark) Color.White.copy(alpha = 0.20f) else Color(0xFFD5E1F0).copy(alpha = 0.95f)
    val radius = 26.dp

    FloatingNavigationBar(
        modifier = modifier.drawBehind {
            val r = radius.toPx()
            // 斜向高光：模拟玻璃厚度的光程差，左上亮、右下暗
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        sheen.copy(alpha = highlight),
                        Color.Transparent,
                        sheen.copy(alpha = highlight * 0.35f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                size = size,
            )
            // 1dp 亮边：任意壁纸/内容上都保持边界清晰
            val inset = 0.5.dp.toPx()
            drawRoundRect(
                color = border,
                topLeft = Offset(inset, inset),
                size = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(r - inset, r - inset),
                style = Stroke(width = 1.dp.toPx()),
            )
        },
        color = tint,
        cornerRadius = radius,
        horizontalOutSidePadding = 20.dp,
        shadowElevation = 10.dp,
        mode = FloatingNavigationBarDisplayMode.IconAndText,
    ) {
        items.forEachIndexed { index, item ->
            FloatingNavigationBarItem(
                selected = index == selected,
                onClick = { onSelect(index) },
                icon = item.icon,
                label = item.label,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
