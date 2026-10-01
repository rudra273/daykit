package com.daykit.core.designsystem.background

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The graphic backgrounds (Waves, Orbit, Contour): shapes and lines for glass to
 * refract, unlike the soft meshes. Drawn in code at the window's size, so they
 * are sharp on any screen and cost nothing in APK size.
 *
 * Every coordinate and stroke is a fraction of the canvas, so the same code
 * draws the full page and the Settings thumbnail. All gradients use a dithering
 * paint (see `ditheredGlow` for why).
 */
internal object GeneratedArt {
    /** Longest edge rendered; matches the photo limit. */
    private const val MAX_EDGE = 2400

    private var cache: Pair<Key, PageBackground.Image>? = null
    private data class Key(val kind: PageBackgroundKind, val dark: Boolean, val width: Int, val height: Int)

    fun isGenerated(kind: PageBackgroundKind) =
        kind == PageBackgroundKind.Waves || kind == PageBackgroundKind.Orbit || kind == PageBackgroundKind.Contour

    /** The page-ready background (sharp + frosted) for a [window] in pixels; cached per kind/theme/size. */
    suspend fun load(kind: PageBackgroundKind, dark: Boolean, window: Size): PageBackground.Image {
        val scale = min(1f, MAX_EDGE / max(window.width, window.height))
        val key = Key(kind, dark, (window.width * scale).roundToInt(), (window.height * scale).roundToInt())
        cache?.let { (cachedKey, image) -> if (cachedKey == key) return image }
        return withContext(Dispatchers.Default) {
            val bitmap = render(kind, dark, key.width, key.height)
            Frosting.toImageBackground(bitmap, solid = baseColor(kind, dark), scrim = Color.Transparent)
        }.also { cache = key to it }
    }

    /** Draws [kind] into a new bitmap of the given size. */
    fun render(kind: PageBackgroundKind, dark: Boolean, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        when (kind) {
            PageBackgroundKind.Waves -> drawWaves(canvas, if (dark) DarkWaves else LightWaves)
            PageBackgroundKind.Orbit -> drawOrbit(canvas, if (dark) DarkOrbit else LightOrbit)
            PageBackgroundKind.Contour -> drawContour(canvas, if (dark) DarkContour else LightContour)
            else -> error("$kind is not a generated background")
        }
        return bitmap
    }

    private fun baseColor(kind: PageBackgroundKind, dark: Boolean): Color = when (kind) {
        PageBackgroundKind.Waves -> (if (dark) DarkWaves else LightWaves).top
        PageBackgroundKind.Orbit -> (if (dark) DarkOrbit else LightOrbit).top
        else -> (if (dark) DarkContour else LightContour).top
    }

    // ---- Waves: layered flowing bands, a bright line along each crest -------------------------

    private class WavesPalette(val top: Color, val bottom: Color, val bands: List<Pair<Color, Color>>, val crest: Color)

    private fun drawWaves(canvas: Canvas, palette: WavesPalette) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        fillVertical(canvas, palette.top, palette.bottom)
        val crest = strokePaint(palette.crest, width = min(w, h) * 0.004f)
        palette.bands.forEachIndexed { i, (left, right) ->
            // Back to front; the top 35% stays calm so headers read cleanly.
            val baseline = h * (0.36f + i * 0.13f)
            val amplitude = h * (0.040f + i * 0.006f)
            val curve = wavePath(w, baseline) { x ->
                amplitude * (0.7f * sin(2 * PI.toFloat() * (x * (0.9f + i * 0.15f)) + i * 1.7f) +
                    0.3f * sin(2 * PI.toFloat() * (x * (2.1f - i * 0.1f)) + i * 0.6f))
            }
            val band = Path(curve).apply {
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            canvas.drawPath(band, ditheredPaint(LinearGradient(0f, 0f, w, h * 0.3f, left.toArgb(), right.toArgb(), Shader.TileMode.CLAMP)))
            canvas.drawPath(curve, crest)
        }
    }

