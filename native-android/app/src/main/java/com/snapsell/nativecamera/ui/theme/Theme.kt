package com.snapsell.nativecamera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Obsidian Lens color palette (matching the Capacitor app)
val Primary = Color(0xFF69F6B8)
val PrimaryContainer = Color(0xFF06B77F)
val OnPrimary = Color(0xFF005A3C)
val SurfaceLowest = Color(0xFF000000)
val SurfaceColor = Color(0xFF0E0E0E)
val SurfaceContainer = Color(0xFF191919)
val SurfaceHigh = Color(0xFF1F1F1F)
val OnSurface = Color(0xFFFFFFFF)
val OnSurfaceVariant = Color(0xFFABABAB)
val Outline = Color(0xFF757575)
val OutlineVariant = Color(0xFF484848)
val Error = Color(0xFFFF716C)
val Secondary = Color(0xFF64A8FE)
val SurfaceDark = Color(0xFF12161C)
val SurfaceLight = Color(0xFF1A2030)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    secondary = Secondary,
    surface = SurfaceColor,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
)

@Composable
fun SnapSellTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}