package com.daykit.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.RemoteViews
import com.daykit.R
import com.daykit.feature.weather.WeatherPrefs
import com.daykit.feature.weather.WeatherSnapshot
import com.daykit.feature.weather.WeatherSyncWorker
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Transparent frosted-glass Weather Home Screen AppWidget.
 * Renders a rounded-square ARGB_8888 glass cube with alpha transparency so whatever
 * wallpaper is on the user's home screen shows through naturally, synced to real
 * live weather via [WeatherPrefs] and [WeatherSyncWorker].
 */
class WeatherGlassWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherSyncWorker.schedulePeriodicSync(context)
        WeatherSyncWorker.enqueueImmediateSync(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WeatherSyncWorker.schedulePeriodicSync(context)
        renderAll(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WEATHER) {
            WeatherSyncWorker.enqueueImmediateSync(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH_WEATHER = "com.daykit.widget.WEATHER_GLASS_REFRESH"

        fun refreshAll(context: Context) {
            val appCtx = context.applicationContext
            val manager = AppWidgetManager.getInstance(appCtx)
            val ids = manager.getAppWidgetIds(ComponentName(appCtx, WeatherGlassWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                renderAll(appCtx, manager, ids)
            }
        }

        private fun renderAll(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val snapshot = WeatherPrefs.loadSnapshot(context)
            val useFahrenheit = WeatherPrefs.useFahrenheit(context)
            val bitmap = renderGlassCubeBitmap(
                sizePx = 520,
                snapshot = snapshot,
                useFahrenheit = useFahrenheit,
            )
            val refreshIntent = PendingIntent.getBroadcast(
                context,
                ACTION_REFRESH_WEATHER.hashCode(),
                Intent(context, WeatherGlassWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WEATHER
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_weather_glass)
                views.setImageViewBitmap(R.id.weather_glass_canvas, bitmap)
                views.setOnClickPendingIntent(R.id.weather_glass_root, refreshIntent)
                manager.updateAppWidget(id, views)
            }
        }

        private fun renderGlassCubeBitmap(
            sizePx: Int,
            snapshot: WeatherSnapshot,
            useFahrenheit: Boolean,
        ): Bitmap {
            val t = snapshot.intensity.coerceIn(0f, 1f)
            val wind = snapshot.windStrength.coerceIn(0f, 1f)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val w = sizePx.toFloat()
            val h = sizePx.toFloat()
            val inset = w * 0.025f
            val cardRect = RectF(inset, inset, w - inset, h - inset)
            val cardSize = cardRect.width()
            val cornerRadius = cardSize * 0.22f

            val clipPath = Path().apply {
                addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)
            }

            canvas.save()
            canvas.clipPath(clipPath)

            // 1. Transparent frosted-glass atmospheric tint (home screen wallpaper shines through)
            val skyTopColor = when {
                t < 0.33f -> lerpColor(0xFF4CA8FF.toInt(), 0xFF707E8C.toInt(), t / 0.33f)
                t < 0.66f -> lerpColor(0xFF707E8C.toInt(), 0xFF28313B.toInt(), (t - 0.33f) / 0.33f)
                else -> lerpColor(0xFF28313B.toInt(), 0xFF10151C.toInt(), (t - 0.66f) / 0.34f)
            }
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                shader = LinearGradient(
                    0f, cardRect.top, 0f, cardRect.bottom,
                    intArrayOf(
                        withAlpha(skyTopColor, (lerpF(0.28f, 0.72f, t) * 255).toInt()),
                        withAlpha(0xFF141B24.toInt(), (lerpF(0.14f, 0.42f, t) * 255).toInt()),
                        withAlpha(0xFF10151C.toInt(), (lerpF(0.16f, 0.38f, t) * 255).toInt()),
                        withAlpha(0xFFDCE7F2.toInt(), (lerpF(0.16f, 0.34f, t) * 255).toInt()),
                    ),
                    floatArrayOf(0f, 0.42f, 0.80f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(cardRect, bgPaint)

            val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((lerpF(0.14f, 0.08f, t) * 255).toInt(), 255, 255, 255)
            }
            canvas.drawRect(cardRect, veilPaint)

            // 2. Volumetric procedural clouds at top
            drawStaticClouds(canvas, cardRect, t)

            // 3. Rain streaks & bottom ground splash accumulation
            val rainFactor = smoothstep(0.08f, 1.0f, t)
            if (rainFactor > 0.01f) {
                drawStaticRainAndSplashes(canvas, cardRect, cornerRadius, rainFactor, wind)
            }

            // 4. Frosted glass inner glow + crisp white rim
            val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = cardSize * 0.055f
                color = Color.argb((lerpF(0.26f, 0.22f, t) * 255).toInt(), 255, 255, 255)
                maskFilter = BlurMaskFilter(cardSize * 0.045f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, innerGlowPaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = cardSize * 0.009f
                shader = LinearGradient(
                    cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                    intArrayOf(
                        Color.argb(180, 255, 255, 255),
                        Color.argb(55, 255, 255, 255),
                        Color.argb(135, 255, 255, 255),
                    ),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

            // 5. Typography & Weather Icon
            drawWeatherContent(canvas, cardRect, snapshot, useFahrenheit)

            canvas.restore()
            return bmp
        }

        private fun drawStaticClouds(canvas: Canvas, rect: RectF, t: Float) {
            val puff = createWidgetCloudPuff(96, 101)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val dst = RectF()

            val highlightColor = lerpColor(0xFFFFFFFF.toInt(), 0xFF9EA8B3.toInt(), t)
            val midColor = lerpColor(0xFFE9EEF5.toInt(), 0xFF232A32.toInt(), t)
            val shadowColor = lerpColor(0xFF9EB0C4.toInt(), 0xFF0A0D11.toInt(), t)

            data class PuffSpec(val x: Float, val y: Float, val rx: Float, val ry: Float, val layer: Int, val minT: Float)
            val specs = listOf(
                PuffSpec(0.24f, 0.22f, 0.34f, 0.22f, 0, 0.20f),
                PuffSpec(0.54f, 0.25f, 0.38f, 0.24f, 0, 0.14f),
                PuffSpec(0.78f, 0.23f, 0.34f, 0.23f, 0, 0.00f),
                PuffSpec(0.20f, 0.12f, 0.32f, 0.22f, 1, 0.20f),
                PuffSpec(0.48f, 0.15f, 0.36f, 0.23f, 1, 0.15f),
                PuffSpec(0.72f, 0.16f, 0.34f, 0.23f, 1, 0.00f),
                PuffSpec(0.56f, 0.14f, 0.22f, 0.17f, 2, 0.00f),
                PuffSpec(0.71f, 0.11f, 0.26f, 0.20f, 2, 0.00f),
                PuffSpec(0.83f, 0.15f, 0.20f, 0.16f, 2, 0.00f),
                PuffSpec(0.32f, 0.08f, 0.28f, 0.18f, 2, 0.22f),
            )

            val w = rect.width()
            val h = rect.height()
            for (s in specs) {
                val vis = smoothstep(s.minT - 0.12f, s.minT + 0.18f, t)
                if (vis <= 0.01f) continue
                val color = when (s.layer) {
                    0 -> shadowColor
                    1 -> midColor
                    else -> highlightColor
                }
                val alpha = when (s.layer) {
                    0 -> lerpF(0.25f, 0.86f, t)
                    1 -> lerpF(0.72f, 0.84f, t)
                    else -> lerpF(0.92f, 0.65f, t)
                } * vis

                val cx = rect.left + s.x * w
                val cy = rect.top + s.y * h
                val rx = s.rx * w * lerpF(0.9f, 1.15f, t)
                val ry = s.ry * h * lerpF(0.9f, 1.15f, t)
                dst.set(cx - rx, cy - ry, cx + rx, cy + ry)
                paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
                canvas.drawBitmap(puff, null, dst, paint)
            }
            puff.recycle()
        }

        private fun drawStaticRainAndSplashes(
            canvas: Canvas,
            rect: RectF,
            cornerRadius: Float,
            rainFactor: Float,
            wind: Float,
        ) {
            val w = rect.width()
            val h = rect.height()
            val rng = Random(77)

            val mistH = h * lerpF(0.10f, 0.24f, rainFactor)
            val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, rect.bottom - mistH, 0f, rect.bottom,
                    Color.TRANSPARENT,
                    Color.argb((0.24f * rainFactor * 255).toInt(), 255, 255, 255),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(rect.left, rect.bottom - mistH, rect.right, rect.bottom, mistPaint)

            val dropCount = lerpF(14f, 88f, rainFactor).roundToInt()
            val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
            val slantRatio = (wind * 1.1f - 0.08f) * 0.42f
            for (i in 0 until dropCount) {
                val depth = 0.35f + rng.nextFloat() * 0.65f
                val x = rect.left + rng.nextFloat() * w
                val y = rect.top + h * (0.14f + rng.nextFloat() * 0.80f)
                val len = h * lerpF(0.04f, 0.09f, rainFactor) * (0.55f + 0.65f * depth)
                val norm = hypot(slantRatio.toDouble(), 1.0).toFloat()
                val dx = (slantRatio / norm) * len
                val dy = (1f / norm) * len
                val alpha = ((0.28f + 0.60f * depth) * rainFactor * 255).toInt().coerceIn(0, 235)
                streakPaint.color = Color.argb(alpha, 244, 249, 255)
                streakPaint.strokeWidth = lerpF(1.4f, 3.0f, rainFactor) * (0.55f + 0.55f * depth)
                canvas.drawLine(x - dx, y - dy, x, y, streakPaint)
            }

            val splashCount = lerpF(18f, 110f, rainFactor).roundToInt()
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 0 until splashCount) {
                val localX = w * (0.06f + rng.nextFloat() * 0.88f)
                val floorY = when {
                    localX < cornerRadius -> {
                        val dx = cornerRadius - localX
                        rect.bottom - cornerRadius + sqrt((cornerRadius * cornerRadius - dx * dx).coerceAtLeast(0f))
                    }
                    localX > w - cornerRadius -> {
                        val dx = localX - (w - cornerRadius)
                        rect.bottom - cornerRadius + sqrt((cornerRadius * cornerRadius - dx * dx).coerceAtLeast(0f))
                    }
                    else -> rect.bottom
                }
                val bias = rng.nextFloat() * rng.nextFloat()
                val y = floorY - (4f + bias * h * lerpF(0.06f, 0.15f, rainFactor))
                val r = (1.5f + rng.nextFloat() * 3.0f) * lerpF(0.75f, 1.15f, rainFactor)
                val alpha = ((0.35f + rng.nextFloat() * 0.60f) * rainFactor * 255).toInt().coerceIn(0, 240)
                dotPaint.color = Color.argb(alpha, 255, 255, 255)
                canvas.drawCircle(rect.left + localX, y, r, dotPaint)
            }
        }

        private fun drawWeatherContent(
            canvas: Canvas,
            rect: RectF,
            snapshot: WeatherSnapshot,
            useFahrenheit: Boolean,
        ) {
            val w = rect.width()
            val h = rect.height()
            val padX = rect.left + w * 0.11f
            val t = snapshot.intensity.coerceIn(0f, 1f)
            val wind = snapshot.windStrength.coerceIn(0f, 1f)

            val temp = snapshot.displayTemp(useFahrenheit)
            val high = snapshot.displayHigh(useFahrenheit)
            val low = snapshot.displayLow(useFahrenheit)
            val unit = snapshot.unitSymbol(useFahrenheit)

            fun textPaint(sizeRatio: Float, bold: Boolean, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 255, 255, 255)
                textSize = w * sizeRatio
                typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setShadowLayer(w * 0.02f, 0f, w * 0.008f, Color.argb(95, 0, 0, 0))
            }

            canvas.drawText(snapshot.city, padX, rect.top + h * 0.205f, textPaint(0.078f, bold = false, alpha = 242))
            canvas.drawText("$temp$unit", padX - w * 0.008f, rect.top + h * 0.415f, textPaint(0.212f, bold = true))

            val iconSize = w * 0.135f
            val iconTop = rect.top + h * 0.545f
            if (t < 0.22f) {
                val cx = padX + iconSize * 0.45f
                val cy = iconTop + iconSize * 0.48f
                if (snapshot.isNight()) {
                    com.daykit.feature.weather.WeatherIcons.drawMoon(canvas, cx, cy, iconSize)
                } else {
                    com.daykit.feature.weather.WeatherIcons.drawSun(canvas, cx, cy, iconSize)
                }
            } else {
                val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    strokeCap = Paint.Cap.ROUND
                }
                val baseY = iconTop + iconSize * 0.56f
                canvas.drawCircle(padX + iconSize * 0.27f, baseY - iconSize * 0.14f, iconSize * 0.16f, cloudPaint)
                canvas.drawCircle(padX + iconSize * 0.48f, baseY - iconSize * 0.22f, iconSize * 0.23f, cloudPaint)
                canvas.drawCircle(padX + iconSize * 0.70f, baseY - iconSize * 0.15f, iconSize * 0.17f, cloudPaint)
                canvas.drawRoundRect(
                    padX + iconSize * 0.14f,
                    baseY - iconSize * 0.24f,
                    padX + iconSize * 0.83f,
                    baseY,
                    iconSize * 0.12f,
                    iconSize * 0.12f,
                    cloudPaint,
                )
                val drops = if (t < 0.52f) 3 else 4
                val dashLen = iconSize * lerpF(0.11f, 0.19f, t)
                val slant = dashLen * lerpF(0.16f, 0.35f, wind)
                cloudPaint.strokeWidth = iconSize * 0.08f
                for (i in 0 until drops) {
                    val frac = i.toFloat() / (drops - 1)
                    val x = padX + iconSize * lerpF(0.24f, 0.72f, frac)
                    val y = baseY + iconSize * 0.11f + (i % 2) * iconSize * 0.05f
                    canvas.drawLine(x, y, x - slant, y + dashLen, cloudPaint)
                }
            }

            canvas.drawText(snapshot.conditionLabel, padX, rect.top + h * 0.775f, textPaint(0.082f, bold = true))
            canvas.drawText("H:${high}°  L:${low}°", padX, rect.top + h * 0.885f, textPaint(0.068f, bold = false, alpha = 232))
        }

        private fun createWidgetCloudPuff(size: Int, seed: Int): Bitmap {
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
                    val n = noise2D(fx, fy, seed) * 0.58f +
                        noise2D(fx * 2f + 9.3f, fy * 2f + 5.1f, seed + 17) * 0.28f +
                        noise2D(fx * 4f + 19.7f, fy * 4f + 13.4f, seed + 43) * 0.14f
                    val radial = smoothstep(1.0f, 0.18f, dist)
                    val d = ((radial * (0.58f + 0.72f * n)) - 0.14f).coerceIn(0f, 1f)
                    val a = (d * d * 255f).toInt().coerceIn(0, 255)
                    pixels[y * size + x] = (a shl 24) or 0x00FFFFFF
                }
            }
            bmp.setPixels(pixels, 0, size, 0, 0, size, size)
            return bmp
        }

        private fun noise2D(x: Float, y: Float, seed: Int): Float {
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
    }
}
