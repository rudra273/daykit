package com.daykit.feature.wallpaper

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import com.daykit.feature.weather.WeatherPrefs
import com.daykit.feature.weather.WeatherSnapshot
import com.daykit.feature.weather.WeatherSyncWorker
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 60fps Live Wallpaper rendering the transparent frosted-glass weather cube
 * over the user's chosen wallpaper photo (or a dark atmospheric backdrop).
 * Supports "Page 1 only" mode via [Engine.onOffsetsChanged] so the glass cube
 * slides away smoothly when swiping to other home screen pages.
 */
class WeatherGlassWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = WeatherGlassEngine()

    private inner class WeatherGlassEngine : Engine(), Choreographer.FrameCallback {
        private val choreographer = Choreographer.getInstance()
        private var visible = false
        private var surfaceW = 1080
        private var surfaceH = 2400
        private var lastFrameNanos = 0L

        private var pageXOffset = 0f
        private var pageXStep = 0f
        private var firstObservedOffset = Float.NaN
        private var minObservedOffset = 0f
        private var seenOffsetChange = false

        private var snapshot: WeatherSnapshot = WeatherPrefs.loadSnapshot(applicationContext)
        private var useFahrenheit: Boolean = WeatherPrefs.useFahrenheit(applicationContext)
        private var onlyOnFirstPage: Boolean = WeatherPrefs.onlyOnFirstPage(applicationContext)
        private var weatherEnabled: Boolean = WeatherPrefs.wallpaperWeatherEnabled(applicationContext)
        private var cubeCenterX: Float = WeatherPrefs.wallpaperCubeX(applicationContext)
        private var cubeCenterY: Float = WeatherPrefs.wallpaperCubeY(applicationContext)
        private var cubeScale: Float = WeatherPrefs.wallpaperCubeScale(applicationContext)
        private var loadedPhotoVersion: Long = -1L

        private var bgBitmap: Bitmap? = null
        private var bgBlurredBitmap: Bitmap? = null

        private val cloudPuffs: List<Bitmap> = listOf(101, 277, 509).map { createCloudPuff(96, it) }
        private val sim = WallpaperPhysics()

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
        }
        private val tempRect = RectF()
        private val srcRect = Rect()
        private val dstRect = RectF()

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            reloadPrefs()
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            reloadPrefs()
            WeatherPrefs.prefs(applicationContext).registerOnSharedPreferenceChangeListener(prefListener)
            WeatherSyncWorker.schedulePeriodicSync(applicationContext)
        }

        override fun onDestroy() {
            visible = false
            choreographer.removeFrameCallback(this)
            WeatherPrefs.prefs(applicationContext).unregisterOnSharedPreferenceChangeListener(prefListener)
            bgBitmap?.recycle()
            bgBlurredBitmap?.recycle()
            cloudPuffs.forEach { it.recycle() }
            super.onDestroy()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            val frame = holder.surfaceFrame
            if (frame.width() > 0 && frame.height() > 0) {
                surfaceW = frame.width()
                surfaceH = frame.height()
            }
            reloadWallpaperPhotoIfNeeded(force = true)
            drawFrame(0.016f)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceW = width.coerceAtLeast(1)
            surfaceH = height.coerceAtLeast(1)
            reloadWallpaperPhotoIfNeeded(force = true)
            drawFrame(0.016f)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            choreographer.removeFrameCallback(this)
            if (visible) {
                reloadPrefs()
                lastFrameNanos = 0L
                choreographer.postFrameCallback(this)
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            val clamped = if (xOffset.isFinite()) xOffset.coerceIn(0f, 1f) else return
            if (firstObservedOffset.isNaN()) {
                firstObservedOffset = clamped
                minObservedOffset = clamped
            } else {
                if (kotlin.math.abs(clamped - firstObservedOffset) > 0.01f) {
                    seenOffsetChange = true
                }
                if (clamped < minObservedOffset) {
                    minObservedOffset = clamped
                }
            }
            // An offset pinned to an edge (0 or 1) proves the launcher really scrolls
            // the wallpaper (non-scrolling launchers report a constant 0.5).
            if (clamped <= 0.001f || clamped >= 0.999f) {
                seenOffsetChange = true
            }
            pageXOffset = clamped
            // xOffsetStep = 1 / (pages - 1). With exactly 2 pages the step is 1.0,
            // so accept the full (0, 1] range instead of rejecting it.
            pageXStep = if (xOffsetStep.isFinite() && xOffsetStep > 0.005f && xOffsetStep <= 1.0f) {
                xOffsetStep
            } else {
                0f
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!visible) return
            val dt = if (lastFrameNanos == 0L) {
                0.016f
            } else {
                ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            }
            lastFrameNanos = frameTimeNanos
            drawFrame(dt)
            choreographer.postFrameCallback(this)
        }

        private fun reloadPrefs() {
            snapshot = WeatherPrefs.loadSnapshot(applicationContext)
            useFahrenheit = WeatherPrefs.useFahrenheit(applicationContext)
            onlyOnFirstPage = WeatherPrefs.onlyOnFirstPage(applicationContext)
            weatherEnabled = WeatherPrefs.wallpaperWeatherEnabled(applicationContext)
            cubeCenterX = WeatherPrefs.wallpaperCubeX(applicationContext)
            cubeCenterY = WeatherPrefs.wallpaperCubeY(applicationContext)
            cubeScale = WeatherPrefs.wallpaperCubeScale(applicationContext)
            reloadWallpaperPhotoIfNeeded(force = false)
        }

        private fun reloadWallpaperPhotoIfNeeded(force: Boolean) {
            val version = WeatherPrefs.wallpaperPhotoVersion(applicationContext)
            if (!force && version == loadedPhotoVersion) return
            loadedPhotoVersion = version

            bgBitmap?.recycle()
            bgBlurredBitmap?.recycle()
            bgBitmap = null
            bgBlurredBitmap = null

            val file = WeatherPrefs.wallpaperPhotoFile(applicationContext)
            if (!file.exists() || file.length() == 0L) return

            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                if (opts.outWidth <= 0 || opts.outHeight <= 0) return

                val targetW = surfaceW.coerceAtMost(1440)
                val targetH = surfaceH.coerceAtMost(2560)
                var sample = 1
                while (opts.outWidth / (sample * 2) >= targetW && opts.outHeight / (sample * 2) >= targetH) {
                    sample *= 2
                }
                val decoded = BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: return

                bgBitmap = decoded
                bgBlurredBitmap = createSoftBlurredCopy(decoded)
            }
        }

        private fun drawFrame(dt: Float) {
            val holder = surfaceHolder ?: return
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (_: Throwable) {
                try {
                    holder.lockCanvas()
                } catch (_: Throwable) {
                    null
                }
            } ?: return

            try {
                renderScene(canvas, dt)
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Throwable) {
                    // Surface may already be torn down
                }
            }
        }

        private fun renderScene(canvas: Canvas, dt: Float) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val t = snapshot.intensity.coerceIn(0f, 1f)
            val wind = snapshot.windStrength.coerceIn(0f, 1f)

            // 1. Full-screen background (user's chosen photo or sleek atmospheric gradient)
            val photo = bgBitmap
            if (photo != null && !photo.isRecycled) {
                drawCenterCrop(canvas, photo, w, h, bgPaint)
            } else {
                drawDefaultBackdrop(canvas, w, h, t)
            }

            // 2. If weather widget is toggled off for the wallpaper, show just the clean backdrop
            if (!weatherEnabled) return

            // 3. Compute "Page 1 only" slide/fade factor (never hide in preview; works even on launchers that omit xOffsetStep)
            //    Page 1 is always xOffset == 0 (xOffset == 1 for RTL launchers), so measure the
            //    distance from that absolute edge instead of the smallest offset seen so far
            //    (which broke when the wallpaper was applied from any page other than page 1).
            //    Only hide once we know the launcher actually scrolls the wallpaper, otherwise
            //    launchers that report a constant 0.5 would hide the cube everywhere.
            val pageProgress = if (onlyOnFirstPage && !isPreview && seenOffsetChange) {
                val isRtl = resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
                val distFromPage1 = if (isRtl) 1f - pageXOffset else pageXOffset
                val step = if (pageXStep > 0f) pageXStep else 0.25f
                (distFromPage1 / step).coerceIn(0f, 1.4f)
            } else {
                0f
            }
            val cubeAlpha = (1f - pageProgress * 1.15f).coerceIn(0f, 1f)
            if (cubeAlpha <= 0.01f) return

            // 3. Position & size the rounded-square frosted-glass weather cube using user's custom layout
            val maxCardSize = min(w * 0.92f, h * 0.68f).coerceAtLeast(220f)
            val cardSize = (w * cubeScale).coerceIn(220f, maxCardSize)
            val cornerRadius = cardSize * 0.22f
            val slideOffsetX = -pageProgress * w * 0.75f
            val marginX = w * 0.03f
            val marginY = h * 0.04f
            val baseLeft = (w * cubeCenterX - cardSize * 0.5f)
                .coerceIn(marginX, (w - cardSize - marginX).coerceAtLeast(marginX))
            val cardLeft = baseLeft + slideOffsetX
            val cardTop = (h * cubeCenterY - cardSize * 0.5f)
                .coerceIn(marginY, (h - cardSize - marginY).coerceAtLeast(marginY))
            val cardRect = RectF(cardLeft, cardTop, cardLeft + cardSize, cardTop + cardSize)

            sim.updateBounds(cardSize, cardSize, cornerRadius)
            sim.step(dt, intensity = t, windStrength = wind)

            val layerSave = if (cubeAlpha < 0.99f) {
                canvas.saveLayerAlpha(
                    cardLeft - 48f,
                    cardTop - 48f,
                    cardRect.right + 48f,
                    cardRect.bottom + 48f,
                    (cubeAlpha * 255).toInt(),
                )
            } else {
                canvas.save()
            }

            val clipPath = Path().apply {
                addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)

            // 3a. Pixel-aligned frosted blur of the user's wallpaper photo inside the cube
            val blurred = bgBlurredBitmap
            if (blurred != null && !blurred.isRecycled) {
                drawCenterCrop(canvas, blurred, w, h, bgPaint)
            }

            // Translate into cube-local coordinates (0..cardSize)
            canvas.save()
            canvas.translate(cardLeft, cardTop)

            drawAtmosphericSky(canvas, cardSize, t)
            drawClouds(canvas, cardSize, t)
            drawRainAndSplashes(canvas, cardSize, t)
            drawGlassOptics(canvas, cardSize, cornerRadius, t)
            drawCubeTextAndIcon(canvas, cardSize, t, wind)

            canvas.restore()
            canvas.restoreToCount(layerSave)
        }

        private fun drawCenterCrop(canvas: Canvas, bmp: Bitmap, viewW: Float, viewH: Float, paint: Paint) {
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val scale = max(viewW / bw, viewH / bh)
            val scaledW = bw * scale
            val scaledH = bh * scale
            val left = (viewW - scaledW) * 0.5f
            val top = (viewH - scaledH) * 0.5f
            srcRect.set(0, 0, bmp.width, bmp.height)
            dstRect.set(left, top, left + scaledW, top + scaledH)
            canvas.drawBitmap(bmp, srcRect, dstRect, paint)
        }

        private fun drawDefaultBackdrop(canvas: Canvas, w: Float, h: Float, t: Float) {
            val top = lerpColor(0xFF102238.toInt(), 0xFF0A0E14.toInt(), t)
            val mid = lerpColor(0xFF182C44.toInt(), 0xFF12171F.toInt(), t)
            val bot = lerpColor(0xFF0C141E.toInt(), 0xFF07090D.toInt(), t)
            val p = Paint(Paint.DITHER_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(top, mid, bot),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, w, h, p)
        }

        private fun drawAtmosphericSky(canvas: Canvas, s: Float, t: Float) {
            val skyTop = when {
                t < 0.33f -> lerpColor(0xFF3E9BFF.toInt(), 0xFF6B7987.toInt(), t / 0.33f)
                t < 0.66f -> lerpColor(0xFF6B7987.toInt(), 0xFF28313B.toInt(), (t - 0.33f) / 0.33f)
                else -> lerpColor(0xFF28313B.toInt(), 0xFF10151C.toInt(), (t - 0.66f) / 0.34f)
            }
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, s,
                    intArrayOf(
                        withAlpha(skyTop, (lerpF(0.28f, 0.74f, t) * 255).toInt()),
                        withAlpha(0xFF141B24.toInt(), (lerpF(0.10f, 0.42f, t) * 255).toInt()),
                        withAlpha(0xFF0B1017.toInt(), (lerpF(0.10f, 0.32f, t) * 255).toInt()),
                        withAlpha(0xFFD8E4F0.toInt(), (lerpF(0.06f, 0.25f, t) * 255).toInt()),
                    ),
                    floatArrayOf(0f, 0.40f, 0.78f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, s, s, p)
        }

        private fun drawClouds(canvas: Canvas, s: Float, t: Float) {
            val highlight = lerpColor(0xFFFFFFFF.toInt(), 0xFF9AA4AF.toInt(), t)
            val mid = lerpColor(0xFFE9EEF5.toInt(), 0xFF1D232A.toInt(), t)
            val shadow = lerpColor(0xFF9EB0C4.toInt(), 0xFF070A0E.toInt(), t)

            for (i in 0 until sim.clouds.size) {
                val c = sim.clouds[i]
                val vis = smoothstep(c.minIntensity - 0.14f, c.minIntensity + 0.18f, t)
                if (vis <= 0.01f) continue

                val bmp = cloudPuffs[c.variant % cloudPuffs.size]
                val color = when (c.layer) {
                    0 -> shadow
                    1 -> mid
                    else -> highlight
                }
                val alpha = when (c.layer) {
                    0 -> lerpF(0.28f, 0.88f, t)
                    1 -> lerpF(0.74f, 0.84f, t)
                    else -> lerpF(0.92f, 0.66f, t)
                } * c.alphaScale * vis

                val cx = c.x * s
                val cy = (c.y + sin(sim.elapsed * c.bobSpeed + c.phase) * 0.009f) * s
                val rx = c.scaleX * s * lerpF(0.88f, 1.18f, t)
                val ry = c.scaleY * s * lerpF(0.86f, 1.16f, t)

                tempRect.set(cx - rx, cy - ry, cx + rx, cy + ry)
                cloudPaint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                cloudPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
                canvas.drawBitmap(bmp, null, tempRect, cloudPaint)
            }
        }

        private fun drawRainAndSplashes(canvas: Canvas, s: Float, t: Float) {
            val rainFactor = smoothstep(0.06f, 1.0f, t)
            if (rainFactor <= 0.005f) return

            val mistH = s * lerpF(0.10f, 0.24f, rainFactor)
            val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, s - mistH, 0f, s,
                    Color.TRANSPARENT,
                    Color.argb((0.24f * rainFactor * 255).toInt(), 255, 255, 255),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, s - mistH, s, s, mistPaint)

            for (i in 0 until sim.raindrops.size) {
                val drop = sim.raindrops[i]
                if (!drop.active) continue
                val emerge = smoothstep(drop.spawnY, drop.spawnY + s * 0.09f, drop.y)
                val alpha = (drop.alpha * emerge * rainFactor).coerceIn(0f, 0.92f)
                if (alpha <= 0.01f) continue

                val speed = hypot(drop.vx.toDouble(), drop.vy.toDouble()).toFloat().coerceAtLeast(1f)
                val tailX = drop.x - (drop.vx / speed) * drop.length
                val tailY = drop.y - (drop.vy / speed) * drop.length

                streakPaint.color = Color.argb((alpha * 0.42f * 255).toInt(), 242, 247, 255)
                streakPaint.strokeWidth = drop.thickness * 1.45f
                canvas.drawLine(tailX, tailY, drop.x, drop.y, streakPaint)

                streakPaint.color = Color.argb((alpha * 0.92f * 255).toInt(), 255, 255, 255)
                streakPaint.strokeWidth = drop.thickness
                canvas.drawLine(lerpF(tailX, drop.x, 0.45f), lerpF(tailY, drop.y, 0.45f), drop.x, drop.y, streakPaint)
            }

            for (i in 0 until sim.ripples.size) {
                val rip = sim.ripples[i]
                if (!rip.active) continue
                val frac = (rip.age / rip.maxAge).coerceIn(0f, 1f)
                val alpha = ((1f - frac) * 0.55f * rainFactor * 255).toInt().coerceIn(0, 160)
                val rx = lerpF(2.2f, rip.maxRadius, frac)
                val ry = rx * 0.34f
                ripplePaint.color = Color.argb(alpha, 255, 255, 255)
                canvas.drawOval(rip.x - rx, rip.y - ry, rip.x + rx, rip.y + ry, ripplePaint)
            }

            for (i in 0 until sim.splashes.size) {
                val p = sim.splashes[i]
                if (!p.active) continue
                val frac = (p.age / p.maxAge).coerceIn(0f, 1f)
                val alpha = ((1f - frac * frac) * p.alpha * rainFactor).coerceIn(0f, 0.95f)
                if (alpha <= 0.01f) continue
                dotPaint.color = Color.argb((alpha * 255).toInt(), 255, 255, 255)
                canvas.drawCircle(p.x, p.y, p.radius, dotPaint)
            }

            for (i in 0 until sim.beads.size) {
                val b = sim.beads[i]
                val vis = smoothstep(b.minIntensity, b.minIntensity + 0.25f, t)
                if (vis <= 0.01f) continue
                val shimmer = 0.62f + 0.38f * sin(sim.elapsed * b.shimmerSpeed + b.phase)
                val x = b.normX * s
                val y = sim.bottomFloorY(x) - b.heightOffset * lerpF(0.45f, 1.35f, rainFactor)
                val alpha = (b.alpha * shimmer * vis).coerceIn(0f, 0.92f)
                dotPaint.color = Color.argb((alpha * 255).toInt(), 255, 255, 255)
                canvas.drawCircle(x, y, b.radius * lerpF(0.75f, 1.15f, rainFactor), dotPaint)
            }
        }

        private fun drawGlassOptics(canvas: Canvas, s: Float, cr: Float, t: Float) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = s * 0.055f
                color = Color.argb((lerpF(0.26f, 0.20f, t) * 255).toInt(), 255, 255, 255)
                maskFilter = BlurMaskFilter(s * 0.045f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(0f, 0f, s, s, cr, cr, glowPaint)

            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = s * 0.008f
                shader = LinearGradient(
                    0f, 0f, s, s,
                    intArrayOf(
                        Color.argb(185, 255, 255, 255),
                        Color.argb(60, 255, 255, 255),
                        Color.argb(145, 255, 255, 255),
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRoundRect(0f, 0f, s, s, cr, cr, rimPaint)
        }

        private fun drawCubeTextAndIcon(canvas: Canvas, s: Float, t: Float, wind: Float) {
            val padX = s * 0.11f
            fun tp(sizeRatio: Float, bold: Boolean, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 255, 255, 255)
                textSize = s * sizeRatio
                typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setShadowLayer(s * 0.02f, 0f, s * 0.008f, Color.argb(95, 0, 0, 0))
            }

            val temp = snapshot.displayTemp(useFahrenheit)
            val high = snapshot.displayHigh(useFahrenheit)
            val low = snapshot.displayLow(useFahrenheit)
            val unit = snapshot.unitSymbol(useFahrenheit)

            canvas.drawText(snapshot.city, padX, s * 0.205f, tp(0.078f, bold = false, alpha = 242))
            canvas.drawText("$temp$unit", padX - s * 0.008f, s * 0.415f, tp(0.212f, bold = true))

            val iconSize = s * 0.135f
            val iconTop = s * 0.545f
            if (t < 0.22f) {
                val cx = padX + iconSize * 0.45f
                val cy = iconTop + iconSize * 0.48f
                val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFB81C.toInt()
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = iconSize * 0.09f
                }
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        cx, cy, iconSize * 0.6f,
                        Color.argb(115, 255, 202, 40),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawCircle(cx, cy, iconSize * 0.6f, glow)
                val rot = sim.elapsed * 0.24f
                for (i in 0 until 8) {
                    val a = rot + i * (PI.toFloat() / 4f)
                    canvas.drawLine(
                        cx + cos(a) * iconSize * 0.34f,
                        cy + sin(a) * iconSize * 0.34f,
                        cx + cos(a) * iconSize * 0.47f,
                        cy + sin(a) * iconSize * 0.47f,
                        sunPaint,
                    )
                }
                canvas.drawCircle(cx, cy, iconSize * 0.24f, sunPaint)
            } else {
                val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    strokeCap = Paint.Cap.ROUND
                }
                val baseY = iconTop + iconSize * 0.56f
                canvas.drawCircle(padX + iconSize * 0.27f, baseY - iconSize * 0.14f, iconSize * 0.16f, cp)
                canvas.drawCircle(padX + iconSize * 0.48f, baseY - iconSize * 0.22f, iconSize * 0.23f, cp)
                canvas.drawCircle(padX + iconSize * 0.70f, baseY - iconSize * 0.15f, iconSize * 0.17f, cp)
                canvas.drawRoundRect(
                    padX + iconSize * 0.14f,
                    baseY - iconSize * 0.24f,
                    padX + iconSize * 0.83f,
                    baseY,
                    iconSize * 0.12f,
                    iconSize * 0.12f,
                    cp,
                )
                val drops = if (t < 0.52f) 3 else 4
                val dashLen = iconSize * lerpF(0.11f, 0.19f, t)
                val slant = dashLen * lerpF(0.16f, 0.35f, wind)
                val speed = lerpF(1.6f, 3.6f, t)
                cp.strokeWidth = iconSize * 0.08f
                for (i in 0 until drops) {
                    val frac = i.toFloat() / (drops - 1)
                    val x = padX + iconSize * lerpF(0.24f, 0.72f, frac)
                    val phase = (sim.elapsed * speed + i * 0.31f) % 1f
                    val y = baseY + iconSize * 0.06f + phase * iconSize * 0.18f
                    cp.alpha = (sin(phase * PI.toFloat()).coerceIn(0.25f, 1f) * 255).toInt()
                    canvas.drawLine(x, y, x - slant, y + dashLen, cp)
                }
            }

            canvas.drawText(snapshot.conditionLabel, padX, s * 0.775f, tp(0.082f, bold = true))
            canvas.drawText("H:${high}°  L:${low}°", padX, s * 0.885f, tp(0.068f, bold = false, alpha = 232))
        }
    }
}

