package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val SmartShopDarkColorScheme = darkColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = CyanSecondary,
    onSecondary = NavyVoid,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = EmeraldProfit,
    onTertiary = NavyVoid,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = NavyVoid,
    onBackground = SlateTextPrimary,
    surface = NavyBase,
    onSurface = SlateTextPrimary,
    surfaceVariant = NavyCard,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerLowest = NavyVoid,
    surfaceContainerLow = NavyBase,
    surfaceContainer = NavySurface,
    surfaceContainerHigh = NavyCard,
    surfaceContainerHighest = NavyElevated,
    outline = NavyBorder,
    outlineVariant = Color(0xFF1E3256),
    error = CrimsonDanger,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = OnCrimsonContainer
)

private val SmartShopShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SmartShopTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = SmartShopDarkColorScheme,
            typography = AppTypography,
            shapes = SmartShopShapes,
            content = content
        )
    }
}
