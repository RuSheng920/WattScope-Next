package io.github.rusheng920.wattscope.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import io.github.rusheng920.wattscope.domain.GlassIntensity
import io.github.rusheng920.wattscope.ui.theme.LocalStatusColors
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
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
    // 卡片内的 Material Text 若没显式给 color，会落到 LocalContentColor 的默认值（黑）。
    // 深色卡片上黑字几乎读不出来 —— 实测「语言 / Language」标题在 #242424 上只剩 1.1:1。
    // 在这里统一注入 MIUIX 的 onSurface，让「放进卡片就是正确颜色」成为组件的默认行为，
    // 而不是指望每个调用点自己记得补 color。
    CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurface) {
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

/**
 * 36dp 圆角图标徽标，底色为主色半透明。
 * 暗色下主色 12% 透明在深色卡片上过于微弱，提升到 18% 保证图标容器可辨识。
 */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = MiuixTheme.colorScheme.primary, size: Dp = 36.dp) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = if (isDark) 0.18f else 0.12f)),
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

/**
 * MIUIX 分段控件（TabRow）。
 *
 * MIUIX 默认样式有两个问题：
 * 1. 容器背景是「直角矩形」且用 `surface`（暗色即纯黑），套在 #242424 的卡片里
 *    就成了「黑色方块里放圆角胶囊」，非常突兀 —— 这里外层 clip 一层与卡片协调的圆角；
 * 2. 容器底色 surface 与选中胶囊 surfaceContainer 在暗色下层次不清 ——
 *    换成 surfaceContainerHigh 作底、surface 作选中胶囊（暗色下反色，HyperOS 风格）。
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MiuixTheme.colorScheme
    TabRow(
        tabs = options,
        selectedTabIndex = selectedIndex,
        onTabSelected = onSelect,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(21.dp)),
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = cs.surfaceContainerHigh,
            contentColor = cs.onSurfaceVariantSummary,
            selectedBackgroundColor = cs.surface,
            selectedContentColor = cs.onBackground,
        ),
    )
}

/**
 * 主按钮 / 次要按钮统一走 MIUIX Button。
 *
 * 之前 unfilled 态用 MIUIX TextButton，它内部用 MIUIX 自己的文字样式渲染 text，
 * 与 filled 态自绘的 `labelLarge` 文字大小不一致 —— 「开始记录 / 停止记录」切换时
 * 文字会跳一下。现在两个态都用 Button + 同一段 Text，仅颜色不同。
 *
 * 圆角刻意给一个远大于按钮高度的值：`RoundedCornerShape` 会按实际高度把半径
 * clamp 到 min(w,h)/2，因此无论系统字体放大到多少、按钮被撑高到什么尺寸，
 * 轮廓始终是完整药丸，不会退化成「圆角矩形」。
 * 文本强制单行，宽度不足时裁切而不是换行 —— 换行会把按钮撑成两倍高。
 */
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
    val pillRadius = 1000.dp
    val cs = MiuixTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = pillRadius,
        minWidth = 56.dp,
        minHeight = height,
        colors = if (filled) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.buttonColors(
                color = Color.Transparent,
                disabledColor = Color.Transparent,
                contentColor = cs.primary,
                disabledContentColor = cs.primary.copy(alpha = 0.35f),
            )
        },
        insideMargin = PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
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
 * 悬浮液态玻璃底栏（社区 Backdrop 实现）。
 *
 * 背景不是自绘渐变，而是 [com.kyant.backdrop] 的 `drawBackdrop`：
 * 从 [backdrop] 采样真实屏幕内容 → 提纯饱和度 → 轻微高斯模糊 → `lens` 边缘折射
 * （AGSL RuntimeShader，含色散）→ 高光 + 内阴影，再叠一层很薄的表面着色。
 * 参考实现：Kyant0/AndroidLiquidGlass 的 LiquidBottomTabs / LiquidButton。
 *
 * **通透度的关键不是「多模糊」，而是「少刷白」**：
 * 参考实现用 2–8dp 模糊、5%–18% 着色，靠折射与高光表现玻璃厚度；
 * 之前 6–17dp 模糊叠加 26%–56% 白色着色，透光被吃掉，观感就成了磨砂亚克力。
 * 这里把着色压到 5%–18%，并用 `colorControls` 把背景饱和度提到 1.7，
 * 玻璃后的内容仍然「有颜色地」透出来，而不是一层灰雾。
 *
 * [live] = false 时退化为静态玻璃（零 GPU 模糊）：本应用用于测量功耗，
 * 记录期间必须避免自身 UI 的逐帧模糊污染数据；静态版只补很薄的一层底，
 * 因为应用内容本身与主题一致（浅色页白卡、深色页黑卡），文字不依赖底色对比。
 *
 * @param backdrop 由内容层注册的 [LayerBackdrop]；为 null 时同样走静态降级
 * @param dark 深色主题用近黑冷调玻璃，浅色主题用带蓝调的白玻璃
 */