private fun createSoftBlurredCopy(src: Bitmap): Bitmap {
    // Fast frosted-glass approximation: downsample smoothly to small buffer then box-blur twice.
    val smallW = (src.width / 10).coerceIn(48, 140)
    val smallH = (src.height / 10).coerceIn(48, 220)
    val scaled = Bitmap.createScaledBitmap(src, smallW, smallH, true)
    val pixels = IntArray(smallW * smallH)
    val temp = IntArray(smallW * smallH)
    scaled.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)

    repeat(2) {
        boxBlurHorizontal(pixels, temp, smallW, smallH, radius = 3)
        boxBlurVertical(temp, pixels, smallW, smallH, radius = 3)
    }

    val out = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
    out.setPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
    if (scaled != src) scaled.recycle()
    return out
}

private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val div = radius * 2 + 1
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            for (k in -radius..radius) {
                val px = src[row + (x + k).coerceIn(0, w - 1)]
                r += (px shr 16) and 0xFF
                g += (px shr 8) and 0xFF
                b += px and 0xFF
            }
            dst[row + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
        }
    }
}

private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val div = radius * 2 + 1
    for (x in 0 until w) {
        for (y in 0 until h) {
            var r = 0
            var g = 0
            var b = 0
            for (k in -radius..radius) {
                val py = (y + k).coerceIn(0, h - 1)
                val px = src[py * w + x]
                r += (px shr 16) and 0xFF
                g += (px shr 8) and 0xFF
                b += px and 0xFF
            }
            dst[y * w + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
        }
    }
}

