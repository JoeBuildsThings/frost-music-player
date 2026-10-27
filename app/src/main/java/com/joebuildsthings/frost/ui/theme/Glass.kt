package com.joebuildsthings.frost.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

@Composable
fun barHazeStyle(): HazeStyle {
    val dark = LocalPalette.current.dark
    return remember(dark) {
        val base = if (dark) Color(0xFF0B1220) else Color.White
        HazeStyle(
            backgroundColor = base,
            tints = listOf(HazeTint(base.copy(alpha = if (dark) 0.50f else 0.46f))),
            blurRadius = 20.dp,
            noiseFactor = 0.02f,
            fallbackTint = HazeTint(base.copy(alpha = 0.92f))
        )
    }
}

@Composable
fun panelHazeStyle(): HazeStyle {
    val dark = LocalPalette.current.dark
    return remember(dark) {
        val base = if (dark) Color(0xFF0B1220) else Color.White
        HazeStyle(
            backgroundColor = base,
            tints = listOf(HazeTint(base.copy(alpha = if (dark) 0.40f else 0.34f))),
            blurRadius = 28.dp,
            noiseFactor = 0.02f,
            fallbackTint = HazeTint(base.copy(alpha = 0.88f))
        )
    }
}

@Composable
fun Modifier.glassEdge(shape: Shape): Modifier {
    val p = LocalPalette.current
    val mid = p.edgeFade.copy(alpha = p.edgeFade.alpha * 0.4f)
    return this.border(1.dp, Brush.linearGradient(listOf(p.edgeStrong, mid, p.edgeFade)), shape)
}

@Composable
private fun sheenBrush(): Brush {
    val dark = LocalPalette.current.dark
    return Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.10f else 0.35f),
        0.45f to Color.White.copy(alpha = 0f),
        1f to Color.White.copy(alpha = if (dark) 0.04f else 0.14f)
    )
}

@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp)): Modifier {
    val p = LocalPalette.current
    return this
        .shadow(14.dp, shape, ambientColor = p.shadow, spotColor = p.shadow)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(p.glassTop, p.glassBottom)))
        .background(sheenBrush())
        .glassEdge(shape)
}

@Composable
fun Modifier.glassFlat(shape: Shape = RoundedCornerShape(20.dp)): Modifier {
    val p = LocalPalette.current
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(p.flatTop, p.flatBottom)))
        .background(sheenBrush())
        .glassEdge(shape)
}

private fun DrawScope.blob(color: Color, cx: Float, cy: Float, r: Float) {
    val c = Offset(cx, cy)
    drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = r),
        radius = r,
        center = c
    )
}

@Composable
fun Modifier.dynamicGlow(colors: List<Color>?): Modifier {
    val p = LocalPalette.current
    val spec = tween<Color>(durationMillis = 900)
    val c0 by animateColorAsState(colors?.getOrNull(0) ?: p.blobBlue, spec, label = "glow0")
    val c1 by animateColorAsState(colors?.getOrNull(1) ?: p.blobMint, spec, label = "glow1")
    val c2 by animateColorAsState(colors?.getOrNull(2) ?: p.blobPeach, spec, label = "glow2")
    val c3 by animateColorAsState(colors?.getOrNull(3) ?: p.blobSky, spec, label = "glow3")
    val dim = if (p.dark) 0.45f else 1f
    val top = p.bgTop
    val bottom = p.bgBottom
    return this
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .drawBehind {
            blob(c0.copy(alpha = c0.alpha * dim), size.width * 0.10f, size.height * 0.08f, size.width * 0.95f)
            blob(c1.copy(alpha = c1.alpha * dim), size.width * 0.98f, size.height * 0.42f, size.width * 0.80f)
            blob(c2.copy(alpha = c2.alpha * dim), size.width * 0.15f, size.height * 0.70f, size.width * 0.85f)
            blob(c3.copy(alpha = c3.alpha * dim), size.width * 0.90f, size.height * 0.96f, size.width * 0.80f)
        }
}
