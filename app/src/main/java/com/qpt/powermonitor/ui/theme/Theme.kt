package com.qpt.powermonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightScheme

/**
 * 主题分两层：
 * 1. MiuixTheme —— 官方 MIUIX 配色，决定所有 MIUIX 组件的观感（默认即 HyperOS 蓝）；
 * 2. MaterialTheme —— 仅用于少数仍使用 Material 组件/图表的场景，色值对齐 MIUIX，
 *    避免两套配色打架（浅色：页面 #F7F7F7 + 白卡片；深色：纯黑页面 + #242424 卡片）。
 */

private val MiuixLight = miuixLightScheme()
private val MiuixDark = miuixDarkScheme()

private val MaterialLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF3482FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAF2FF),
    onPrimaryContainer = Color(0xFF3482FF),
    secondary = Color(0xFF6A6A6A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color(0xFF303030),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF1DD),
    onTertiaryContainer = Color(0xFF6B4200),
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0x99000000),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFE94634),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDF6F4),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
)

private val MaterialDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF5B9DFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1B3556),
    onPrimaryContainer = Color(0xFFAECDFF),
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color(0xFFD9D9D9),
    tertiary = Color(0xFFFFC24B),
    onTertiary = Color(0xFF3A2600),
    tertiaryContainer = Color(0xFF3A2A10),
    onTertiaryContainer = Color(0xFFFFD89A),
    background = Color(0xFF000000),
    onBackground = Color(0xE6FFFFFF),
    surface = Color(0xFF242424),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0x99FFFFFF),
    surfaceContainer = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF2D2D2D),
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF393939),
    error = Color(0xFFFF6B5E),
    onError = Color(0xFF3A0906),
    errorContainer = Color(0xFF3A1512),
    onErrorContainer = Color(0xFFFFDAD5),
    scrim = Color(0xFF000000),
)

/** 状态胶囊配色：MIUIX 本身没有语义色，这里按 HyperOS 的通知色系补齐。 */
data class StatusColors(
    val okBg: Color,
    val okFg: Color,
    val warnBg: Color,
    val warnFg: Color,
    val errorBg: Color,
    val errorFg: Color,
    val neutralBg: Color,
    val neutralFg: Color,
)

private val LightStatus = StatusColors(
    okBg = Color(0xFFE7F7EC), okFg = Color(0xFF1F8A45),
    warnBg = Color(0xFFFFF1DD), warnFg = Color(0xFF8A5A00),
    errorBg = Color(0xFFFDECEA), errorFg = Color(0xFFC0392B),
    neutralBg = Color(0xFFF0F0F0), neutralFg = Color(0xFF666666),
)

private val DarkStatus = StatusColors(
    okBg = Color(0xFF16301F), okFg = Color(0xFF6FE08F),
    warnBg = Color(0xFF3A2A10), warnFg = Color(0xFFFFD08A),
    errorBg = Color(0xFF3A1512), errorFg = Color(0xFFFF9A8F),
    neutralBg = Color(0xFF2D2D2D), neutralFg = Color(0xFFB0B0B0),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

data class ChartColors(
    val axis: Color,
    val grid: Color,
    val label: Color,
    val crosshair: Color,
    val tooltipBg: Color,
    val tooltipBorder: Color,
    val tooltipText: Color,
    val tooltipMuted: Color,
    val palette: List<Color>,
)

private val LightCharts = ChartColors(
    axis = Color(0xFFC9C9C9),
    grid = Color(0xFFEDEDED),
    label = Color(0xFF7A7A7A),
    crosshair = Color(0xCC000000),
    tooltipBg = Color(0xF2FFFFFF),
    tooltipBorder = Color(0xFFE0E0E0),
    tooltipText = Color(0xFF000000),
    tooltipMuted = Color(0xFF7A7A7A),
    palette = listOf(
        Color(0xFF3482FF), Color(0xFF00A0A0), Color(0xFFE6A23C), Color(0xFFE94634),
        Color(0xFF8A6BFF), Color(0xFF2FA84F), Color(0xFFD64BB0), Color(0xFF0F8FB0),
    ),
)

private val DarkCharts = ChartColors(
    axis = Color(0xFF4A4A4A),
    grid = Color(0xFF2E2E2E),
    label = Color(0xFFB0B0B0),
    crosshair = Color(0xCCFFFFFF),
    tooltipBg = Color(0xF2242424),
    tooltipBorder = Color(0xFF3A3A3A),
    tooltipText = Color(0xFFF2F2F2),
    tooltipMuted = Color(0xFF9A9A9A),
    palette = listOf(
        Color(0xFF5B9DFF), Color(0xFF33D6A6), Color(0xFFFFCF66), Color(0xFFFF7A7A),
        Color(0xFFC9A7FF), Color(0xFFB4F17F), Color(0xFFFF9DE2), Color(0xFF7ED9E8),
    ),
)

val LocalChartColors = staticCompositionLocalOf { DarkCharts }

private val QptTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

/** MIUIX 卡片圆角 16dp，页面级浮层 28dp。 */
private val QptShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun QptTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalChartColors provides if (darkTheme) DarkCharts else LightCharts,
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus,
    ) {
        MiuixTheme(colors = if (darkTheme) MiuixDark else MiuixLight) {
            MaterialTheme(
                colorScheme = if (darkTheme) MaterialDark else MaterialLight,
                typography = QptTypography,
                shapes = QptShapes,
                content = content,
            )
        }
    }
}
