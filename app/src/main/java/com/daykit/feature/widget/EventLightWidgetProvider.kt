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
import android.graphics.RectF
import android.provider.Settings
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.daykit.R
import com.daykit.core.permissions.PermissionIntents
import com.daykit.feature.eventlight.data.EventLightSettings
import com.daykit.feature.eventlight.data.EventLightStore
import com.daykit.feature.eventlight.data.edgeLightTintColor
import com.daykit.feature.eventlight.service.EventLightService

class EventLightWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return

        val current = EventLightStore.get(context)
        if (current.enabled) {
            EventLightStore.setEnabled(context, false)
            EventLightService.stop(context)
        } else {
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
        }
        refreshAll(context)
    }

    companion object {
        const val ACTION_TOGGLE = "com.daykit.feature.widget.ACTION_TOGGLE_EVENT_LIGHT"

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
            val badgeBitmap = renderRingBadgeBitmap(context, settings, isOn)

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
                val views = RemoteViews(context.packageName, R.layout.widget_event_light).apply {
                    setImageViewBitmap(R.id.widget_event_light_icon, badgeBitmap)
                    setTextViewText(
                        R.id.widget_event_light_status,
                        context.getString(
                            if (isOn) R.string.widget_event_light_on else R.string.widget_event_light_off,
                        ),
                    )
                    setTextViewText(
                        R.id.widget_event_light_toggle,
                        context.getString(
                            if (isOn) R.string.widget_event_light_badge_on else R.string.widget_event_light_badge_off,
                        ),
                    )
                    setInt(
                        R.id.widget_event_light_toggle,
                        "setBackgroundResource",
                        if (isOn) R.drawable.widget_habit_done_background else R.drawable.widget_habit_open_background,
                    )
                    setTextColor(
                        R.id.widget_event_light_toggle,
                        ContextCompat.getColor(
                            context,
                            if (isOn) R.color.on_brand_primary else R.color.text_muted,
                        ),
                    )
                    setOnClickPendingIntent(R.id.widget_root, togglePendingIntent)
                    setOnClickPendingIntent(R.id.widget_event_light_toggle, togglePendingIntent)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun renderRingBadgeBitmap(
            context: Context,
            settings: EventLightSettings,
            isOn: Boolean,
        ): Bitmap {
            val density = context.resources.displayMetrics.density
            val sizePx = (42f * density).toInt().coerceAtLeast(84)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val bgRect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
            val bgCorner = sizePx * 0.28f
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (isOn) 0xFF14161A.toInt() else 0xFF23262C.toInt()
            }
            canvas.drawRoundRect(bgRect, bgCorner, bgCorner, bgPaint)

            val inset = sizePx * 0.22f
            val ringRect = RectF(inset, inset, sizePx - inset, sizePx - inset)
            val ringCorner = sizePx * 0.16f

            if (isOn) {
                val tint = edgeLightTintColor(settings.colorTemp)
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = sizePx * 0.14f
                    color = tint.copy(alpha = 0.72f).toArgb()
                    maskFilter = BlurMaskFilter(sizePx * 0.11f, BlurMaskFilter.Blur.NORMAL)
                }
                val corePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = sizePx * 0.085f
                    color = lerp(Color.White, tint, 0.25f).toArgb()
                }
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, glowPaint)
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, corePaint)
            } else {
                val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = sizePx * 0.07f
                    color = 0xFF6E7480.toInt()
                }
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, offPaint)
            }

            return bitmap
        }
    }
}
