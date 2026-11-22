package com.daykit.feature.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Built-in procedural DayKit wallpapers crafted specifically for Liquid Glass
 * widgets and cards: 2 Live (Light + Dark) and 2 Static (Light + Dark).
 *
 * Rendered 100% in code with resolution-independent coordinates and dithered
 * gradients — zero bundled image assets.
 */
enum class WallpaperPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val isLive: Boolean,
    val isDark: Boolean,
) {
    LIVE_LIGHT(
        id = "live_light",
        title = "Sunlit Caustics",
        subtitle = "Live · Light",
        isLive = true,
        isDark = false,
    ),
    LIVE_DARK(
        id = "live_dark",
        title = "Obsidian Aurora",
        subtitle = "Live · Dark",
        isLive = true,
        isDark = true,
    ),
    STATIC_LIGHT(
        id = "static_light",
        title = "Tahoe Prism",
        subtitle = "Static · Light",
        isLive = false,
        isDark = false,
    ),
    STATIC_DARK(
        id = "static_dark",
        title = "Velvet Ribbon",
        subtitle = "Static · Dark",
        isLive = false,
        isDark = true,
    );

    companion object {
        fun fromId(id: String?): WallpaperPreset? =
            if (id.isNullOrBlank()) null else entries.firstOrNull { it.id == id }
    }
}

/**
 * Shared procedural renderer for all [WallpaperPreset]s.
 * Used identically by:
 *  - Canvas -> Wallpapers swatch thumbnails
 *  - Canvas -> Wallpapers interactive phone preview
 *  - [WeatherGlassWallpaperService] (60fps live & cached static)
 *  - Direct native static wallpaper export
 */
object LiquidGlassWallpaperRenderer {

    private const val TWO_PI = (2.0 * PI).toFloat()

    fun draw(
        canvas: Canvas,
        preset: WallpaperPreset,
        width: Float,
        height: Float,
        timeSeconds: Float = 0f,
    ) {
        if (width <= 0f || height <= 0f) return
        when (preset) {
            WallpaperPreset.LIVE_LIGHT -> drawSunlitCaustics(canvas, width, height, timeSeconds)
            WallpaperPreset.LIVE_DARK -> drawObsidianAurora(canvas, width, height, timeSeconds)
            WallpaperPreset.STATIC_LIGHT -> drawTahoePrism(canvas, width, height)
            WallpaperPreset.STATIC_DARK -> drawVelvetRibbon(canvas, width, height)
        }
    }

    /**
     * Renders any [WallpaperPreset] into a new [Bitmap] at the requested size.
     * For live presets, [timeSeconds] selects a representative hero frame (e.g. for thumbnails).
     */
    fun renderBitmap(
        preset: WallpaperPreset,
        width: Int,
        height: Int,
        timeSeconds: Float = 1.6f,
    ): Bitmap {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        draw(canvas, preset, safeW.toFloat(), safeH.toFloat(), timeSeconds)
        return bmp
    }

    // =========================================================================
    // 1. LIVE · LIGHT — "Sunlit Caustics"
    //    Inspired by coastal daylight refraction & liquid glass water caustics.
    //    Luminous light-theme palette with rich chromatic mid-tones so white
    //    Liquid Glass rims and frosted typography stay crisp and legible.
    // =========================================================================

