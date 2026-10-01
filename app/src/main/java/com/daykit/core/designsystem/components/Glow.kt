package com.daykit.core.designsystem.components

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * A soft radial glow behind the content that does not band.
 *
 * Compose's `Brush.radialGradient` is linear and undithered: a faint glow spans
 * only a few 8-bit color steps across hundreds of pixels, so it shows rings, and
 * the linear falloff adds a visible edge where it meets the background. This one
 * fades along a smoothstep curve (no edge) and is drawn with a dithering paint,
 * which spreads each step below what the eye can resolve.
 *
 * [center] and [radius] are fractions of the element's width/height and width.
 */
fun Modifier.ditheredGlow(
    color: Color,
    alpha: Float,
    center: Offset = Offset(1f, 0f),
    radius: Float = 0.9f,
): Modifier = drawWithCache {
    val radiusPx = size.width * radius
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        if (radiusPx > 0f) {
            shader = RadialGradient(
                size.width * center.x,
                size.height * center.y,
                radiusPx,
                GlowStops.map { t -> color.copy(alpha = alpha * glowFalloff(t)).toArgb() }.toIntArray(),
                GlowStops.toFloatArray(),
                Shader.TileMode.CLAMP,
            )
        }
    }
    onDrawBehind {
        if (radiusPx > 0f) {
            drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
        }
    }
}

/** Enough stops that the piecewise-linear shader follows the curve smoothly. */
private val GlowStops = List(9) { it / 8f }

/** 1 at the center to 0 at the rim, with zero slope at the rim: 1 − smoothstep(t). */
internal fun glowFalloff(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - x * x * (3f - 2f * x)
}
