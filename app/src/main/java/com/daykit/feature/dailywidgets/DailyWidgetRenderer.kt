package com.daykit.feature.dailywidgets

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Shared high-craft Android Canvas renderers for all Daily Widgets.
 * Used identically by:
 *  - Canvas -> Daily Widgets live previews
 *  - Canvas -> Wallpapers interactive studio
 *  - Live Wallpaper Service (60fps, over frosted user wallpaper photo)
 *  - Android Home Screen AppWidgets
 */
object DailyWidgetRenderer {

    /**
     * Draws a frosted/liquid glass specular rim + subtle inner glow around [rect].
     */
    fun drawLiquidGlassRim(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
        rimAlpha: Int = 185,
        tintColor: Int = Color.argb(72, 16, 22, 34),
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tintColor }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)

        // Subtle top-left specular sheen
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.top + rect.height() * 0.45f,
                Color.argb(46, 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, sheenPaint)

        // Crisp beveled rim stroke
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (min(rect.width(), rect.height()) * 0.012f).coerceIn(1.5f, 4f)
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    Color.argb(rimAlpha, 255, 255, 255),
                    Color.argb((rimAlpha * 0.25f).toInt(), 255, 255, 255),
                    Color.argb((rimAlpha * 0.72f).toInt(), 255, 255, 255),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, rimPaint)
    }

    // -------------------------------------------------------------------------
    // 1. SOLAR ARC (Full Unified Liquid Glass — Square 2×2 & Wide 4×2)
    // -------------------------------------------------------------------------
    fun drawSolarArc(
        canvas: Canvas,
        rect: RectF,
        state: SolarArcState,
        overWallpaperBlur: Boolean = false,
    ) {
        val w = rect.width()
        val h = rect.height()
        val minDim = min(w, h)
        val cr = minDim * 0.22f
        val isWide = (w / h.coerceAtLeast(1f)) >= 1.38f

        canvas.save()
        val clip = Path().apply { addRoundRect(rect, cr, cr, Path.Direction.CW) }
        canvas.clipPath(clip)

        // 1. Unified full-surface Liquid Glass body (no two-tone split at horizon)
        val glassColors = when {
            overWallpaperBlur && state.isDaytime -> intArrayOf(
                Color.argb(68, 224, 236, 250),
                Color.argb(48, 148, 180, 218),
                Color.argb(66, 36, 72, 124),
            )
            overWallpaperBlur && !state.isDaytime -> intArrayOf(
                Color.argb(86, 44, 58, 88),
                Color.argb(68, 22, 32, 54),
                Color.argb(84, 14, 22, 40),
            )
            state.isDaytime -> intArrayOf(
                Color.argb(122, 152, 178, 208),
                Color.argb(106, 92, 138, 192),
                Color.argb(132, 38, 92, 162),
            )
            else -> intArrayOf(
                Color.argb(148, 44, 56, 86),
                Color.argb(134, 24, 34, 58),
                Color.argb(156, 14, 22, 42),
            )
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top,
                rect.left + w * 0.32f, rect.bottom,
                glassColors,
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(rect, bodyPaint)

        // Uniform frosted glass milkiness veil
        val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(if (overWallpaperBlur) 22 else 28, 255, 255, 255)
        }
        canvas.drawRect(rect, veilPaint)

        // Top-left liquid glass specular caustic sheen
        val causticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top,
                rect.left + w * 0.65f, rect.top + h * 0.55f,
                intArrayOf(
                    Color.argb(58, 255, 255, 255),
                    Color.argb(16, 255, 255, 255),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(rect, causticPaint)

        // Subtle bottom-edge internal glass refraction lift
        val bottomRefractionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.bottom,
                rect.left, rect.bottom - h * 0.24f,
                Color.argb(38, 218, 236, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(rect, bottomRefractionPaint)

        // 2. Crisp middle horizon line
        val horizonY = rect.top + h * 0.61f
        val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 255, 255, 255)
            strokeWidth = (minDim * 0.0055f).coerceIn(1.2f, 2.4f)
        }
        canvas.drawLine(rect.left, horizonY, rect.right, horizonY, horizonPaint)

        // 3. Single smooth cosine arch:
        //    trough at x = 0.0 & 1.0, crosses horizon at x = 0.25 & 0.75, single peak at x = 0.50
        val amp = h * 0.135f
        fun curveY(normX: Float): Float {
            val phase = (normX - 0.50f) * (2f * PI.toFloat())
            return horizonY - cos(phase) * amp
        }

        val fullPath = Path()
        val steps = 96
        for (i in 0..steps) {
            val nx = i.toFloat() / steps
            val x = rect.left + nx * w
            val y = curveY(nx)
            if (i == 0) fullPath.moveTo(x, y) else fullPath.lineTo(x, y)
        }

        val strokeW = (minDim * 0.023f).coerceIn(3.0f, 9.0f)

        // Below-horizon segment: translucent dark glass-etched curve
        canvas.save()
        canvas.clipRect(rect.left, horizonY, rect.right, rect.bottom)
        val belowCurvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = Color.argb(if (state.isDaytime) 92 else 112, 14, 24, 42)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(fullPath, belowCurvePaint)
        canvas.restore()

        // Above-horizon segment: luminous frosted-white single arch with soft bloom
        canvas.save()
        canvas.clipRect(rect.left, rect.top, rect.right, horizonY)
        val aboveBloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW * 1.9f
            color = Color.argb(52, 255, 255, 255)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(fullPath, aboveBloomPaint)

        val aboveCurvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = Color.argb(218, 255, 255, 255)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(fullPath, aboveCurvePaint)
        canvas.restore()

        // 4. Radiant Sun / Moon orb positioned along the single arch
        val orbX = rect.left + state.curveProgress * w
        val orbY = curveY(state.curveProgress)
        val orbR = (minDim * 0.036f).coerceIn(5.0f, 16.0f)
        val isAboveHorizon = orbY <= horizonY + orbR * 0.45f

        val outerGlowR = orbR * 2.95f
        val outerGlowColor = if (isAboveHorizon) {
            Color.argb(155, 255, 255, 255)
        } else {
            Color.argb(95, 195, 218, 255)
        }
        val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(orbX, orbY, outerGlowR, outerGlowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(orbX, orbY, outerGlowR, outerGlowPaint)

        val innerHaloR = orbR * 1.5f
        val innerHaloColor = if (isAboveHorizon) {
            Color.argb(235, 255, 255, 255)
        } else {
            Color.argb(155, 224, 236, 255)
        }
        val innerHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(orbX, orbY, innerHaloR, innerHaloColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(orbX, orbY, innerHaloR, innerHaloPaint)

        val orbCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isAboveHorizon) Color.WHITE else Color.argb(205, 225, 236, 255)
        }
        canvas.drawCircle(orbX, orbY, orbR, orbCorePaint)

        // 5. Typography & Sunrise/Sunset Horizon Icon (matches reference layout)
        val padX = if (isWide) h * 0.115f else w * 0.095f
        val primaryLabel = if (state.isDaytime) "SUNSET" else "SUNRISE"
        val primaryTime = if (state.isDaytime) state.sunsetText else state.sunriseText
        val secondaryText = if (state.isDaytime) {
            "Sunrise: ${state.sunriseText}"
        } else {
            "Sunset: ${state.sunsetText}"
        }

        val headerSize = if (isWide) h * 0.078f else h * 0.068f
        val headerBaselineY = rect.top + h * (if (isWide) 0.185f else 0.168f)
        val iconSize = headerSize * 1.32f
        val iconCx = padX + iconSize * 0.52f
        val iconCy = headerBaselineY - headerSize * 0.34f
        drawSolarHorizonIcon(
            canvas = canvas,
            cx = iconCx,
            cy = iconCy,
            size = iconSize,
            isSunset = state.isDaytime,
            color = Color.argb(212, 255, 255, 255),
        )

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(212, 255, 255, 255)
            textSize = headerSize
            letterSpacing = 0.08f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setShadowLayer(minDim * 0.014f, 0f, minDim * 0.004f, Color.argb(55, 0, 0, 0))
        }
        canvas.drawText(primaryLabel, padX + iconSize * 1.28f, headerBaselineY, headerPaint)

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (isWide) h * 0.228f else h * 0.205f
            letterSpacing = -0.02f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(minDim * 0.022f, 0f, minDim * 0.008f, Color.argb(65, 0, 0, 0))
        }
        canvas.drawText(primaryTime, padX - minDim * 0.008f, rect.top + h * (if (isWide) 0.405f else 0.368f), timePaint)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(238, 255, 255, 255)
            textSize = if (isWide) h * 0.084f else h * 0.076f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setShadowLayer(minDim * 0.016f, 0f, minDim * 0.005f, Color.argb(60, 0, 0, 0))
        }
        canvas.drawText(secondaryText, padX, rect.top + h * 0.895f, footerPaint)

        // 6. High-craft Liquid Glass perimeter optics (frosted inner glow + beveled specular rim)
        val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = minDim * 0.052f
            color = Color.argb(if (overWallpaperBlur) 60 else 68, 255, 255, 255)
            maskFilter = BlurMaskFilter(minDim * 0.042f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(rect, cr, cr, innerGlowPaint)

        val rimStroke = (minDim * 0.0088f).coerceIn(1.6f, 3.8f)
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = rimStroke
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    Color.argb(215, 255, 255, 255),
                    Color.argb(65, 255, 255, 255),
                    Color.argb(48, 255, 255, 255),
                    Color.argb(165, 255, 255, 255),
                ),
                floatArrayOf(0f, 0.36f, 0.68f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, cr, cr, rimPaint)

        val innerDepthRect = RectF(
            rect.left + rimStroke,
            rect.top + rimStroke,
            rect.right - rimStroke,
            rect.bottom - rimStroke,
        )
        val innerDepthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (rimStroke * 0.55f).coerceAtLeast(1f)
            color = Color.argb(24, 0, 0, 0)
        }
        canvas.drawRoundRect(innerDepthRect, (cr - rimStroke).coerceAtLeast(0f), (cr - rimStroke).coerceAtLeast(0f), innerDepthPaint)

        canvas.restore()
    }

    /**
     * Minimal iOS-style sunrise / sunset horizon glyph (half-sun, rays, horizon line, directional arrow).
     */
    private fun drawSolarHorizonIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        isSunset: Boolean,
        color: Int,
    ) {
        val stroke = (size * 0.095f).coerceIn(1.2f, 3.2f)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        val horizonY = cy + size * 0.30f
        // Horizon baseline
        canvas.drawLine(cx - size * 0.46f, horizonY, cx + size * 0.46f, horizonY, linePaint)

        // Half-sun dome resting on the horizon
        val sunR = size * 0.21f
        val sunOval = RectF(cx - sunR, horizonY - sunR, cx + sunR, horizonY + sunR)
        canvas.drawArc(sunOval, 180f, 180f, true, fillPaint)

        // Left & right angled rays
        val rayInner = sunR * 1.38f
        val rayOuter = sunR * 1.82f
        val anglesDeg = floatArrayOf(-150f, -30f)
        for (deg in anglesDeg) {
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            canvas.drawLine(
                cx + cos(rad) * rayInner,
                horizonY + sin(rad) * rayInner,
                cx + cos(rad) * rayOuter,
                horizonY + sin(rad) * rayOuter,
                linePaint,
            )
        }

        // Tiny vertical arrow above sun (down for sunset, up for sunrise)
        val arrowTop = cy - size * 0.44f
        val arrowBot = cy - size * 0.14f
        canvas.drawLine(cx, arrowTop, cx, arrowBot, linePaint)
        val head = size * 0.12f
        if (isSunset) {
            canvas.drawLine(cx - head, arrowBot - head * 0.85f, cx, arrowBot, linePaint)
            canvas.drawLine(cx + head, arrowBot - head * 0.85f, cx, arrowBot, linePaint)
        } else {
            canvas.drawLine(cx - head, arrowTop + head * 0.85f, cx, arrowTop, linePaint)
            canvas.drawLine(cx + head, arrowTop + head * 0.85f, cx, arrowTop, linePaint)
        }
    }

    // -------------------------------------------------------------------------
    // 2. RADIAL DIAL CLOCK (Reference Image 2 — Liquid Glass)
    // -------------------------------------------------------------------------
    fun drawRadialDialClock(
        canvas: Canvas,
        rect: RectF,
        nowMillis: Long = System.currentTimeMillis(),
        overWallpaperBlur: Boolean = false,
    ) {
        val w = rect.width()
        val h = rect.height()
        val cr = h * 0.18f

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val dowText = SimpleDateFormat("EEE", Locale.US).format(Date(nowMillis)).uppercase(Locale.US)
        val dayNumText = SimpleDateFormat("dd", Locale.US).format(Date(nowMillis))

        canvas.save()
        val clip = Path().apply { addRoundRect(rect, cr, cr, Path.Direction.CW) }
        canvas.clipPath(clip)

        // Dark translucent liquid-glass base
        val bgAlpha = if (overWallpaperBlur) 130 else 215
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    Color.argb(bgAlpha, 26, 36, 28),
                    Color.argb(bgAlpha, 14, 18, 16),
                    Color.argb(bgAlpha, 20, 26, 22),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(rect, bgPaint)

        // Dial geometry: pivot sits just below the bottom center of the card
        val cx = rect.centerX()
        val cy = rect.bottom + h * 0.14f
        val innerR = h * 0.68f
        val outerR = h * 1.18f
        val wedgeStepDeg = 28f

        // Active center wedge spotlight beam
        val halfStepRad = Math.toRadians((wedgeStepDeg * 0.5).toDouble()).toFloat()
        val beamPath = Path().apply {
            val a1 = -PI.toFloat() / 2f - halfStepRad
            val a2 = -PI.toFloat() / 2f + halfStepRad
            moveTo(cx + cos(a1) * innerR, cy + sin(a1) * innerR)
            lineTo(cx + cos(a1) * outerR, cy + sin(a1) * outerR)
            lineTo(cx + cos(a2) * outerR, cy + sin(a2) * outerR)
            lineTo(cx + cos(a2) * innerR, cy + sin(a2) * innerR)
            close()
        }
        val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx, cy - outerR, cx, cy - innerR,
                intArrayOf(
                    Color.argb(24, 255, 255, 255),
                    Color.argb(72, 235, 255, 190),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(beamPath, beamPaint)

        // Concentric thin dial rings
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (h * 0.008f).coerceAtLeast(1.2f)
            color = Color.argb(65, 255, 255, 255)
        }
        canvas.drawCircle(cx, cy, innerR, ringPaint)
        canvas.drawCircle(cx, cy, innerR * 0.94f, ringPaint.apply { color = Color.argb(35, 255, 255, 255) })

        // Radial spoke dividers between hours (-2..+2)
        val spokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = (h * 0.008f).coerceAtLeast(1.2f)
            color = Color.argb(70, 255, 255, 255)
        }
        for (i in -2..3) {
            val deg = -90f + (i - 0.5f) * wedgeStepDeg
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            canvas.drawLine(
                cx + cos(rad) * innerR,
                cy + sin(rad) * innerR,
                cx + cos(rad) * outerR,
                cy + sin(rad) * outerR,
                spokePaint,
            )
        }

        // Hour numbers around the arc: H-2, H-1, H, H+1, H+2
        val hourRadius = (innerR + outerR) * 0.47f
        for (offset in -2..2) {
            val hVal = ((hour24 + offset) % 24 + 24) % 24
            val label = hVal.toString().padStart(2, '0')
            val angleDeg = offset * wedgeStepDeg
            val isCenter = offset == 0

            val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("monospace", if (isCenter) Typeface.BOLD else Typeface.NORMAL)
                textSize = if (isCenter) h * 0.19f else h * 0.125f
                color = when (kotlin.math.abs(offset)) {
                    0 -> Color.WHITE
                    1 -> Color.argb(195, 235, 240, 235)
                    else -> Color.argb(115, 215, 225, 215)
                }
            }
            canvas.save()
            canvas.rotate(angleDeg, cx, cy)
            canvas.drawText(label, cx, cy - hourRadius + hPaint.textSize * 0.34f, hPaint)
            canvas.restore()
        }

        // Glowing neon-lime active arc directly under current hour
        val arcRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        val sweepStart = -90f - wedgeStepDeg * 0.48f
        val sweepFull = wedgeStepDeg * 0.96f

        val neonGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = (h * 0.068f).coerceAtLeast(6f)
            color = Color.argb(140, 200, 255, 20)
            maskFilter = BlurMaskFilter((h * 0.055f).coerceAtLeast(4f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawArc(arcRect, sweepStart, sweepFull, false, neonGlowPaint)

        val neonCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = (h * 0.036f).coerceAtLeast(3.5f)
            color = 0xFFD9FF1A.toInt()
        }
        canvas.drawArc(arcRect, sweepStart, sweepFull, false, neonCorePaint)

        // Minutes inside the arc
        val minPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = h * 0.20f
            typeface = Typeface.create("monospace", Typeface.BOLD)
        }
        canvas.drawText(minute.toString().padStart(2, '0'), cx, rect.top + h * 0.65f, minPaint)

        // Bottom date label: "SAT" (white) + "21" (muted)
        val dateSize = h * 0.095f
        val dowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 245, 248, 245)
            textSize = dateSize
            typeface = Typeface.create("monospace", Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(135, 210, 220, 210)
            textSize = dateSize
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            letterSpacing = 0.08f
        }
        val wDow = dowPaint.measureText(dowText)
        val wNum = numPaint.measureText(dayNumText)
        val gap = h * 0.02f
        val totalW = wDow + gap + wNum
        val startX = cx - totalW * 0.5f
        val dateY = rect.top + h * 0.89f
        canvas.drawText(dowText, startX, dateY, dowPaint)
        canvas.drawText(dayNumText, startX + wDow + gap, dateY, numPaint)

        drawLiquidGlassRim(canvas, rect, cr, rimAlpha = 135, tintColor = Color.TRANSPARENT)
        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // 3. LIQUID GLASS 7-SEGMENT CLOCK (Reference Image 3 — Liquid Glass)
    // -------------------------------------------------------------------------
    fun drawLiquidGlassClock(
        canvas: Canvas,
        rect: RectF,
        nowMillis: Long = System.currentTimeMillis(),
        overWallpaperBlur: Boolean = false,
    ) {
        val w = rect.width()
        val h = rect.height()
        val cr = h * 0.24f

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)

        val d0 = hour / 10
        val d1 = hour % 10
        val d2 = min / 10
        val d3 = min % 10

        canvas.save()
        val clip = Path().apply { addRoundRect(rect, cr, cr, Path.Direction.CW) }
        canvas.clipPath(clip)

        // Cool sapphire refractive glass fill
        val baseAlpha = if (overWallpaperBlur) 128 else 225
        val glassBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    Color.argb(baseAlpha, 34, 128, 224),
                    Color.argb(baseAlpha, 22, 98, 196),
                    Color.argb(baseAlpha, 30, 118, 218),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(rect, glassBg)

        // Layout 4 digits + colon with generous breathing room
        val padX = w * 0.11f
        val padY = h * 0.21f
        val availW = w - padX * 2f
        val digitH = h - padY * 2f
        val colonW = availW * 0.11f
        val gapW = availW * 0.045f
        val digitW = (availW - colonW - gapW * 2f) / 4f

        val x0 = rect.left + padX
        val x1 = x0 + digitW + gapW
        val xColon = x1 + digitW + colonW * 0.5f
        val x2 = x1 + digitW + colonW
        val x3 = x2 + digitW + gapW

        // Leading zero is dimmed like in the reference image (" 6:00")
        drawSevenSegmentDigit(canvas, x0, rect.top + padY, digitW, digitH, if (d0 == 0) -1 else d0)
        drawSevenSegmentDigit(canvas, x1, rect.top + padY, digitW, digitH, d1)
        drawSevenSegmentDigit(canvas, x2, rect.top + padY, digitW, digitH, d2)
        drawSevenSegmentDigit(canvas, x3, rect.top + padY, digitW, digitH, d3)

        // Beveled glass colon dots
        val dotR = digitH * 0.068f
        val pulseAlpha = if (sec % 2 == 0) 240 else 190
        val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((pulseAlpha * 0.55f).toInt(), 185, 225, 255)
        }
        val dotRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (dotR * 0.22f).coerceAtLeast(1.4f)
            color = Color.argb(pulseAlpha, 225, 245, 255)
        }
        val dotTopY = rect.top + padY + digitH * 0.31f
        val dotBotY = rect.top + padY + digitH * 0.69f
        canvas.drawCircle(xColon, dotTopY, dotR, dotFill)
        canvas.drawCircle(xColon, dotTopY, dotR, dotRim)
        canvas.drawCircle(xColon, dotBotY, dotR, dotFill)
        canvas.drawCircle(xColon, dotBotY, dotR, dotRim)

        drawLiquidGlassRim(canvas, rect, cr, rimAlpha = 210, tintColor = Color.TRANSPARENT)
        canvas.restore()
    }

    private fun drawSevenSegmentDigit(
        canvas: Canvas,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        digit: Int,
    ) {
        // Segment indices: 0=top, 1=topRight, 2=botRight, 3=bottom, 4=botLeft, 5=topLeft, 6=middle
        val mask = when (digit) {
            0 -> intArrayOf(1, 1, 1, 1, 1, 1, 0)
            1 -> intArrayOf(0, 1, 1, 0, 0, 0, 0)
            2 -> intArrayOf(1, 1, 0, 1, 1, 0, 1)
            3 -> intArrayOf(1, 1, 1, 1, 0, 0, 1)
            4 -> intArrayOf(0, 1, 1, 0, 0, 1, 1)
            5 -> intArrayOf(1, 0, 1, 1, 0, 1, 1)
            6 -> intArrayOf(1, 0, 1, 1, 1, 1, 1)
            7 -> intArrayOf(1, 1, 1, 0, 0, 0, 0)
            8 -> intArrayOf(1, 1, 1, 1, 1, 1, 1)
            9 -> intArrayOf(1, 1, 1, 1, 0, 1, 1)
            else -> intArrayOf(0, 0, 0, 0, 0, 0, 0)
        }

        val t = (w * 0.185f).coerceAtLeast(3f) // segment thickness
        val gap = t * 0.28f
        val halfH = h * 0.5f

        val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(32, 200, 232, 255)
        }
        val activeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                left, top, left, top + h,
                Color.argb(145, 210, 238, 255),
                Color.argb(95, 130, 198, 255),
                Shader.TileMode.CLAMP,
            )
        }
        val activeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (t * 0.14f).coerceAtLeast(1.2f)
            color = Color.argb(230, 225, 245, 255)
        }

        fun hSeg(y: Float): Path {
            val x1 = left + t * 0.65f + gap
            val x2 = left + w - t * 0.65f - gap
            val ht = t * 0.5f
            return Path().apply {
                moveTo(x1, y)
                lineTo(x1 + ht, y - ht)
                lineTo(x2 - ht, y - ht)
                lineTo(x2, y)
                lineTo(x2 - ht, y + ht)
                lineTo(x1 + ht, y + ht)
                close()
            }
        }

        fun vSeg(x: Float, y1: Float, y2: Float): Path {
            val ht = t * 0.5f
            val topY = y1 + gap
            val botY = y2 - gap
            return Path().apply {
                moveTo(x, topY)
                lineTo(x + ht, topY + ht)
                lineTo(x + ht, botY - ht)
                lineTo(x, botY)
                lineTo(x - ht, botY - ht)
                lineTo(x - ht, topY + ht)
                close()
            }
        }

        val paths = arrayOf(
            hSeg(top + t * 0.5f),                               // 0: top
            vSeg(left + w - t * 0.5f, top + t * 0.5f, top + halfH), // 1: top-right
            vSeg(left + w - t * 0.5f, top + halfH, top + h - t * 0.5f), // 2: bot-right
            hSeg(top + h - t * 0.5f),                           // 3: bottom
            vSeg(left + t * 0.5f, top + halfH, top + h - t * 0.5f), // 4: bot-left
            vSeg(left + t * 0.5f, top + t * 0.5f, top + halfH), // 5: top-left
            hSeg(top + halfH),                                  // 6: middle
        )

        for (i in 0..6) {
            if (mask[i] == 1) {
                canvas.drawPath(paths[i], activeFill)
                canvas.drawPath(paths[i], activeRim)
            } else {
                canvas.drawPath(paths[i], ghostPaint)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 4. RUN & WALK SESSION TRACKER (Solid Dark — On-demand Start/Stop + Run Bars)
    // -------------------------------------------------------------------------
    fun drawStepsBarChart(
        canvas: Canvas,
        rect: RectF,
        state: StepsChartState,
    ) {
        val w = rect.width()
        val h = rect.height()
        val cr = h * 0.14f

        canvas.save()
        val clip = Path().apply { addRoundRect(rect, cr, cr, Path.Direction.CW) }
        canvas.clipPath(clip)

        // Solid obsidian black card
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0C0B.toInt() }
        canvas.drawRect(rect, bgPaint)

        val padX = w * 0.055f
        val topY = rect.top + h * 0.145f

        // Green footprints icon (two stylized ovals)
        val fpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF39D92A.toInt() }
        val fpSize = h * 0.085f
        canvas.drawOval(padX, topY - fpSize * 0.9f, padX + fpSize * 0.42f, topY - fpSize * 0.15f, fpPaint)
        canvas.drawOval(padX + fpSize * 0.52f, topY - fpSize * 0.65f, padX + fpSize * 0.94f, topY + fpSize * 0.10f, fpPaint)

        // "Steps" title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = h * 0.088f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText("Steps", padX + fpSize * 1.25f, topY, titlePaint)

        // Top-right icon-only Start (▶) / Stop (■) circular button
        val btnR = h * 0.092f
        val btnCx = rect.right - padX - btnR
        val btnCy = rect.top + h * 0.145f
        val btnBgColor = if (state.isRunning) 0xFF361816.toInt() else 0xFF172C1A.toInt()
        val btnAccent = if (state.isRunning) 0xFFFF5A4E.toInt() else 0xFF39D92A.toInt()

        if (state.isRunning) {
            val pulseGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    btnCx, btnCy, btnR * 1.65f,
                    Color.argb(110, 255, 90, 78),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawCircle(btnCx, btnCy, btnR * 1.65f, pulseGlow)
        }

        val btnFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = btnBgColor }
        canvas.drawCircle(btnCx, btnCy, btnR, btnFill)

        val btnStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (h * 0.008f).coerceAtLeast(1.5f)
            color = btnAccent
        }
        canvas.drawCircle(btnCx, btnCy, btnR, btnStroke)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = btnAccent }
        if (state.isRunning) {
            // Rounded Stop square icon
            val sq = btnR * 0.42f
            val sqRect = RectF(btnCx - sq, btnCy - sq, btnCx + sq, btnCy + sq)
            canvas.drawRoundRect(sqRect, sq * 0.28f, sq * 0.28f, iconPaint)
        } else {
            // Crisp Play triangle icon (optically nudged slightly right)
            val triR = btnR * 0.46f
            val playPath = Path().apply {
                moveTo(btnCx - triR * 0.58f, btnCy - triR * 0.85f)
                lineTo(btnCx + triR * 0.92f, btnCy)
                lineTo(btnCx - triR * 0.58f, btnCy + triR * 0.85f)
                close()
            }
            canvas.drawPath(playPath, iconPaint)
        }

        // Big stats row for the selected (or active) run: steps + km + date/time
        val selected = state.selectedSession
        val numFmt = NumberFormat.getIntegerInstance(Locale.US)
        val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = h * 0.118f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(135, 210, 215, 212)
            textSize = h * 0.066f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val statY = rect.top + h * 0.315f
        val statSubY = rect.top + h * 0.398f
        val displaySteps = selected?.steps ?: 0
        val displayKm = selected?.distanceKm ?: 0f

        canvas.drawText(numFmt.format(displaySteps), padX, statY, bigPaint)
        canvas.drawText("steps", padX, statSubY, subPaint)

        val col2X = padX + w * 0.245f
        canvas.drawText(String.format(Locale.US, "%.2f", displayKm), col2X, statY, bigPaint)
        canvas.drawText("km", col2X, statSubY, subPaint)

        // Right-aligned selected run date + time range
        val metaTopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected?.isActive == true) 0xFF8CFF52.toInt() else Color.argb(225, 232, 238, 234)
            textSize = h * 0.068f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val metaBotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 200, 208, 204)
            textSize = h * 0.064f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())
        val rightEdge = rect.right - padX

        if (selected == null) {
            canvas.drawText("No runs yet", rightEdge, statY - h * 0.01f, metaTopPaint)
            canvas.drawText("Tap ▶ to start", rightEdge, statSubY, metaBotPaint)
        } else if (selected.isActive) {
            val startStr = timeFmt.format(Date(selected.startMillis))
            val mins = selected.durationMinutes
            canvas.drawText("● Live · $startStr", rightEdge, statY - h * 0.01f, metaTopPaint)
            canvas.drawText("${mins}m elapsed", rightEdge, statSubY, metaBotPaint)
        } else {
            val dateStr = dateFmt.format(Date(selected.startMillis))
            val rangeStr = "${timeFmt.format(Date(selected.startMillis))}–${timeFmt.format(Date(selected.endMillis))}"
            canvas.drawText(dateStr, rightEdge, statY - h * 0.01f, metaTopPaint)
            canvas.drawText(rangeStr, rightEdge, statSubY, metaBotPaint)
        }

        // Session Bar Chart (7 fixed slots — oldest on left, newest/live on right)
        val totalSlots = state.totalSlots.coerceAtLeast(1)
        val sessions = state.sessions
        val chartLeft = rect.left + padX * 0.85f
        val chartRight = rect.right - padX * 0.85f
        val chartBottom = rect.bottom - h * 0.145f
        val maxBarH = h * 0.40f
        val minBarH = h * 0.15f
        val maxSteps = sessions.maxOfOrNull { it.steps }?.coerceAtLeast(100) ?: 1000

        val slotW = (chartRight - chartLeft) / totalSlots
        val barW = slotW * 0.78f
        val barCr = barW * 0.32f

        val barLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = h * 0.060f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        for (i in 0 until totalSlots) {
            val session = sessions.getOrNull(i)
            val isSelected = session != null && i == state.selectedIndex
            val cx = chartLeft + slotW * (i + 0.5f)
            val bLeft = cx - barW * 0.5f
            val bRight = cx + barW * 0.5f

            val ratio = if (session == null) {
                0.42f + ((i % 3) * 0.12f)
            } else {
                (session.steps.toFloat() / maxSteps.toFloat()).coerceIn(0.24f, 1.0f)
            }
            val barH = minBarH + (maxBarH - minBarH) * ratio
            val bTop = chartBottom - barH
            val barRect = RectF(bLeft, bTop, bRight, chartBottom)

            when {
                isSelected -> {
                    val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(
                            cx, bTop, cx, chartBottom,
                            intArrayOf(0xFFC5FF36.toInt(), 0xFF39D92A.toInt(), 0xFF15AD45.toInt()),
                            floatArrayOf(0f, 0.55f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                    }
                    canvas.drawRoundRect(barRect, barCr, barCr, activePaint)

                    // White glowing dot on top of selected bar
                    val dotR = barW * 0.15f
                    val dotGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(120, 255, 255, 255)
                    }
                    canvas.drawCircle(cx, bTop + dotR * 0.2f, dotR * 1.9f, dotGlow)
                    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                    canvas.drawCircle(cx, bTop + dotR * 0.2f, dotR, dotPaint)

                    // Floating pill badge above selected bar
                    val pillText = numFmt.format(session!!.steps)
                    val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textAlign = Paint.Align.CENTER
                        textSize = h * 0.064f
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    }
                    val tw = pillTextPaint.measureText(pillText)
                    val pillW = tw + h * 0.095f
                    val pillH = h * 0.11f
                    val pillBottom = bTop - h * 0.04f
                    val pillCx = cx.coerceIn(chartLeft + pillW * 0.5f, chartRight - pillW * 0.5f)
                    val pillRect = RectF(pillCx - pillW * 0.5f, pillBottom - pillH, pillCx + pillW * 0.5f, pillBottom)
                    val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF232724.toInt() }
                    canvas.drawRoundRect(pillRect, pillH * 0.5f, pillH * 0.5f, pillBg)
                    canvas.drawText(pillText, pillCx, pillRect.centerY() + pillTextPaint.textSize * 0.35f, pillTextPaint)
                }

                session == null -> {
                    val emptyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF161817.toInt() }
                    val emptyStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5f
                        color = 0xFF282C2A.toInt()
                    }
                    canvas.drawRoundRect(barRect, barCr, barCr, emptyFill)
                    canvas.drawRoundRect(barRect, barCr, barCr, emptyStroke)
                }

                else -> {
                    val pastFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(
                            cx, bTop, cx, chartBottom,
                            0xFF425144.toInt(),
                            0xFF2C362E.toInt(),
                            Shader.TileMode.CLAMP,
                        )
                    }
                    val pastStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 1.2f
                        color = Color.argb(65, 165, 195, 170)
                    }
                    canvas.drawRoundRect(barRect, barCr, barCr, pastFill)
                    canvas.drawRoundRect(barRect, barCr, barCr, pastStroke)
                }
            }

            // Compact time label under each bar
            if (session != null) {
                barLabelPaint.color = if (isSelected) Color.WHITE else Color.argb(185, 215, 222, 218)
                barLabelPaint.typeface = Typeface.create("sans-serif", if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                val label = timeFmt.format(Date(session.startMillis))
                canvas.drawText(label, cx, rect.bottom - h * 0.045f, barLabelPaint)
            } else {
                barLabelPaint.color = Color.argb(75, 190, 196, 192)
                barLabelPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                canvas.drawText("·", cx, rect.bottom - h * 0.045f, barLabelPaint)
            }
        }

        // Subtle outer border
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb(45, 255, 255, 255)
        }
        canvas.drawRoundRect(rect, cr, cr, border)
        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // 5. BENTO BATTERIES (Reference Image 5 — Solid Clean Editorial)
    // -------------------------------------------------------------------------
    fun drawBentoBattery(
        canvas: Canvas,
        rect: RectF,
        state: BatteryState,
        darkSurface: Boolean = false,
    ) {
        val w = rect.width()
        val h = rect.height()
        val cr = min(w, h) * 0.16f

        canvas.save()
        val clip = Path().apply { addRoundRect(rect, cr, cr, Path.Direction.CW) }
        canvas.clipPath(clip)

        val bg = if (darkSurface) 0xFF17191D.toInt() else 0xFFF7F7F5.toInt()
        val fg = if (darkSurface) 0xFFF3F4F6.toInt() else 0xFF14161A.toInt()
        val muted = if (darkSurface) 0xFF8E95A2.toInt() else 0xFF7B808A.toInt()
        val track = if (darkSurface) 0xFF2A2E35.toInt() else 0xFFE6E8EC.toInt()
        val cobalt = 0xFF2952FF.toInt()

        canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })

        val pad = w * 0.10f
        val dotR = w * 0.018f
        canvas.drawCircle(rect.left + pad + dotR, rect.top + pad + dotR * 1.4f, dotR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cobalt })

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = w * 0.068f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText("Batteries", rect.left + pad + dotR * 4.5f, rect.top + pad + dotR * 3.0f, headerPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = w * 0.072f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = w * 0.068f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        val rows = listOf(
            Triple(if (state.isCharging) "Phone ⚡" else "Phone", "${state.levelPercent}%", state.levelPercent / 100f),
            Triple("Temp", "${state.temperatureC.toInt()}°C", ((state.temperatureC - 15f) / 35f).coerceIn(0.15f, 1f)),
            Triple(state.statusLabel, "${state.healthPercent}%", state.healthPercent / 100f),
        )

        val startY = rect.top + h * 0.33f
        val rowGap = h * 0.225f
        val barH = h * 0.036f
        val barLeft = rect.left + pad
        val barRight = rect.right - pad

        rows.forEachIndexed { idx, (label, valText, frac) ->
            val y = startY + idx * rowGap
            canvas.drawText(label, barLeft, y, labelPaint)
            canvas.drawText(valText, barRight, y, pctPaint)

            val barTop = y + h * 0.045f
            val trackRect = RectF(barLeft, barTop, barRight, barTop + barH)
            canvas.drawRoundRect(trackRect, barH * 0.5f, barH * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track })

            val fillW = ((barRight - barLeft) * frac.coerceIn(0.06f, 1f))
            val fillRect = RectF(barLeft, barTop, barLeft + fillW, barTop + barH)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (idx == 0) {
                    shader = LinearGradient(barLeft, barTop, barLeft + fillW, barTop, cobalt, 0xFF5A82FF.toInt(), Shader.TileMode.CLAMP)
                } else {
                    color = fg
                }
            }
            canvas.drawRoundRect(fillRect, barH * 0.5f, barH * 0.5f, fillPaint)
        }

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = if (darkSurface) Color.argb(35, 255, 255, 255) else Color.argb(28, 0, 0, 0)
        }
        canvas.drawRoundRect(rect, cr, cr, border)
        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // 6. BENTO DAY AGENDA (Reference Image 5 — Solid Clean Editorial)
    // -------------------------------------------------------------------------
    fun drawBentoAgenda(
        canvas: Canvas,
        rect: RectF,
        state: AgendaState,
        darkSurface: Boolean = false,
    ) {
        val w = rect.width()
        val h = rect.height()
        val cr = h * 0.16f

        canvas.save()
        val clip = Path().apply { addRoundRect(rect, cr, cr, Path.Direction.CW) }
        canvas.clipPath(clip)

        val bg = if (darkSurface) 0xFF17191D.toInt() else 0xFFF7F7F5.toInt()
        val fg = if (darkSurface) 0xFFF3F4F6.toInt() else 0xFF14161A.toInt()
        val muted = if (darkSurface) 0xFF8E95A2.toInt() else 0xFF7B808A.toInt()
        val divider = if (darkSurface) 0xFF282C33.toInt() else 0xFFE7E8EC.toInt()
        val cobalt = 0xFF2952FF.toInt()

        canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })

        val padX = w * 0.055f
        val padY = h * 0.11f

        // Left column: Day of week, huge date, month, event count
        val dotR = h * 0.018f
        canvas.drawCircle(rect.left + padX + dotR, rect.top + padY + dotR * 1.4f, dotR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cobalt })

        val dowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = h * 0.082f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText(state.dayOfWeek, rect.left + padX + dotR * 4.5f, rect.top + padY + dotR * 3.0f, dowPaint)

        val bigDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = h * 0.42f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        canvas.drawText(state.dayOfMonth, rect.left + padX * 0.9f, rect.top + h * 0.64f, bigDayPaint)

        val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = h * 0.088f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText(state.monthName, rect.left + padX, rect.top + h * 0.80f, monthPaint)

        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = h * 0.068f
        }
        canvas.drawText(state.eventCountLabel, rect.left + padX, rect.top + h * 0.90f, countPaint)

        // Subtle vertical divider
        val divX = rect.left + w * 0.35f
        canvas.drawLine(divX, rect.top + padY, divX, rect.bottom - padY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = divider
            strokeWidth = 1.5f
        })

        // Right column: timeline items
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = h * 0.068f
            typeface = Typeface.create("monospace", Typeface.NORMAL)
        }
        val evTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = h * 0.082f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val evSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = h * 0.066f
        }

        val rightStart = divX + w * 0.045f
        val barX = rightStart + w * 0.135f
        val textX = barX + w * 0.032f
        val rowTop0 = rect.top + h * 0.16f
        val rowStep = h * 0.27f

        state.items.take(3).forEachIndexed { idx, item ->
            val y = rowTop0 + idx * rowStep
            canvas.drawText(item.time, rightStart, y + h * 0.07f, timePaint)

            // Vertical accent bar
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (idx == 0) cobalt else divider
                strokeWidth = (w * 0.007f).coerceAtLeast(2.2f)
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(barX, y, barX, y + h * 0.165f, barPaint)

            canvas.drawText(item.title, textX, y + h * 0.072f, evTitlePaint)
            canvas.drawText(item.subtitle, textX, y + h * 0.155f, evSubPaint)

            // Optional cobalt pill badge on top right of first row
            if (item.badge != null) {
                val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = h * 0.058f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
                val bw = badgeTextPaint.measureText(item.badge) + h * 0.09f
                val bh = h * 0.115f
                val bRight = rect.right - padX * 0.8f
                val bRect = RectF(bRight - bw, y + h * 0.005f, bRight, y + h * 0.005f + bh)
                canvas.drawRoundRect(bRect, bh * 0.5f, bh * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cobalt })
                canvas.drawText(item.badge, bRect.centerX(), bRect.centerY() + badgeTextPaint.textSize * 0.34f, badgeTextPaint)
            }
        }

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = if (darkSurface) Color.argb(35, 255, 255, 255) else Color.argb(28, 0, 0, 0)
        }
        canvas.drawRoundRect(rect, cr, cr, border)
        canvas.restore()
    }

    private fun withAlpha(color: Int, alphaFrac: Float): Int =
        Color.argb((alphaFrac.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