    private fun drawSunlitCaustics(canvas: Canvas, w: Float, h: Float, t: Float) {
        val minDim = min(w, h)
        val slow = t * 0.28f

        // 1. Luminous sunlit coastal gradient base
        val basePaint = ditherPaint().apply {
            shader = LinearGradient(
                0f, 0f, w * 0.35f, h,
                intArrayOf(
                    0xFF458CE0.toInt(), // Crisp sunlit sky blue
                    0xFF6BB5EB.toInt(), // Airy cerulean
                    0xFF8B99EF.toInt(), // Soft periwinkle lavender
                    0xFFE5A996.toInt(), // Warm sunlit peach-coral horizon
                ),
                floatArrayOf(0f, 0.34f, 0.70f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, basePaint)

        // 2. Orbiting chromatic mesh blooms (organic Lissajous drift)
        drawRadialBloom(
            canvas = canvas,
            cx = w * (0.78f + 0.14f * cos(slow * 0.85f)),
            cy = h * (0.22f + 0.09f * sin(slow * 0.72f)),
            radius = minDim * 0.92f,
            centerColor = 0xAA3CE6D8.toInt(), // Bright aqua-mint
            midColor = 0x4462D8E8.toInt(),
        )
        drawRadialBloom(
            canvas = canvas,
            cx = w * (0.20f + 0.13f * sin(slow * 0.68f + 1.2f)),
            cy = h * (0.48f + 0.10f * cos(slow * 0.56f)),
            radius = minDim * 0.96f,
            centerColor = 0x99A984FF.toInt(), // Luminous orchid-lavender
            midColor = 0x3D8A7EF5.toInt(),
        )
        drawRadialBloom(
            canvas = canvas,
            cx = w * (0.76f + 0.12f * sin(slow * 0.62f + 2.4f)),
            cy = h * (0.78f + 0.08f * cos(slow * 0.78f + 0.5f)),
            radius = minDim * 0.90f,
            centerColor = 0xBBFFB580.toInt(), // Warm sunlit apricot
            midColor = 0x44F79C84.toInt(),
        )
        drawRadialBloom(
            canvas = canvas,
            cx = w * (0.24f + 0.10f * cos(slow * 0.52f + 3.1f)),
            cy = h * (0.16f + 0.07f * sin(slow * 0.64f + 1.8f)),
            radius = minDim * 0.80f,
            centerColor = 0x883578E5.toInt(), // Deep azure contrast anchor
            midColor = 0x264D8FF0.toInt(),
        )

        // 3. Sweeping translucent refractive glass ribbons
        drawLightGlassRibbon(
            canvas = canvas,
            w = w,
            h = h,
            baseYFrac = 0.34f,
            ampFrac = 0.055f,
            thicknessFrac = 0.19f,
            phase = slow * 1.15f,
            freq = 1.10f,
            topColor = 0x52FFFFFF,
            midColor = 0x389CEFFF,
            botColor = 0x08FFFFFF,
            crestAlpha = 185,
            dispersionColor = 0x706CF4FF,
        )
        drawLightGlassRibbon(
            canvas = canvas,
            w = w,
            h = h,
            baseYFrac = 0.56f,
            ampFrac = 0.068f,
            thicknessFrac = 0.22f,
            phase = -slow * 0.92f + 1.8f,
            freq = 0.95f,
            topColor = 0x5AFFFFFF,
            midColor = 0x3BD6B8FF,
            botColor = 0x0AFFFFFF,
            crestAlpha = 195,
            dispersionColor = 0x66FFD2A6,
        )
        drawLightGlassRibbon(
            canvas = canvas,
            w = w,
            h = h,
            baseYFrac = 0.77f,
            ampFrac = 0.050f,
            thicknessFrac = 0.20f,
            phase = slow * 0.80f + 3.4f,
            freq = 1.25f,
            topColor = 0x4EFFF8F0,
            midColor = 0x36FFC09E,
            botColor = 0x08FFFFFF,
            crestAlpha = 175,
            dispersionColor = 0x608CEEFF,
        )

        // 4. Shimmering sunlight caustic arcs
        drawCausticFilament(canvas, w, h, yFrac = 0.24f, phase = slow * 1.3f, alpha = 95, minDim = minDim)
        drawCausticFilament(canvas, w, h, yFrac = 0.45f, phase = -slow * 1.05f + 2.1f, alpha = 85, minDim = minDim)
        drawCausticFilament(canvas, w, h, yFrac = 0.67f, phase = slow * 0.95f + 4.0f, alpha = 80, minDim = minDim)

        // 5. Soft sunlit lens sheen at top-left for cohesive glass lighting
        drawRadialBloom(
            canvas = canvas,
            cx = w * (0.14f + 0.05f * sin(slow * 0.5f)),
            cy = h * (0.08f + 0.03f * cos(slow * 0.45f)),
            radius = minDim * 0.65f,
            centerColor = 0x55FFFFFF,
            midColor = 0x18FFFFFF,
        )
    }

    private fun drawLightGlassRibbon(
        canvas: Canvas,
        w: Float,
        h: Float,
        baseYFrac: Float,
        ampFrac: Float,
        thicknessFrac: Float,
        phase: Float,
        freq: Float,
        topColor: Int,
        midColor: Int,
        botColor: Int,
        crestAlpha: Int,
        dispersionColor: Int,
    ) {
        val steps = 40
        val baseY = h * baseYFrac
        val amp = h * ampFrac
        val thickness = h * thicknessFrac

        val crestPath = Path()
        val bodyPath = Path()

        for (i in 0..steps) {
            val u = i.toFloat() / steps
            val x = u * w
            val wave = sin(u * TWO_PI * freq + phase) * 0.68f +
                sin(u * TWO_PI * (freq * 1.85f) - phase * 0.7f) * 0.32f
            val y = baseY + wave * amp
            if (i == 0) {
                crestPath.moveTo(x, y)
                bodyPath.moveTo(x, y)
            } else {
                crestPath.lineTo(x, y)
                bodyPath.lineTo(x, y)
            }
        }

        for (i in steps downTo 0) {
            val u = i.toFloat() / steps
            val x = u * w
            val wave = sin(u * TWO_PI * freq + phase + 0.45f) * 0.62f +
                cos(u * TWO_PI * (freq * 1.4f) - phase * 0.5f) * 0.38f
            val y = baseY + thickness + wave * amp * 0.75f
            bodyPath.lineTo(x, y)
        }
        bodyPath.close()

        val bodyPaint = ditherPaint().apply {
            shader = LinearGradient(
                0f, baseY - amp, 0f, baseY + thickness + amp,
                intArrayOf(topColor, midColor, botColor),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(bodyPath, bodyPaint)

        val minDim = min(w, h)
        // Subtle prism chromatic edge
        val dispPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (minDim * 0.0085f).coerceIn(2.5f, 10f)
            color = dispersionColor
            strokeCap = Paint.Cap.ROUND
        }
        canvas.save()
        canvas.translate(0f, minDim * 0.004f)
        canvas.drawPath(crestPath, dispPaint)
        canvas.restore()

        // Crisp specular white glass crest
        val crestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (minDim * 0.0038f).coerceIn(1.4f, 4.5f)
            shader = LinearGradient(
                0f, baseY, w, baseY,
                intArrayOf(
                    Color.argb((crestAlpha * 0.25f).toInt(), 255, 255, 255),
                    Color.argb(crestAlpha, 255, 255, 255),
                    Color.argb((crestAlpha * 0.75f).toInt(), 255, 255, 255),
                    Color.argb((crestAlpha * 0.20f).toInt(), 255, 255, 255),
                ),
                floatArrayOf(0f, 0.32f, 0.70f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(crestPath, crestPaint)
    }

    private fun drawCausticFilament(
        canvas: Canvas,
        w: Float,
        h: Float,
        yFrac: Float,
        phase: Float,
        alpha: Int,
        minDim: Float,
    ) {
        val steps = 32
        val path = Path()
        val cy = h * yFrac
        val amp = h * 0.028f
        for (i in 0..steps) {
            val u = i.toFloat() / steps
            val x = u * w
            val y = cy + sin(u * TWO_PI * 1.6f + phase) * amp + cos(u * TWO_PI * 2.9f - phase * 0.8f) * amp * 0.35f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (minDim * 0.0028f).coerceIn(1.2f, 3.2f)
            shader = LinearGradient(
                0f, cy, w, cy,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(alpha, 255, 255, 255),
                    Color.argb((alpha * 0.65f).toInt(), 210, 248, 255),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0.05f, 0.38f, 0.72f, 0.96f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(path, paint)
    }

    // =========================================================================
    // 2. LIVE · DARK — "Obsidian Aurora"
    //    Deep OLED midnight anchor with flowing cyan, ultraviolet & magenta
    //    aurora silk curtains. Makes dark Liquid Glass borders gleam like crystal.
    // =========================================================================

    private fun drawObsidianAurora(canvas: Canvas, w: Float, h: Float, t: Float) {
        val minDim = min(w, h)
        val slow = t * 0.26f

        // 1. Deep OLED midnight-obsidian base
        val basePaint = ditherPaint().apply {
            shader = LinearGradient(
                0f, 0f, w * 0.25f, h,
                intArrayOf(
                    0xFF040712.toInt(),
                    0xFF0A1128.toInt(),
                    0xFF090D22.toInt(),
                    0xFF04060E.toInt(),
                ),
                floatArrayOf(0f, 0.36f, 0.72f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, basePaint)

        // 2. Breathing chromatic aurora pools (behind typical widget zones)
        drawRadialBloom(
            canvas = canvas,
            cx = w * (0.76f + 0.16f * cos(slow * 0.78f)),
            cy = h * (0.24f + 0.10f * sin(slow * 0.65f)),
            radius = minDim * 0.95f,
            centerColor = 0xAA00D4FF.toInt(), // Electric cyan
            midColor = 0x3D0088CC.toInt(),
        )
        drawRadialBloom(
            canvas = canvas,
            cx = w * (0.22f + 0.15f * sin(slow * 0.62f + 1.1f)),
            cy = h * (0.46f + 0.11f * cos(slow * 0.54f)),
            radius = minDim * 1.02f,
            centerColor = 0xB87A3BFF.toInt(), // Vivid ultraviolet
            midColor = 0x424B1EC8.toInt(),
        )
        drawRadialBloom(
            canvas = canvas,
            cx = w * (0.72f + 0.14f * sin(slow * 0.58f + 2.8f)),
            cy = h * (0.74f + 0.09f * cos(slow * 0.72f + 0.9f)),
            radius = minDim * 0.90f,
            centerColor = 0x99E03E9D.toInt(), // Neon rose-magenta
            midColor = 0x368A1E68.toInt(),
        )
        drawRadialBloom(
            canvas = canvas,
            cx = w * (0.30f + 0.12f * cos(slow * 0.48f + 4.1f)),
            cy = h * (0.86f + 0.06f * sin(slow * 0.60f)),
            radius = minDim * 0.82f,
            centerColor = 0x881658FF.toInt(), // Royal cobalt
            midColor = 0x2A0F35A8.toInt(),
        )

        // 3. Flowing aurora silk curtains (organic translucent light ribbons)
        drawAuroraCurtain(
            canvas = canvas,
            w = w,
            h = h,
            baseYFrac = 0.27f,
            ampFrac = 0.070f,
            bandHFrac = 0.24f,
            phase = slow * 1.05f,
            freq = 0.95f,
            edgeColor = 0xCC4CF6FF.toInt(),
            bodyTop = 0x7A00D4FF,
            bodyMid = 0x423C5BFF,
            bodyBot = 0x00000000,
        )
        drawAuroraCurtain(
            canvas = canvas,
            w = w,
            h = h,
            baseYFrac = 0.50f,
            ampFrac = 0.082f,
            bandHFrac = 0.26f,
            phase = -slow * 0.88f + 1.9f,
            freq = 0.85f,
            edgeColor = 0xCCBF8BFF.toInt(),
            bodyTop = 0x808A44FF.toInt(),
            bodyMid = 0x42D83C9E,
            bodyBot = 0x00000000,
        )
        drawAuroraCurtain(
            canvas = canvas,
            w = w,
            h = h,
            baseYFrac = 0.72f,
            ampFrac = 0.064f,
            bandHFrac = 0.23f,
            phase = slow * 0.76f + 3.6f,
            freq = 1.15f,
            edgeColor = 0xB8FF78C8.toInt(),
            bodyTop = 0x6CE044A4,
            bodyMid = 0x382068FF,
            bodyBot = 0x00000000,
        )

        // 4. Subtle drifting prism glints for spatial depth
        val glints = arrayOf(
            floatArrayOf(0.18f, 0.20f, 0.16f, 0.55f),
            floatArrayOf(0.84f, 0.42f, 0.14f, 0.72f),
            floatArrayOf(0.28f, 0.68f, 0.18f, 0.48f),
            floatArrayOf(0.74f, 0.86f, 0.15f, 0.64f),
        )
        for (i in glints.indices) {
            val g = glints[i]
            val pulse = 0.55f + 0.45f * sin(slow * (1.2f + i * 0.35f) + i * 1.7f)
            val gx = w * (g[0] + 0.04f * cos(slow * g[3] + i))
            val gy = h * (g[1] + 0.03f * sin(slow * g[3] + i * 2f))
            val alpha = (pulse * 78f).toInt().coerceIn(0, 255)
            drawRadialBloom(
                canvas = canvas,
                cx = gx,
                cy = gy,
                radius = minDim * g[2],
                centerColor = Color.argb(alpha, 196, 240, 255),
                midColor = Color.argb((alpha * 0.25f).toInt(), 120, 175, 255),
            )
        }
    }

    private fun drawAuroraCurtain(
        canvas: Canvas,
        w: Float,
        h: Float,
        baseYFrac: Float,
        ampFrac: Float,
        bandHFrac: Float,
        phase: Float,
        freq: Float,
        edgeColor: Int,
        bodyTop: Int,
        bodyMid: Int,
        bodyBot: Int,
    ) {
        val steps = 40
        val baseY = h * baseYFrac
        val amp = h * ampFrac
        val bandH = h * bandHFrac

        val ridgePath = Path()
        val fillPath = Path()

        for (i in 0..steps) {
            val u = i.toFloat() / steps
            val x = u * w
            val wave = sin(u * TWO_PI * freq + phase) * 0.65f +
                cos(u * TWO_PI * (freq * 1.75f) - phase * 0.65f) * 0.35f
            val y = baseY + wave * amp
            if (i == 0) {
                ridgePath.moveTo(x, y)
                fillPath.moveTo(x, y)
            } else {
                ridgePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        for (i in steps downTo 0) {
            val u = i.toFloat() / steps
            val x = u * w
            val wave = sin(u * TWO_PI * freq + phase + 0.6f) * 0.55f +
                sin(u * TWO_PI * (freq * 1.3f) - phase * 0.4f) * 0.45f
            val y = baseY + bandH + wave * amp * 0.8f
            fillPath.lineTo(x, y)
        }
        fillPath.close()

        val fillPaint = ditherPaint().apply {
            shader = LinearGradient(
                0f, baseY - amp, 0f, baseY + bandH + amp,
                intArrayOf(bodyTop, bodyMid, bodyBot),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(fillPath, fillPaint)

        // Luminous filament crest along the top edge of the curtain
        val minDim = min(w, h)
        val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (minDim * 0.0042f).coerceIn(1.5f, 5f)
            shader = LinearGradient(
                0f, baseY, w, baseY,
                intArrayOf(
                    Color.TRANSPARENT,
                    edgeColor,
                    Color.argb(180, 255, 255, 255),
                    edgeColor,
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.25f, 0.52f, 0.78f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(ridgePath, ridgePaint)
    }

    // =========================================================================
    // 3. STATIC · LIGHT — "Tahoe Prism"
    //    Fluted optical glass ribs + sculpted Tahoe coastal refractive waves.
    //    Built to create rich geometric refraction inside frosted light cards.
    // =========================================================================

    private fun drawTahoePrism(canvas: Canvas, w: Float, h: Float) {
        val minDim = min(w, h)

        // 1. Daylight coastal sky-to-horizon gradient
        val basePaint = ditherPaint().apply {
            shader = LinearGradient(
                0f, 0f, w * 0.30f, h,
                intArrayOf(
                    0xFF428AE0.toInt(), // Clear daylight azure
                    0xFF72B5EE.toInt(), // Soft coastal sky
                    0xFF9EA0F2.toInt(), // Pastel orchid-periwinkle
                    0xFFECB49A.toInt(), // Warm sunlit sand-apricot
                ),
                floatArrayOf(0f, 0.36f, 0.70f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, basePaint)

        // 2. Chromatic light pools for depth
        drawRadialBloom(
            canvas = canvas,
            cx = w * 0.84f,
            cy = h * 0.16f,
            radius = minDim * 0.95f,
            centerColor = 0xAA4CE4DC.toInt(),
            midColor = 0x387CE8F0.toInt(),
        )
        drawRadialBloom(
            canvas = canvas,
            cx = w * 0.16f,
            cy = h * 0.58f,
            radius = minDim * 0.92f,
            centerColor = 0x99B48CFF.toInt(),
            midColor = 0x369482F5.toInt(),
        )
        drawRadialBloom(
            canvas = canvas,
            cx = w * 0.80f,
            cy = h * 0.86f,
            radius = minDim * 0.88f,
            centerColor = 0xB8FFB888.toInt(),
            midColor = 0x40F5A088.toInt(),
        )

        // 3. Fluted optical glass vertical ribs (subtle alternating refraction bands)
        val ribCount = 14
        val ribW = w / ribCount
        for (i in 0 until ribCount) {
            val left = i * ribW
            val right = left + ribW
            val ribPaint = ditherPaint().apply {
                shader = LinearGradient(
                    left, 0f, right, 0f,
                    intArrayOf(
                        0x1CFFFFFF,
                        0x05FFFFFF,
                        0x10283C66,
                        0x24FFFFFF,
                    ),
                    floatArrayOf(0f, 0.42f, 0.78f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(left, 0f, right, h, ribPaint)
        }

        // 4. Sculpted Tahoe refractive glass waves (back to front)
        drawSculptedStaticWave(
            canvas = canvas,
            w = w,
            h = h,
            startY = 0.42f,
            ctrl1X = 0.30f, ctrl1Y = 0.28f,
            ctrl2X = 0.72f, ctrl2Y = 0.50f,
            endY = 0.36f,
            fillColors = intArrayOf(0x8C5FA6EE.toInt(), 0x7A7E8AF0.toInt(), 0x44A592F4.toInt()),
            crestColor = 0xD8FFFFFF.toInt(),
            shadowColor = 0x20223A68,
            minDim = minDim,
        )
        drawSculptedStaticWave(
            canvas = canvas,
            w = w,
            h = h,
            startY = 0.60f,
            ctrl1X = 0.26f, ctrl1Y = 0.72f,
            ctrl2X = 0.68f, ctrl2Y = 0.46f,
            endY = 0.58f,
            fillColors = intArrayOf(0x948794F0.toInt(), 0x86B494F4.toInt(), 0x52E2A6A8.toInt()),
            crestColor = 0xE6FFFFFF.toInt(),
            shadowColor = 0x222A2E62,
            minDim = minDim,
        )
        drawSculptedStaticWave(
            canvas = canvas,
            w = w,
            h = h,
            startY = 0.78f,
            ctrl1X = 0.34f, ctrl1Y = 0.66f,
            ctrl2X = 0.76f, ctrl2Y = 0.88f,
            endY = 0.74f,
            fillColors = intArrayOf(0x9AC8A2E8.toInt(), 0x90ECA896.toInt(), 0x78F5C49E.toInt()),
            crestColor = 0xEEFFFFFF.toInt(),
            shadowColor = 0x1E3C2A44,
            minDim = minDim,
        )

        // 5. Crisp diagonal specular sunbeam across top-left
        val beamPaint = ditherPaint().apply {
            shader = LinearGradient(
                0f, 0f, w * 0.75f, h * 0.42f,
                intArrayOf(0x40FFFFFF, 0x12FFFFFF, Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h * 0.50f, beamPaint)
    }

    private fun drawSculptedStaticWave(
        canvas: Canvas,
        w: Float,
        h: Float,
        startY: Float,
        ctrl1X: Float, ctrl1Y: Float,
        ctrl2X: Float, ctrl2Y: Float,
        endY: Float,
        fillColors: IntArray,
        crestColor: Int,
        shadowColor: Int,
        minDim: Float,
    ) {
        val crest = Path().apply {
            moveTo(0f, h * startY)
            cubicTo(
                w * ctrl1X, h * ctrl1Y,
                w * ctrl2X, h * ctrl2Y,
                w, h * endY,
            )
        }
        val body = Path(crest).apply {
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        // Subtle elevation shadow right behind the glass ridge
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (minDim * 0.018f).coerceIn(5f, 18f)
            color = shadowColor
        }
        canvas.save()
        canvas.translate(0f, -minDim * 0.005f)
        canvas.drawPath(crest, shadowPaint)
        canvas.restore()

        // Translucent prism wave body
        val minY = minOf(startY, ctrl1Y, ctrl2Y, endY) * h
        val fillPaint = ditherPaint().apply {
            shader = LinearGradient(
                0f, minY, w * 0.35f, h,
                fillColors,
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(body, fillPaint)

        // Razor-crisp specular crest line
        val crestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (minDim * 0.0042f).coerceIn(1.5f, 4.8f)
            shader = LinearGradient(
                0f, minY, w, minY,
                intArrayOf(
                    Color.argb(65, 255, 255, 255),
                    crestColor,
                    Color.argb(180, 255, 255, 255),
                    Color.argb(70, 255, 255, 255),
                ),
                floatArrayOf(0f, 0.34f, 0.68f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(crest, crestPaint)
    }

    // =========================================================================
    // 4. STATIC · DARK — "Velvet Ribbon"
    //    OLED-anchored midnight obsidian with sculpted sapphire/amethyst/cyan
    //    beveled glass ribbons and fine architectural prism contour arcs.
    // =========================================================================

    private fun drawVelvetRibbon(canvas: Canvas, w: Float, h: Float) {
        val minDim = min(w, h)

        // 1. Deep velvet OLED midnight base
        val basePaint = ditherPaint().apply {
            shader = LinearGradient(
                0f, 0f, w * 0.25f, h,
                intArrayOf(
                    0xFF03050C.toInt(),
                    0xFF090F22.toInt(),
                    0xFF0C1026.toInt(),
                    0xFF04060E.toInt(),
                ),
                floatArrayOf(0f, 0.35f, 0.68f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, basePaint)

        // 2. Controlled chromatic glow pools (gives Liquid Glass cards inner color)
        drawRadialBloom(
            canvas = canvas,
            cx = w * 0.22f,
            cy = h * 0.26f,
            radius = minDim * 0.90f,
            centerColor = 0xAA1B5EFF.toInt(), // Electric sapphire
            midColor = 0x3A1038B8.toInt(),
        )
        drawRadialBloom(
            canvas = canvas,
            cx = w * 0.80f,
            cy = h * 0.50f,
            radius = minDim * 0.95f,
            centerColor = 0xAA7E3AFF.toInt(), // Royal amethyst
            midColor = 0x3D491DC4.toInt(),
        )
        drawRadialBloom(
            canvas = canvas,
            cx = w * 0.30f,
            cy = h * 0.78f,
            radius = minDim * 0.88f,
            centerColor = 0x9900BFD8.toInt(), // Luminous cyan-teal
            midColor = 0x3400729A.toInt(),
        )

        // 3. Fine architectural prism contour arcs (subtle geometric refraction)
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (minDim * 0.0022f).coerceIn(1f, 2.5f)
        }
        val arcCx = w * 0.88f
        val arcCy = h * 0.18f
        for (i in 1..5) {
            val r = minDim * (0.28f + i * 0.17f)
            val alpha = (44 - i * 6).coerceAtLeast(12)
            arcPaint.color = Color.argb(alpha, 175, 220, 255)
            canvas.drawCircle(arcCx, arcCy, r, arcPaint)
        }

        // 4. Interlocking sculpted dark-glass S-ribbons
        drawDarkSculptedRibbon(
            canvas = canvas,
            w = w,
            h = h,
            topStartY = 0.22f, topC1X = 0.34f, topC1Y = 0.10f, topC2X = 0.68f, topC2Y = 0.34f, topEndY = 0.20f,
            thicknessFrac = 0.18f,
            fillColors = intArrayOf(0xAA1F64FF.toInt(), 0x7A4834CC.toInt(), 0x1A0A1228.toInt()),
            rimColors = intArrayOf(0x3000E5FF, 0xEE8CF6FF.toInt(), 0xCCFFFFFF.toInt(), 0x407C4DFF),
            minDim = minDim,
        )
        drawDarkSculptedRibbon(
            canvas = canvas,
            w = w,
            h = h,
            topStartY = 0.48f, topC1X = 0.28f, topC1Y = 0.62f, topC2X = 0.70f, topC2Y = 0.36f, topEndY = 0.50f,
            thicknessFrac = 0.21f,
            fillColors = intArrayOf(0xB47C3CFF.toInt(), 0x80384AE0.toInt(), 0x1A0B0E24.toInt()),
            rimColors = intArrayOf(0x30C896FF, 0xF0DFC2FF.toInt(), 0xD8FFFFFF.toInt(), 0x4000E5FF),
            minDim = minDim,
        )
        drawDarkSculptedRibbon(
            canvas = canvas,
            w = w,
            h = h,
            topStartY = 0.74f, topC1X = 0.32f, topC1Y = 0.62f, topC2X = 0.72f, topC2Y = 0.86f, topEndY = 0.72f,
            thicknessFrac = 0.20f,
            fillColors = intArrayOf(0xA600BDD8.toInt(), 0x781B54E0.toInt(), 0x18071022.toInt()),
            rimColors = intArrayOf(0x3000E5FF, 0xE89CF8FF.toInt(), 0xC8FFFFFF.toInt(), 0x38667CFF),
            minDim = minDim,
        )

        // 5. Subtle OLED top/bottom vignette framing
        val vignettePaint = ditherPaint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0x4A02040A, Color.TRANSPARENT, Color.TRANSPARENT, 0x5802040A),
                floatArrayOf(0f, 0.16f, 0.84f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, vignettePaint)
    }

    private fun drawDarkSculptedRibbon(
        canvas: Canvas,
        w: Float,
        h: Float,
        topStartY: Float,
        topC1X: Float, topC1Y: Float,
        topC2X: Float, topC2Y: Float,
        topEndY: Float,
        thicknessFrac: Float,
        fillColors: IntArray,
        rimColors: IntArray,
        minDim: Float,
    ) {
        val thickness = h * thicknessFrac
        val topCurve = Path().apply {
            moveTo(0f, h * topStartY)
            cubicTo(
                w * topC1X, h * topC1Y,
                w * topC2X, h * topC2Y,
                w, h * topEndY,
            )
        }
        val ribbon = Path(topCurve).apply {
            lineTo(w, h * topEndY + thickness)
            cubicTo(
                w * topC2X, h * topC2Y + thickness * 1.12f,
                w * topC1X, h * topC1Y + thickness * 0.88f,
                0f, h * topStartY + thickness,
            )
            close()
        }

        // Deep drop-shadow above the beveled ribbon for 3D separation
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (minDim * 0.022f).coerceIn(6f, 20f)
            color = 0x55000000
        }
        canvas.save()
        canvas.translate(0f, -minDim * 0.006f)
        canvas.drawPath(topCurve, shadowPaint)
        canvas.restore()

        val minY = minOf(topStartY, topC1Y, topC2Y, topEndY) * h
        val maxY = maxOf(topStartY, topC1Y, topC2Y, topEndY) * h + thickness
        val fillPaint = ditherPaint().apply {
            shader = LinearGradient(
                0f, minY, w * 0.25f, maxY,
                fillColors,
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(ribbon, fillPaint)

        // Beveled specular rim along the leading edge
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (minDim * 0.0044f).coerceIn(1.5f, 4.8f)
            shader = LinearGradient(
                0f, minY, w, minY,
                rimColors,
                floatArrayOf(0f, 0.35f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(topCurve, rimPaint)
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private fun ditherPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    private fun drawRadialBloom(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        centerColor: Int,
        midColor: Int,
    ) {
        val r = radius.coerceAtLeast(1f)
        val p = ditherPaint().apply {
            shader = RadialGradient(
                cx, cy, r,
                intArrayOf(centerColor, midColor, Color.TRANSPARENT),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, r, p)
    }
}
