package com.daykit.core.designsystem.background

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.daykit.core.designsystem.components.glowFalloff
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What is painted behind every screen, resolved for the current theme.
 *
 * Everything is drawn in **window coordinates**: the page and every glass surface
 * call [drawRegion] with their own position in the window, so a glass card shows
 * exactly the part of the background that sits behind it, and pages stacked
 * during a predictive-back swipe line up.
 */
@Immutable
sealed interface PageBackground {
    /** Opaque color behind/under the background; also the base for top and bottom bars. */
    val solid: Color

    /** True when something other than a flat color is showing. */
    val isDecorated: Boolean get() = this !is Plain

    /**
     * Draws the part of the background under an element at [origin] (its position
     * in the window) into the current bounds. [frosted] asks for the blurred
     * variant used behind glass.
     */
    fun DrawScope.drawRegion(origin: Offset, window: Size, frosted: Boolean)

    data class Plain(override val solid: Color) : PageBackground {
        override fun DrawScope.drawRegion(origin: Offset, window: Size, frosted: Boolean) {
            drawRect(solid)
        }
    }

    /**
     * A soft generated "mesh": a base color with large blurred color fields.
     * It is already as smooth as a blur, so glass draws it as-is.
     */
    class Mesh(private val palette: MeshPalette) : PageBackground {
        override val solid: Color get() = palette.base
        private var cachedFor: Size = Size.Zero
        private var paints: List<Paint> = emptyList()

        private fun paintsFor(window: Size): List<Paint> {
            if (window != cachedFor) {
                paints = palette.blobs.map { it.paint(window) }
                cachedFor = window
            }
            return paints
        }

        override fun DrawScope.drawRegion(origin: Offset, window: Size, frosted: Boolean) {
            drawRect(palette.base)
            if (window.minDimension <= 0f) return
            val blobs = paintsFor(window)
            // Shift into window space; only this element's bounds are touched.
            translate(-origin.x, -origin.y) {
                drawIntoCanvas { canvas ->
                    blobs.forEach { paint ->
                        canvas.nativeCanvas.drawRect(
                            origin.x, origin.y, origin.x + size.width, origin.y + size.height, paint,
                        )
                    }
                }
            }
        }
    }

    /**
     * The user's photo with a legibility scrim baked in, scaled to cover the window.
     * [blurred] is a small, blurred, more saturated copy used behind glass.
     */
    class Image(
        override val solid: Color,
        private val sharp: ImageBitmap,
        private val blurred: ImageBitmap,
    ) : PageBackground {
        override fun DrawScope.drawRegion(origin: Offset, window: Size, frosted: Boolean) {
            drawRect(solid)
            if (window.minDimension <= 0f) return
            val image = if (frosted) blurred else sharp
            val region = imageRegion(origin, size, window, IntSize(image.width, image.height)) ?: return
            drawImage(
                image = image,
                srcOffset = region.srcOffset,
                srcSize = region.srcSize,
                dstOffset = region.dstOffset,
                dstSize = region.dstSize,
            )
        }
    }
}

/** Which image pixels to draw where, for [imageRegion]. */
internal data class ImageRegion(
    val srcOffset: IntOffset,
    val srcSize: IntSize,
    val dstOffset: IntOffset,
    val dstSize: IntSize,
)

/**
 * Maps the on-screen part of an element ([size] at window position [origin]) onto
 * an image scaled to *cover* the window (like `ContentScale.Crop`, centered), so
 * any photo fits any window shape without stretching. Only the visible part is
 * mapped, so an element half scrolled off-screen keeps its backdrop aligned.
 * Null when nothing of the element is on screen.
 */
internal fun imageRegion(origin: Offset, size: Size, window: Size, image: IntSize): ImageRegion? {
    val left = max(origin.x, 0f)
    val top = max(origin.y, 0f)
    val right = min(origin.x + size.width, window.width)
    val bottom = min(origin.y + size.height, window.height)
    if (right <= left || bottom <= top || image.width <= 0 || image.height <= 0) return null

    val scale = max(window.width / image.width, window.height / image.height)
    val shownLeft = (window.width - image.width * scale) / 2f
    val shownTop = (window.height - image.height * scale) / 2f
    val srcLeft = ((left - shownLeft) / scale).roundToInt().coerceIn(0, image.width - 1)
    val srcTop = ((top - shownTop) / scale).roundToInt().coerceIn(0, image.height - 1)
    return ImageRegion(
        srcOffset = IntOffset(srcLeft, srcTop),
        srcSize = IntSize(
            ((right - left) / scale).roundToInt().coerceIn(1, image.width - srcLeft),
            ((bottom - top) / scale).roundToInt().coerceIn(1, image.height - srcTop),
        ),
        dstOffset = IntOffset((left - origin.x).roundToInt(), (top - origin.y).roundToInt()),
        dstSize = IntSize((right - left).roundToInt(), (bottom - top).roundToInt()),
    )
}

