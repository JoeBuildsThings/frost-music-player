package com.joebuildsthings.frost.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun FrostTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.bgTop,
            onBackground = palette.ink,
            surface = palette.bgBottom,
            onSurface = palette.ink,
            onSurfaceVariant = palette.inkMuted
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.bgTop,
            onBackground = palette.ink,
            surface = Color.White,
            onSurface = palette.ink,
            onSurfaceVariant = palette.inkMuted
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