private fun createCloudPuff(size: Int, seed: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    val half = size * 0.5f
    for (y in 0 until size) {
        val ny = (y - half) / half
        for (x in 0 until size) {
            val nx = (x - half) / half
            val dist = sqrt(nx * nx + ny * ny)
            if (dist >= 1f) continue
            val fx = (x.toFloat() / size) * 4.0f
            val fy = (y.toFloat() / size) * 4.0f
            val n = noise(fx, fy, seed) * 0.58f +
                noise(fx * 2f + 9.3f, fy * 2f + 5.1f, seed + 17) * 0.28f +
                noise(fx * 4f + 19.7f, fy * 4f + 13.4f, seed + 43) * 0.14f
            val radial = smoothstep(1.0f, 0.18f, dist)
            val d = ((radial * (0.58f + 0.72f * n)) - 0.14f).coerceIn(0f, 1f)
            val a = (d * d * 255f).toInt().coerceIn(0, 255)
            pixels[y * size + x] = (a shl 24) or 0x00FFFFFF
        }
    }
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    return bmp
}

private fun noise(x: Float, y: Float, seed: Int): Float {
    val xi = floor(x).toInt()
    val yi = floor(y).toInt()
    val xf = x - xi
    val yf = y - yi
    val u = xf * xf * (3f - 2f * xf)
    val v = yf * yf * (3f - 2f * yf)
    val a = hash(xi, yi, seed)
    val b = hash(xi + 1, yi, seed)
    val c = hash(xi, yi + 1, seed)
    val d = hash(xi + 1, yi + 1, seed)
    return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v
}