/** Provided by `DayKitTheme`; [PageBackground.Plain] unless the user picked something else. */
val LocalPageBackground = staticCompositionLocalOf<PageBackground> { PageBackground.Plain(Color.White) }

/** One soft color field: centered at a fraction of the window, radius a fraction of its longer side. */
@Immutable
data class MeshBlob(val x: Float, val y: Float, val radius: Float, val color: Color, val alpha: Float) {
    /** Dithered, smoothstep-falloff radial paint: the same no-banding recipe as `ditheredGlow`. */
    internal fun paint(window: Size): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        shader = RadialGradient(
            window.width * x,
            window.height * y,
            max(window.width, window.height) * radius,
            STOPS.map { t -> this@MeshBlob.color.copy(alpha = this@MeshBlob.alpha * glowFalloff(t)).toArgb() }.toIntArray(),
            STOPS.toFloatArray(),
            Shader.TileMode.CLAMP,
        )
    }

    private companion object {
        val STOPS = List(9) { it / 8f }
    }
}

@Immutable
data class MeshPalette(val base: Color, val blobs: List<MeshBlob>)

/**
 * The generated backgrounds, each with a light and a dark palette. They are
 * deliberately soft and mid-contrast so text drawn straight on the page (section
 * headers, empty states) stays readable, and glass has color to pick up.
 */
fun meshPalette(kind: PageBackgroundKind, dark: Boolean): MeshPalette? = when (kind) {
    PageBackgroundKind.Aurora -> if (dark) {
        MeshPalette(
            base = Color(0xFF0E1420),
            blobs = listOf(
                MeshBlob(0.10f, 0.08f, 0.65f, Color(0xFF1FA39A), 0.55f),
                MeshBlob(0.95f, 0.30f, 0.60f, Color(0xFF6A3FD0), 0.50f),
                MeshBlob(0.30f, 0.85f, 0.70f, Color(0xFF2456C9), 0.45f),
            ),
        )
    } else {
        MeshPalette(
            base = Color(0xFFEFF4F8),
            blobs = listOf(
                MeshBlob(0.10f, 0.08f, 0.65f, Color(0xFF7FE0CF), 0.55f),
                MeshBlob(0.95f, 0.30f, 0.60f, Color(0xFFB9A2FF), 0.50f),
                MeshBlob(0.30f, 0.85f, 0.70f, Color(0xFF9CC3FF), 0.50f),
            ),
        )
    }
    PageBackgroundKind.Dusk -> if (dark) {
        MeshPalette(
            base = Color(0xFF1A1220),
            blobs = listOf(
                MeshBlob(0.90f, 0.05f, 0.65f, Color(0xFFE0674F), 0.45f),
                MeshBlob(0.05f, 0.45f, 0.60f, Color(0xFFB0367A), 0.45f),
                MeshBlob(0.75f, 0.90f, 0.70f, Color(0xFF5B2FA3), 0.50f),
            ),
        )
    } else {
        MeshPalette(
            base = Color(0xFFFBF2EF),
            blobs = listOf(
                MeshBlob(0.90f, 0.05f, 0.65f, Color(0xFFFFB38A), 0.55f),
                MeshBlob(0.05f, 0.45f, 0.60f, Color(0xFFFF9EC4), 0.45f),
                MeshBlob(0.75f, 0.90f, 0.70f, Color(0xFFC6A8FF), 0.50f),
            ),
        )
    }
    PageBackgroundKind.Lagoon -> if (dark) {
        MeshPalette(
            base = Color(0xFF0B171C),
            blobs = listOf(
                MeshBlob(0.85f, 0.10f, 0.65f, Color(0xFF0E8FB8), 0.50f),
                MeshBlob(0.10f, 0.55f, 0.60f, Color(0xFF14A67A), 0.40f),
                MeshBlob(0.80f, 0.95f, 0.65f, Color(0xFF1C5FA8), 0.50f),
            ),
        )
    } else {
        MeshPalette(
            base = Color(0xFFEEF7F7),
            blobs = listOf(
                MeshBlob(0.85f, 0.10f, 0.65f, Color(0xFF8FDDF5), 0.55f),
                MeshBlob(0.10f, 0.55f, 0.60f, Color(0xFF9DEBC8), 0.50f),
                MeshBlob(0.80f, 0.95f, 0.65f, Color(0xFFA9C8FF), 0.50f),
            ),
        )
    }
    PageBackgroundKind.Plain, PageBackgroundKind.Custom -> null
}