    private val LightWaves = WavesPalette(
        top = Color(0xFFF1F4FF),
        bottom = Color(0xFFF6EFFF),
        bands = listOf(
            Color(0xFFD9E4FF) to Color(0xFFEBDDFF),
            Color(0xFFBCD0FF) to Color(0xFFDCC5FF),
            Color(0xFF9DB9FF) to Color(0xFFCBA8FF),
            Color(0xFF7FA0FA) to Color(0xFFB98CF5),
            Color(0xFF6787EE) to Color(0xFFA674E8),
        ),
        crest = Color.White.copy(alpha = 0.65f),
    )

    private val DarkWaves = WavesPalette(
        top = Color(0xFF0D1120),
        bottom = Color(0xFF140F2A),
        bands = listOf(
            Color(0xFF18234A) to Color(0xFF261C4F),
            Color(0xFF1F3170) to Color(0xFF34237A),
            Color(0xFF2A44A0) to Color(0xFF4A2DA6),
            Color(0xFF3557C4) to Color(0xFF6236C4),
            Color(0xFF4469DB) to Color(0xFF7A45D6),
        ),
        crest = Color.White.copy(alpha = 0.22f),
    )

    // ---- Orbit: glossy spheres and fine concentric rings --------------------------------------

    private class Sphere(val x: Float, val y: Float, val radius: Float, val light: Color, val body: Color, val edge: Color)
    private class OrbitPalette(val top: Color, val bottom: Color, val ring: Color, val spheres: List<Sphere>, val dot: Color)

    private fun drawOrbit(canvas: Canvas, palette: OrbitPalette) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val s = min(w, h)
        fillVertical(canvas, palette.top, palette.bottom)

        // Rings around the upper sphere, fading outward.
        val cx = w * 0.80f
        val cy = h * 0.30f
        var radius = s * 0.30f
        var step = 0
        while (radius < max(w, h) * 1.1f) {
            val fade = 1f - step / 16f
            if (fade <= 0f) break
            canvas.drawCircle(cx, cy, radius, strokePaint(palette.ring.copy(alpha = palette.ring.alpha * fade), s * 0.0025f))
            radius += s * 0.075f
            step++
        }