private fun hash(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()
}

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val v = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return v * v * (3f - 2f * v)
}

private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    val a = lerpF(Color.alpha(c1).toFloat(), Color.alpha(c2).toFloat(), f).roundToInt()
    val r = lerpF(Color.red(c1).toFloat(), Color.red(c2).toFloat(), f).roundToInt()
    val g = lerpF(Color.green(c1).toFloat(), Color.green(c2).toFloat(), f).roundToInt()
    val b = lerpF(Color.blue(c1).toFloat(), Color.blue(c2).toFloat(), f).roundToInt()
    return Color.argb(a, r, g, b)
}

private fun withAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

private class WpCloud(
    var x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val speed: Float,
    val layer: Int,
    val variant: Int,
    val minIntensity: Float,
    val alphaScale: Float,
    val bobSpeed: Float,
    val phase: Float,
)

private class WpDrop {
    var active = false
    var x = 0f
    var y = 0f
    var spawnY = 0f
    var vx = 0f
    var vy = 0f
    var length = 22f
    var thickness = 1.6f
    var alpha = 0.7f
    var depth = 1f
}

private class WpSplash {
    var active = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var radius = 1.6f
    var alpha = 0.85f
    var age = 0f
    var maxAge = 0.45f
    var bounced = false
}

private class WpRipple {
    var active = false
    var x = 0f
    var y = 0f
    var age = 0f
    var maxAge = 0.25f
    var maxRadius = 10f
}

