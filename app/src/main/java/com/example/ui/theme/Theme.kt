package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SathiLightColorScheme = lightColorScheme(
    primary = SathiEmerald,
    onPrimary = Color.White,
    primaryContainer = SathiEmeraldContainer,
    onPrimaryContainer = SathiOnEmeraldContainer,
    secondary = SathiTerracotta,
    onSecondary = Color.White,
    secondaryContainer = SathiTerracottaContainer,
    onSecondaryContainer = SathiOnTerracottaContainer,
    tertiary = SathiLotusGold,
    onTertiary = Color.White,
    tertiaryContainer = SathiGoldContainer,
    onTertiaryContainer = SathiOnGoldContainer,
    background = SathiWarmIvory,
    onBackground = SathiDeepInk,
    surface = SathiWarmSurface,
    onSurface = SathiDeepInk,
    surfaceVariant = SathiWarmCard,
    onSurfaceVariant = SathiMutedInk,
    outline = SathiWarmOutline
)

private val SathiDarkColorScheme = darkColorScheme(
    primary = SathiNightEmerald,
    onPrimary = SathiEmeraldDark,
    primaryContainer = SathiEmeraldDark,
    onPrimaryContainer = SathiEmeraldContainer,
    secondary = SathiNightTerracotta,
    onSecondary = SathiOnTerracottaContainer,
    secondaryContainer = Color(0xFF5A2514),
    onSecondaryContainer = SathiTerracottaContainer,
    tertiary = SathiNightGold,
    onTertiary = SathiOnGoldContainer,
    tertiaryContainer = Color(0xFF4D3300),
    onTertiaryContainer = SathiGoldContainer,
    background = SathiNightBg,
    onBackground = SathiNightInk,
    surface = SathiNightSurface,
    onSurface = SathiNightInk,
    surfaceVariant = SathiNightCard,
    onSurfaceVariant = SathiNightMuted,
    outline = Color(0xFF2B4B46)
)

val SathiShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SathiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SathiDarkColorScheme else SathiLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SathiTypography,
        shapes = SathiShapes,
        content = content
    )
}
