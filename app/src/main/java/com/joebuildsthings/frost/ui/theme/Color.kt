package com.joebuildsthings.frost.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

class FrostPalette(
    val dark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val blobBlue: Color,
    val blobMint: Color,
    val blobPeach: Color,
    val blobSky: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val accentTint: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val flatTop: Color,
    val flatBottom: Color,
    val glassFlat: Color,
    val edgeStrong: Color,
    val edgeFade: Color,
    val shadow: Color,
    val surface: Color,
    val scrim: Color
)

val LightPalette = FrostPalette(
    dark = false,
    bgTop = Color(0xFFEAF2FF),
    bgBottom = Color(0xFFFCF9F4),
    blobBlue = Color(0xFF8EB8FF),
    blobMint = Color(0xFF8FE3C8),
    blobPeach = Color(0xFFFFB98F),
    blobSky = Color(0xFFA5D8FF),
    ink = Color(0xFF0E1726),
    inkMuted = Color(0xFF566376),
    accent = Color(0xFF1F6BFF),
    accentTint = Color(0x1F1F6BFF),
    glassTop = Color(0xC2FFFFFF),
    glassBottom = Color(0x6BFFFFFF),
    flatTop = Color(0xB3FFFFFF),
    flatBottom = Color(0x59FFFFFF),
    glassFlat = Color(0x99FFFFFF),
    edgeStrong = Color(0xFFFFFFFF),
    edgeFade = Color(0x99FFFFFF),
    shadow = Color(0x331E3A66),
    surface = Color(0xF7FFFFFF),
    scrim = Color(0x61FFFFFF)
)

val DarkPalette = FrostPalette(
    dark = true,
    bgTop = Color(0xFF0B1220),
    bgBottom = Color(0xFF111A2B),
    blobBlue = Color(0xFF3A6FE0),
    blobMint = Color(0xFF2AA98C),
    blobPeach = Color(0xFFC9703A),
    blobSky = Color(0xFF3590C8),
    ink = Color(0xFFF1F5F9),
    inkMuted = Color(0xFFA3B0C2),
    accent = Color(0xFF3F7FF2),
    accentTint = Color(0x333F7FF2),
    glassTop = Color(0x38FFFFFF),
    glassBottom = Color(0x14FFFFFF),
    flatTop = Color(0x2EFFFFFF),
    flatBottom = Color(0x12FFFFFF),
    glassFlat = Color(0x26FFFFFF),
    edgeStrong = Color(0x80FFFFFF),
    edgeFade = Color(0x1FFFFFFF),
    shadow = Color(0x66000000),
    surface = Color(0xF21B2433),
    scrim = Color(0x80000000)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

val BgTop: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.bgTop
val BgBottom: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.bgBottom
val BlobBlue: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.blobBlue
val BlobMint: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.blobMint
val BlobPeach: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.blobPeach
val BlobSky: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.blobSky
val Ink: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val InkMuted: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.inkMuted
val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
val AccentTint: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.accentTint
val GlassFlat: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.glassFlat
val GlassEdgeStrong: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.edgeStrong
val GlassEdgeFade: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.edgeFade
val GlassShadow: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.shadow
val PanelSurface: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
val PanelScrim: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.scrim