private class WpBead(
    val normX: Float,
    val heightOffset: Float,
    val radius: Float,
    val alpha: Float,
    val minIntensity: Float,
    val shimmerSpeed: Float,
    val phase: Float,
)

private class WallpaperPhysics {
    private val rng = Random(42)
    var width = 600f
        private set
    var height = 600f
        private set
    var cornerRadius = 120f
        private set
    var elapsed = 0f
        private set

    val clouds: Array<WpCloud>
    val raindrops = Array(150) { WpDrop() }
    val splashes = Array(200) { WpSplash() }
    val ripples = Array(28) { WpRipple() }
    val beads: Array<WpBead>

    private var rainAcc = 0f
    private var ambientAcc = 0f
    private var splashCursor = 0
    private var rippleCursor = 0

    init {
        val list = mutableListOf<WpCloud>()
        val under = arrayOf(
            floatArrayOf(0.20f, 0.22f, 0.34f, 0.22f, 0.20f),
            floatArrayOf(0.48f, 0.24f, 0.38f, 0.24f, 0.16f),
            floatArrayOf(0.75f, 0.23f, 0.36f, 0.24f, 0.00f),
            floatArrayOf(0.34f, 0.29f, 0.34f, 0.20f, 0.45f),
            floatArrayOf(0.66f, 0.30f, 0.36f, 0.21f, 0.40f),
        )
        under.forEachIndexed { idx, s ->
            list += WpCloud(s[0], s[1], s[2], s[3], 0.015f + (idx % 3) * 0.005f, 0, idx, s[4], 0.92f, 0.45f + idx * 0.1f, idx * 1.2f)
        }
        val mid = arrayOf(
            floatArrayOf(0.16f, 0.12f, 0.32f, 0.22f, 0.22f),
            floatArrayOf(0.42f, 0.14f, 0.35f, 0.23f, 0.18f),
            floatArrayOf(0.64f, 0.15f, 0.34f, 0.24f, 0.00f),
            floatArrayOf(0.82f, 0.17f, 0.32f, 0.23f, 0.00f),
            floatArrayOf(0.54f, 0.21f, 0.35f, 0.22f, 0.32f),
        )
        mid.forEachIndexed { idx, s ->
            list += WpCloud(s[0], s[1], s[2], s[3], 0.020f + (idx % 3) * 0.005f, 1, idx + 1, s[4], 0.90f, 0.55f + idx * 0.1f, idx * 0.9f)
        }
        val top = arrayOf(
            floatArrayOf(0.55f, 0.14f, 0.22f, 0.17f, 0.00f),
            floatArrayOf(0.70f, 0.11f, 0.26f, 0.20f, 0.00f),
            floatArrayOf(0.82f, 0.15f, 0.21f, 0.16f, 0.00f),
            floatArrayOf(0.24f, 0.08f, 0.28f, 0.19f, 0.20f),
            floatArrayOf(0.46f, 0.07f, 0.30f, 0.19f, 0.25f),
        )
        top.forEachIndexed { idx, s ->
            list += WpCloud(s[0], s[1], s[2], s[3], 0.024f + (idx % 3) * 0.006f, 2, idx + 2, s[4], 0.96f, 0.65f + idx * 0.1f, idx * 1.6f)
        }
        clouds = list.toTypedArray()

        beads = Array(80) { i ->
            val bias = rng.nextFloat() * rng.nextFloat()
            WpBead(
                normX = 0.06f + rng.nextFloat() * 0.88f,
                heightOffset = 4f + bias * 44f,
                radius = 1.4f + rng.nextFloat() * 2.6f,
                alpha = 0.35f + rng.nextFloat() * 0.60f,
                minIntensity = 0.16f + (i.toFloat() / 80f) * 0.62f,
                shimmerSpeed = 2.0f + rng.nextFloat() * 4.0f,
                phase = rng.nextFloat() * (PI.toFloat() * 2f),
            )
        }
    }