@Composable
fun FloatingGlassBottomBar(
    items: List<BottomItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    intensity: GlassIntensity,
    dark: Boolean,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val radius = 28.dp
    val shape = RoundedCornerShape(radius)

    // 表面着色：真实玻璃的自着色很淡，只留够文字可读的一点点底
    val surface = if (dark) Color(0xFF0B0F13) else Color(0xFFF8FBFF)
    val surfaceAlpha = intensity.tintAlpha.coerceIn(0f, 0.9f)
    // 玻璃厚度的斜向高光：只保留极淡一层，否则整块会发灰
    val sheen = if (dark) Color.White else Color(0xFFDCE9FB)
    val sheenAlpha = when (intensity) {
        GlassIntensity.LIGHT -> 0.05f
        GlassIntensity.STANDARD -> 0.07f
        GlassIntensity.STRONG -> 0.10f
    }
    // 浅色主题用高亮白边模拟玻璃切面；深色主题用低透明白边
    val border = if (dark) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.92f)

    // 整个玻璃 modifier 用 remember 固定：effects / highlight / shadow 都是 lambda，
    // 每次重组新建会让 DrawBackdropNode 认为参数变化而重建 RenderEffect。
    // 主页每秒都会刷新采样数据，不缓存会造成每秒多次重建 shader。
    val glassModifier = remember(modifier, backdrop, intensity, dark, live) {
        if (backdrop != null && live) {
            modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    // 先提纯再模糊：玻璃后的内容保持饱和，才不会被「洗成灰雾」
                    colorControls(brightness = 0.02f, contrast = 1.03f, saturation = 1.7f)
                    blur(intensity.blurRadiusDp.dp.toPx())
                    lens(
                        refractionHeight = intensity.refractionHeightDp.dp.toPx(),
                        refractionAmount = intensity.refractionAmountDp.dp.toPx(),
                        chromaticAberration = true,
                    )
                },
                // 默认高光是沿整条边缘的柔和环境光，比「画一根 0.8dp 白线」更像玻璃切面
                highlight = { Highlight.Default },
                shadow = {
                    Shadow(
                        radius = 20.dp,
                        offset = DpOffset(0.dp, 9.dp),
                        color = Color.Black.copy(alpha = if (dark) 0.55f else 0.14f),
                    )
                },
                // 内阴影让玻璃看起来有厚度，而不是一张贴纸
                innerShadow = {
                    InnerShadow(
                        radius = 7.dp,
                        offset = DpOffset(0.dp, 1.dp),
                        alpha = if (dark) 0.55f else 0.20f,
                    )
                },
                onDrawSurface = {
                    drawRect(surface.copy(alpha = surfaceAlpha))
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                sheen.copy(alpha = sheenAlpha),
                                Color.Transparent,
                                sheen.copy(alpha = sheenAlpha * 0.5f),
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                    )
                },
            )
        } else {
            // 静态降级：零模糊、零 shader，轮廓与液态版本保持一致。
            // 没有模糊时仍然可以很透：应用内容本身就是主题一致的（浅色页白卡、深色页黑卡），
            // 底栏文字用自己的前景色，不依赖底色对比，所以只补一点点底即可。
            modifier.drawBehind {
                val r = CornerRadius(radius.toPx())
                drawRoundRect(
                    color = surface.copy(alpha = (surfaceAlpha + 0.18f).coerceIn(0f, 0.94f)),
                    cornerRadius = r,
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            sheen.copy(alpha = sheenAlpha * 1.6f),
                            Color.Transparent,
                            sheen.copy(alpha = sheenAlpha),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    cornerRadius = r,
                )
                val inset = 0.5.dp.toPx()
                drawRoundRect(
                    color = border,
                    topLeft = Offset(inset, inset),
                    size = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius((radius.toPx() - inset).coerceAtLeast(0f)),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }

    FloatingNavigationBar(
        // cornerRadius = 0 让 MIUIX 不做 clip / 不画背景，
        // 圆角轮廓与投影全部交给 drawBackdrop，否则阴影会被裁剪掉。
        modifier = glassModifier,
        color = Color.Transparent,
        cornerRadius = 0.dp,
        horizontalOutSidePadding = 20.dp,
        shadowElevation = 0.dp,
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

/**
 * 可选中的胶囊标签：单选（采样间隔）与多选（OSD 指标）共用。
 *
 * 选中态用主题主色实心 + 白色文字 + 勾选图标，
 * 未选中态用 `surfaceContainerHigh`，在浅色页面上与卡片有清晰层次。
 */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier
            .clip(CircleShape)
            // 未选中态用 MIUIX 层级的 surfaceContainerHigh：暗色已在 Theme.kt 统一提亮到
            // #343434（官方值与卡片同为 #242424 会让 chip 失去边界）；浅色保持官方 #E8E8E8，
            // 它与白卡片的对比（1.29:1）比 Material 的 #F0F0F0（1.14:1）更清晰。
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariantSummary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 空状态：渐变图标底 + 标题 + 说明，避免列表为空时只剩一行灰字。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            // 暗色下 13% 的主色渐变在深底上几乎不可见，提升到 20%/7%
                            colors.primary.copy(alpha = if (isDark) 0.20f else 0.13f),
                            colors.primary.copy(alpha = if (isDark) 0.07f else 0.04f),
                        ),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.primary.copy(alpha = if (isDark) 1f else 0.86f),
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 统一指标瓦片：仪表盘小指标、详情页统计网格共用。 */
@Composable
fun SurfaceTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
) {
    Column(
        modifier
            .clip(shape)
            .background(containerColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leading?.invoke()
            Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

/** 环形进度指示器：用于权限完成度等紧凑场景。 */
@Composable
fun CircularProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.primary,
    trackColor: Color = MiuixTheme.colorScheme.surfaceContainerHigh,
    strokeWidth: Dp = 6.dp,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "circularProgress",
    )
    val stroke = with(LocalDensity.current) { strokeWidth.toPx() }
    Canvas(modifier.size(52.dp)) {
        val diameter = size.minDimension - stroke
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = animated * 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** 列表项入场动画：淡入 + 轻微上移，按 [index] 错开时序。 */
@Composable
fun AnimatedListItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    // 延迟按 index 线性增长要设上限：长列表里第 50 项会等 1.75s 才浮现。
    val delay = (index.coerceAtMost(MAX_STAGGER_INDEX) * STAGGER_STEP_MS)
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(tween(280, delay)) + slideInVertically(tween(280, delay)) { it / 6 },
    ) {
        content()
    }
}

/** 错开动画的最大参与索引：超过后延迟不再增长。 */
private const val MAX_STAGGER_INDEX = 8

/** 每个索引的延迟步长。 */
private const val STAGGER_STEP_MS = 35
