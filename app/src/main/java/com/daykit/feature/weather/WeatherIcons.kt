package com.daykit.feature.weather

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Static clear-sky icons shared by the in-app widget, home screen widget and live wallpaper.
 * Both are intentionally non-animated.
 */
object WeatherIcons {

    /** Static sun (no rotating rays). [size] is the icon box size. */
    fun drawSun(canvas: Canvas, cx: Float, cy: Float, size: Float, alpha: Float = 1f) {
        val a = alpha.coerceIn(0f, 1f)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, size * 0.6f,
                Color.argb((115 * a).toInt(), 255, 202, 40),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, size * 0.6f, glow)
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((255 * a).toInt(), 255, 184, 28)
            strokeCap = Paint.Cap.ROUND
            strokeWidth = size * 0.09f
        }
        for (i in 0 until 8) {
            val ang = i * (PI.toFloat() / 4f)
            canvas.drawLine(
                cx + cos(ang) * size * 0.34f,
                cy + sin(ang) * size * 0.34f,
                cx + cos(ang) * size * 0.47f,
                cy + sin(ang) * size * 0.47f,
                sunPaint,
            )
        }
        canvas.drawCircle(cx, cy, size * 0.24f, sunPaint)
    }

    /** Static crescent moon with a soft glow. [size] is the icon box size. */
    fun drawMoon(canvas: Canvas, cx: Float, cy: Float, size: Float, alpha: Float = 1f) {
        val a = alpha.coerceIn(0f, 1f)
        val r = size * 0.36f
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, size * 0.62f,
                Color.argb((80 * a).toInt(), 220, 228, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, size * 0.62f, glow)

        val moon = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        val bite = Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.38f, r * 0.86f, Path.Direction.CW) }
        moon.op(bite, Path.Op.DIFFERENCE)
        val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((255 * a).toInt(), 246, 242, 222)
        }
        canvas.drawPath(moon, moonPaint)
    }
}