    fun updateBounds(w: Float, h: Float, r: Float) {
        width = w.coerceAtLeast(1f)
        height = h.coerceAtLeast(1f)
        cornerRadius = r.coerceIn(1f, min(w, h) * 0.5f)
    }

    fun bottomFloorY(x: Float): Float {
        val r = cornerRadius
        val cx = x.coerceIn(0f, width)
        return when {
            cx < r -> {
                val dx = r - cx
                height - r + sqrt((r * r - dx * dx).coerceAtLeast(0f))
            }
            cx > width - r -> {
                val dx = cx - (width - r)
                height - r + sqrt((r * r - dx * dx).coerceAtLeast(0f))
            }
            else -> height
        }
    }

    fun step(dt: Float, intensity: Float, windStrength: Float) {
        elapsed += dt
        val rainFactor = smoothstep(0.08f, 1.0f, intensity)

        val drift = 0.35f + windStrength * 2.5f
        for (c in clouds) {
            c.x += c.speed * drift * dt
            if (c.x > 1.38f) c.x -= 1.76f
        }

        if (rainFactor > 0.01f) {
            val rate = lerpF(14f, 210f, rainFactor * rainFactor * 0.65f + rainFactor * 0.35f)
            rainAcc += rate * dt
            val count = rainAcc.toInt().coerceAtMost(12)
            rainAcc -= count
            repeat(count) { spawnDrop(rainFactor, windStrength) }

            val ambRate = lerpF(4f, 52f, rainFactor * rainFactor)
            ambientAcc += ambRate * dt
            val ambCount = ambientAcc.toInt().coerceAtMost(4)
            ambientAcc -= ambCount
            repeat(ambCount) {
                val rx = width * (0.08f + rng.nextFloat() * 0.84f)
                spawnSplash(rx, bottomFloorY(rx) - 2f, rainFactor, windStrength, isAmbient = true)
            }
        } else {
            rainAcc = 0f
            ambientAcc = 0f
        }

        for (d in raindrops) {
            if (!d.active) continue
            val targetVx = (windStrength * 1.15f - 0.10f) * d.vy * 0.42f
            d.vx += (targetVx - d.vx) * (dt * 7f).coerceAtMost(1f)
            d.x += d.vx * dt
            d.y += d.vy * dt
            if (d.x < -width * 0.25f || d.x > width * 1.25f) {
                d.active = false
                continue
            }
            val floorY = bottomFloorY(d.x)
            if (d.y >= floorY - 2f) {
                d.active = false
                if (rainFactor > 0.05f && d.x in 4f..(width - 4f)) {
                    val burst = if (rainFactor < 0.35f) 1 else if (rainFactor < 0.7f) 2 else 3
                    repeat(burst) { spawnSplash(d.x, floorY - 2f, rainFactor, windStrength, false, d.depth) }
                    if (d.depth > 0.62f && rng.nextFloat() < 0.4f) {
                        val rip = ripples[rippleCursor++ % ripples.size]
                        rip.active = true
                        rip.x = d.x
                        rip.y = floorY - 2f
                        rip.age = 0f
                        rip.maxAge = 0.20f + rng.nextFloat() * 0.14f
                        rip.maxRadius = 8f + rng.nextFloat() * 11f * rainFactor
                    }
                }
            }
        }

        val gravity = height * 1.18f
        val windPush = (windStrength - 0.15f) * width * 0.45f
        for (p in splashes) {
            if (!p.active) continue
            p.age += dt
            if (p.age >= p.maxAge) {
                p.active = false
                continue
            }
            p.vx += (windPush * 0.35f - p.vx * 1.4f) * dt
            p.vy += gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            val floorY = bottomFloorY(p.x)
            if (p.y >= floorY - p.radius) {
                p.y = floorY - p.radius
                if (!p.bounced && p.vy > 45f) {
                    p.vy = -p.vy * 0.30f
                    p.vx *= 0.65f
                    p.bounced = true
                } else {
                    p.vy = 0f
                    p.vx *= 0.85f
                }
            }
        }

        for (r in ripples) {
            if (!r.active) continue
            r.age += dt
            if (r.age >= r.maxAge) r.active = false
        }
    }

