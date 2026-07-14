package com.launcher360v2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LauncherColorScheme = darkColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFFB0B0B0),
    background = Color.Transparent,
    surface = Color(0x1AFFFFFF),       // 10% white — for cards/surfaces
    onSurface = Color.White,
    onBackground = Color.White,
    outline = Color(0x33FFFFFF)
)

@Composable
fun Launcher360Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LauncherColorScheme,
        typography = LauncherTypography,
        content = content
    )
}
