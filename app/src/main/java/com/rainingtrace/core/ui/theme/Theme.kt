package com.rainingtrace.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = RainBlueDeep,
    onPrimary = PaperSurface,
    primaryContainer = RainBlue,
    onPrimaryContainer = PaperSurface,
    secondary = MossGreen,
    onSecondary = PaperSurface,
    tertiary = AmberMark,
    onTertiary = InkPrimary,
    background = PaperBackground,
    onBackground = InkPrimary,
    surface = PaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = PaperOutline,
    outlineVariant = PaperOutline,
)

private val DarkColors = darkColorScheme(
    primary = RainBlueNight,
    onPrimary = NightBackground,
    primaryContainer = RainBlueDeep,
    onPrimaryContainer = NightInkPrimary,
    secondary = MossGreenNight,
    onSecondary = NightBackground,
    tertiary = AmberNight,
    onTertiary = NightBackground,
    background = NightBackground,
    onBackground = NightInkPrimary,
    surface = NightSurface,
    onSurface = NightInkPrimary,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightInkSecondary,
    outline = NightOutline,
    outlineVariant = NightOutline,
)

private val RainingTraceShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
)

@Composable
fun RainingTraceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 默认关闭 Material You 动态取色：雨后手账品牌色优先；需要时可显式打开。
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RainingTraceTypography,
        shapes = RainingTraceShapes,
        content = content,
    )
}
