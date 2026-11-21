package com.daykit.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.widget.RemoteViews
import com.daykit.MainActivity
import com.daykit.R
import com.daykit.feature.dailywidgets.DailyWidgetDataProvider
import com.daykit.feature.dailywidgets.DailyWidgetRenderer
import com.daykit.feature.dailywidgets.DailyWidgetType

/**
 * Shared base [AppWidgetProvider] that renders any [DailyWidgetType] via [DailyWidgetRenderer]
 * into a crisp bitmap and binds it to [R.layout.widget_canvas_bitmap].
 * Tapping the widget refreshes its live data and opens DayKit.
 */
abstract class BaseDailyCanvasWidgetProvider(
    private val widgetType: DailyWidgetType,
    private val bitmapWidth: Int,
    private val bitmapHeight: Int,
) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            renderAndPush(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_BATTERY_CHANGED ||
            action == ACTION_REFRESH
        ) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, this::class.java))
            if (ids.isNotEmpty()) {
                onUpdate(context, mgr, ids)
            }
        }
    }

    private fun renderAndPush(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val bmp = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pad = 8f
        val rect = RectF(pad, pad, bitmapWidth - pad, bitmapHeight - pad)
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        when (widgetType) {
            DailyWidgetType.SOLAR_ARC -> {
                val state = DailyWidgetDataProvider.loadSolarArcState(context)
                DailyWidgetRenderer.drawSolarArc(canvas, rect, state, overWallpaperBlur = false)
            }
            DailyWidgetType.RADIAL_DIAL_CLOCK -> {
                DailyWidgetRenderer.drawRadialDialClock(canvas, rect, overWallpaperBlur = false)
            }
            DailyWidgetType.LIQUID_GLASS_CLOCK -> {
                DailyWidgetRenderer.drawLiquidGlassClock(canvas, rect, overWallpaperBlur = false)
            }
            DailyWidgetType.STEPS_BAR_CHART -> {
                val state = DailyWidgetDataProvider.loadStepsState(context)
                DailyWidgetRenderer.drawStepsBarChart(canvas, rect, state)
            }
            DailyWidgetType.BENTO_BATTERY -> {
                val state = DailyWidgetDataProvider.loadBatteryState(context)
                DailyWidgetRenderer.drawBentoBattery(canvas, rect, state, darkSurface = isDark)
            }
            DailyWidgetType.BENTO_AGENDA -> {
                val state = DailyWidgetDataProvider.loadAgendaState()
                DailyWidgetRenderer.drawBentoAgenda(canvas, rect, state, darkSurface = isDark)
            }
            DailyWidgetType.GLASS_WEATHER -> Unit
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            widgetType.ordinal + 500,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val views = RemoteViews(context.packageName, R.layout.widget_canvas_bitmap).apply {
            setImageViewBitmap(R.id.daily_widget_canvas, bmp)
            setOnClickPendingIntent(R.id.daily_widget_root, pi)
        }
        manager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        const val ACTION_REFRESH = "com.daykit.feature.widget.ACTION_REFRESH_DAILY_WIDGET"

        fun providerClassFor(type: DailyWidgetType): Class<*> = when (type) {
            DailyWidgetType.GLASS_WEATHER -> WeatherGlassWidgetProvider::class.java
            DailyWidgetType.SOLAR_ARC -> SolarArcWidgetProvider::class.java
            DailyWidgetType.RADIAL_DIAL_CLOCK -> RadialClockWidgetProvider::class.java
            DailyWidgetType.LIQUID_GLASS_CLOCK -> GlassClockWidgetProvider::class.java
            DailyWidgetType.STEPS_BAR_CHART -> StepsChartWidgetProvider::class.java
            DailyWidgetType.BENTO_BATTERY -> BatteryBentoWidgetProvider::class.java
            DailyWidgetType.BENTO_AGENDA -> AgendaBentoWidgetProvider::class.java
        }
    }
}

class SolarArcWidgetProvider : BaseDailyCanvasWidgetProvider(
    widgetType = DailyWidgetType.SOLAR_ARC,
    bitmapWidth = 860,
    bitmapHeight = 426,
)

class RadialClockWidgetProvider : BaseDailyCanvasWidgetProvider(
    widgetType = DailyWidgetType.RADIAL_DIAL_CLOCK,
    bitmapWidth = 840,
    bitmapHeight = 438,
)

class GlassClockWidgetProvider : BaseDailyCanvasWidgetProvider(
    widgetType = DailyWidgetType.LIQUID_GLASS_CLOCK,
    bitmapWidth = 880,
    bitmapHeight = 410,
)

class StepsChartWidgetProvider : BaseDailyCanvasWidgetProvider(
    widgetType = DailyWidgetType.STEPS_BAR_CHART,
    bitmapWidth = 840,
    bitmapHeight = 488,
)

class BatteryBentoWidgetProvider : BaseDailyCanvasWidgetProvider(
    widgetType = DailyWidgetType.BENTO_BATTERY,
    bitmapWidth = 520,
    bitmapHeight = 520,
)

class AgendaBentoWidgetProvider : BaseDailyCanvasWidgetProvider(
    widgetType = DailyWidgetType.BENTO_AGENDA,
    bitmapWidth = 860,
    bitmapHeight = 440,
)
