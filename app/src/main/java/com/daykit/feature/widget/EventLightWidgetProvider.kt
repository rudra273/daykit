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
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.daykit.R
import com.daykit.core.permissions.PermissionIntents
import com.daykit.feature.eventlight.data.EventLightSettings
import com.daykit.feature.eventlight.data.EventLightStore
import com.daykit.feature.eventlight.data.edgeLightTintColor
import com.daykit.feature.eventlight.service.EventLightService
import com.daykit.feature.eventlight.service.FrontCameraMonitor
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class EventLightWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val current = EventLightStore.get(context)
                if (current.enabled) {
                    turnOff(context)
                } else {
                    turnOn(context)
                }
            }
            ACTION_TURN_ON -> turnOn(context)
            ACTION_TURN_OFF -> turnOff(context)
        }
    }

    private fun turnOn(context: Context) {
        FrontCameraMonitor.dismissNotification(context)
        if (!Settings.canDrawOverlays(context)) {
            runCatching {
                val settingsIntent = PermissionIntents.overlaySettings(context).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            }
            refreshAll(context)
            return
        }
        EventLightStore.setEnabled(context, true)
        EventLightService.start(context)
        refreshAll(context)
    }

    private fun turnOff(context: Context) {
        FrontCameraMonitor.dismissNotification(context)
        EventLightStore.setEnabled(context, false)
        EventLightService.stop(context)
        refreshAll(context)
    }

    companion object {
        const val ACTION_TOGGLE = "com.daykit.feature.widget.ACTION_TOGGLE_EVENT_LIGHT"
        const val ACTION_TURN_ON = "com.daykit.feature.widget.ACTION_TURN_ON_EVENT_LIGHT"
        const val ACTION_TURN_OFF = "com.daykit.feature.widget.ACTION_TURN_OFF_EVENT_LIGHT"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EventLightWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                updateWidgets(context, manager, ids)
            }
        }

        fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            if (appWidgetIds.isEmpty()) return
            val settings = EventLightStore.get(context)
            val isOn = settings.enabled && Settings.canDrawOverlays(context)

            val toggleIntent = Intent(context, EventLightWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                ACTION_TOGGLE.hashCode(),
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            appWidgetIds.forEach { appWidgetId ->
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160).coerceAtLeast(72)
                val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 84).coerceAtLeast(72)

                val bitmap = renderGlowingRectangleBitmap(
                    widthDp = minWidthDp,
                    heightDp = minHeightDp,
                    settings = settings,
                    isOn = isOn,
                )

                val views = RemoteViews(context.packageName, R.layout.widget_event_light).apply {
                    setImageViewBitmap(R.id.widget_event_light_canvas, bitmap)
                    setOnClickPendingIntent(R.id.widget_root, togglePendingIntent)
                    setOnClickPendingIntent(R.id.widget_event_light_canvas, togglePendingIntent)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        /**
         * Renders a minimal hardware-style dark rounded card whose entire perimeter
         * IS the glowing macOS Edge Light rectangle, with just a clean sun glyph
         * and tiny ON/OFF indicator in the center.
         */
        private fun renderGlowingRectangleBitmap(
            widthDp: Int,
            heightDp: Int,
            settings: EventLightSettings,
            isOn: Boolean,
        ): Bitmap {
            val aspect = (widthDp.toFloat() / heightDp.toFloat()).coerceIn(0.65f, 3.2f)
            val basePx = 360f
            val w: Int
            val h: Int
            if (aspect >= 1f) {
                h = basePx.roundToInt()
                w = (basePx * aspect).roundToInt().coerceAtMost(960)
            } else {
                w = basePx.roundToInt()
                h = (basePx / aspect).roundToInt().coerceAtMost(960)
            }

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val minDim = min(w, h).toFloat()

            // Outer obsidian glass card
            val cardMargin = minDim * 0.025f
            val cardRect = RectF(cardMargin, cardMargin, w - cardMargin, h - cardMargin)
            val cardCorner = minDim * 0.24f

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                style = Paint.Style.FILL
                color = if (isOn) 0xFF0C0E12.toInt() else 0xFF17191F.toInt()
            }
            canvas.drawRoundRect(cardRect, cardCorner, cardCorner, bgPaint)

            // Clip inner glows cleanly to the rounded card
            val saveCount = canvas.save()
            val clipPath = android.graphics.Path().apply {
                addRoundRect(cardRect, cardCorner, cardCorner, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(clipPath)

            val tint = edgeLightTintColor(settings.colorTemp)

            // Subtle ambient center wash when ON so the dark glass feels illuminated
            if (isOn) {
                val washPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    shader = RadialGradient(
                        w / 2f,
                        h / 2f,
                        maxOf(w, h) * 0.72f,
                        intArrayOf(
                            tint.copy(alpha = 0.04f).toArgb(),
                            tint.copy(alpha = 0.14f).toArgb(),
                        ),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(cardRect, washPaint)
            }

            // Glowing macOS rounded-rectangle ring around the widget perimeter
            val ringInset = minDim * 0.14f
            val ringRect = RectF(
                cardRect.left + ringInset,
                cardRect.top + ringInset,
                cardRect.right - ringInset,
                cardRect.bottom - ringInset,
            )
            val ringCorner = (cardCorner - ringInset * 0.65f).coerceAtLeast(minDim * 0.10f)
            val ringStroke = minDim * 0.072f

            if (isOn) {
                // 1. Wide outer ambient glow
                val outerGlow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke * 2.3f
                    color = tint.copy(alpha = 0.58f).toArgb()
                    maskFilter = BlurMaskFilter(ringStroke * 1.6f, BlurMaskFilter.Blur.NORMAL)
                }
                // 2. Soft bloom halo
                val midBloom = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke * 1.35f
                    color = lerp(tint, Color.White, 0.30f).copy(alpha = 0.85f).toArgb()
                    maskFilter = BlurMaskFilter(ringStroke * 0.7f, BlurMaskFilter.Blur.NORMAL)
                }
                // 3. Incandescent luminous core tube
                val coreTube = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke * 0.82f
                    color = lerp(Color.White, tint, 0.22f).copy(alpha = 0.98f).toArgb()
                    maskFilter = BlurMaskFilter(ringStroke * 0.18f, BlurMaskFilter.Blur.NORMAL)
                }
                // 4. Inner bright filament
                val innerFilament = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke * 0.38f
                    color = 0xE6FFFFFF.toInt()
                }
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, outerGlow)
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, midBloom)
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, coreTube)
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, innerFilament)
            } else {
                // Subtle matte unlit rectangle silhouette
                val offRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke * 0.72f
                    color = 0xFF3A3F4B.toInt()
                }
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, offRing)
            }

            // Minimal centered macOS sun glyph + subtle ON/OFF micro-label
            val cx = w / 2f
            val cy = h / 2f - minDim * 0.055f
            val glyphSize = minDim * 0.22f
            val glyphColor = if (isOn) 0xF2FFFFFF.toInt() else 0xFF7A808E.toInt()
            drawSunGlyph(canvas, cx, cy, glyphSize, glyphColor)

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isOn) lerp(Color.White, tint, 0.35f).copy(alpha = 0.90f).toArgb() else 0xFF6B717E.toInt()
                textSize = minDim * 0.105f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.14f
            }
            val labelY = cy + glyphSize * 0.78f + labelPaint.textSize * 0.45f
            canvas.drawText(if (isOn) "ON" else "OFF", cx, labelY, labelPaint)

            canvas.restoreToCount(saveCount)

            // Crisp hairline glass border around outer card
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = minDim * 0.012f
                color = if (isOn) 0x26FFFFFF else 0x1AFFFFFF
            }
            canvas.drawRoundRect(cardRect, cardCorner, cardCorner, rimPaint)

            return bitmap
        }

        private fun drawSunGlyph(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            size: Float,
            color: Int,
        ) {
            val stroke = (size * 0.095f).coerceAtLeast(2f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            val r = size * 0.23f
            val rayInner = size * 0.36f
            val rayOuter = size * 0.48f
            canvas.drawCircle(cx, cy, r, paint)
            for (i in 0 until 8) {
                val rad = Math.toRadians(i * 45.0)
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                canvas.drawLine(
                    cx + dx * rayInner,
                    cy + dy * rayInner,
                    cx + dx * rayOuter,
                    cy + dy * rayOuter,
                    paint,
                )
            }
        }
    }
}