    private fun spawnDrop(rainFactor: Float, wind: Float) {
        val drop = raindrops.firstOrNull { !it.active } ?: return
        val depth = 0.35f + rng.nextFloat() * 0.65f
        val vy = height * lerpF(0.95f, 1.85f, rainFactor) * (0.62f + 0.56f * depth)
        val vx = (wind * 1.15f - 0.10f) * vy * 0.42f
        val upwind = max(0f, vx / vy * height * 0.85f)
        drop.active = true
        drop.x = rng.nextFloat() * (width + upwind * 1.2f) - upwind
        drop.y = height * (0.04f + rng.nextFloat() * 0.19f)
        drop.spawnY = drop.y
        drop.vx = vx
        drop.vy = vy
        drop.depth = depth
        drop.length = height * lerpF(0.036f, 0.088f, rainFactor) * (0.55f + 0.65f * depth)
        drop.thickness = lerpF(1.6f, 3.4f, rainFactor) * (0.55f + 0.60f * depth)
        drop.alpha = (0.30f + 0.62f * depth) * lerpF(0.55f, 1.0f, rainFactor)
    }

    private fun spawnSplash(x: Float, y: Float, rainFactor: Float, wind: Float, isAmbient: Boolean, depth: Float = 0.7f) {
        val p = splashes[splashCursor++ % splashes.size]
        val scale = if (isAmbient) 0.55f else 1.0f
        p.active = true
        p.x = x + (rng.nextFloat() * 6f - 3f)
        p.y = y - rng.nextFloat() * 3f
        p.vx = width * lerpF(0.10f, 0.24f, rainFactor) * (rng.nextFloat() * 2f - 1f) * scale + (wind - 0.15f) * width * 0.16f
        p.vy = -height * lerpF(0.14f, 0.42f, rainFactor) * (0.45f + rng.nextFloat() * 0.85f) * scale
        p.radius = lerpF(1.4f, 3.2f, rng.nextFloat()) * (0.65f + 0.45f * depth)
        p.alpha = 0.48f + rng.nextFloat() * 0.48f
        p.age = 0f
        p.maxAge = (0.24f + rng.nextFloat() * 0.36f) * lerpF(0.8f, 1.25f, rainFactor)
        p.bounced = false
    }
}