        palette.spheres.forEach { sphere ->
            val r = sphere.radius * s
            val x = sphere.x * w
            val y = sphere.y * h
            // Light from the upper left: highlight → body → darker rim.
            val shader = RadialGradient(
                x - r * 0.35f, y - r * 0.40f, r * 1.45f,
                intArrayOf(sphere.light.toArgb(), sphere.body.toArgb(), sphere.edge.toArgb()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, r, ditheredPaint(shader))
        }

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.dot.toArgb() }
        listOf(0.30f to 0.22f, 0.12f to 0.40f, 0.55f to 0.52f, 0.90f to 0.62f, 0.40f to 0.92f).forEach { (x, y) ->
            canvas.drawCircle(x * w, y * h, s * 0.012f, dot)
        }
    }

    private val LightOrbit = OrbitPalette(
        top = Color(0xFFF5F2FF),
        bottom = Color(0xFFEAF3FF),
        ring = Color(0xFF7D86C9).copy(alpha = 0.40f),
        spheres = listOf(
            Sphere(0.80f, 0.30f, 0.22f, Color(0xFFFFD2C8), Color(0xFFFF8E7C), Color(0xFFE0605A)),
            Sphere(0.12f, 0.64f, 0.30f, Color(0xFFDCCFFF), Color(0xFF9C80FF), Color(0xFF6E52E0)),
            Sphere(0.74f, 0.88f, 0.17f, Color(0xFFC8F3EE), Color(0xFF52CBBF), Color(0xFF239C93)),
        ),
        dot = Color(0xFF7D86C9).copy(alpha = 0.45f),
    )

    private val DarkOrbit = OrbitPalette(
        top = Color(0xFF0E0F1E),
        bottom = Color(0xFF111829),
        ring = Color(0xFF8C96E0).copy(alpha = 0.26f),
        spheres = listOf(
            Sphere(0.80f, 0.30f, 0.22f, Color(0xFFFF9C8C), Color(0xFFE5564B), Color(0xFF7A2230)),
            Sphere(0.12f, 0.64f, 0.30f, Color(0xFFB4A0FF), Color(0xFF6A4CE6), Color(0xFF2B1F6B)),
            Sphere(0.74f, 0.88f, 0.17f, Color(0xFF8FE8DE), Color(0xFF1FA597), Color(0xFF0B4A47)),
        ),
        dot = Color(0xFF8C96E0).copy(alpha = 0.40f),
    )

    // ---- Contour: flowing topographic lines over soft color -----------------------------------

    private class ContourPalette(
        val top: Color,
        val bottom: Color,
        val blobs: List<MeshBlob>,
        val lineFrom: Color,
        val lineTo: Color,
    )

    private fun drawContour(canvas: Canvas, palette: ContourPalette) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        fillVertical(canvas, palette.top, palette.bottom)
        palette.blobs.forEach { canvas.drawRect(0f, 0f, w, h, it.paint(Size(w, h))) }

        val lines = 34
        for (i in 0 until lines) {
            val t = i / (lines - 1f)
            val baseline = h * (-0.04f + t * 1.08f)
            val path = wavePath(w, baseline) { x ->
                h * (0.034f * sin(2 * PI.toFloat() * x * 1.15f + i * 0.28f) +
                    0.018f * sin(2 * PI.toFloat() * x * 2.6f - i * 0.17f + 1.3f))
            }
            canvas.drawPath(path, strokePaint(lerp(palette.lineFrom, palette.lineTo, t), min(w, h) * 0.0035f))
        }
    }

    private val LightContour = ContourPalette(
        top = Color(0xFFF3F6FA),
        bottom = Color(0xFFEFF5F2),
        blobs = listOf(
            MeshBlob(0.85f, 0.25f, 0.55f, Color(0xFF9FDCEB), 0.55f),
            MeshBlob(0.10f, 0.80f, 0.60f, Color(0xFFFFC3A8), 0.50f),
        ),
        lineFrom = Color(0xFF6F8FD8).copy(alpha = 0.55f),
        lineTo = Color(0xFFC77DCB).copy(alpha = 0.55f),
    )

    private val DarkContour = ContourPalette(
        top = Color(0xFF0B1316),
        bottom = Color(0xFF101422),
        blobs = listOf(
            MeshBlob(0.85f, 0.25f, 0.55f, Color(0xFF136E85), 0.50f),
            MeshBlob(0.10f, 0.80f, 0.60f, Color(0xFF5A2E6E), 0.50f),
        ),
        lineFrom = Color(0xFF3FA7C9).copy(alpha = 0.50f),
        lineTo = Color(0xFF8B6BE0).copy(alpha = 0.50f),
    )

    // ---- Shared helpers ------------------------------------------------------------------------

    /** An open path y = baseline + offset(x / width) across the full width, finely sampled. */
    private inline fun wavePath(width: Float, baseline: Float, offset: (Float) -> Float): Path {
        val samples = 120
        return Path().apply {
            for (step in 0..samples) {
                val x = width * step / samples
                val y = baseline + offset(step / samples.toFloat())
                if (step == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
    }

    private fun fillVertical(canvas: Canvas, top: Color, bottom: Color) {
        val h = canvas.height.toFloat()
        canvas.drawRect(
            0f, 0f, canvas.width.toFloat(), h,
            ditheredPaint(LinearGradient(0f, 0f, 0f, h, top.toArgb(), bottom.toArgb(), Shader.TileMode.CLAMP)),
        )
    }

    private fun ditheredPaint(shader: Shader) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { this.shader = shader }

    private fun strokePaint(color: Color, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color.toArgb()
    }
}
