package com.daykit.feature.widget

import android.app.AlarmManager
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
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import com.daykit.MainActivity
import com.daykit.R
import com.daykit.feature.dailywidgets.DailyWidgetDataProvider
import com.daykit.feature.dailywidgets.DailyWidgetRenderer
import com.daykit.feature.dailywidgets.DailyWidgetType
import com.daykit.feature.dailywidgets.DeviceStepCounter
import kotlin.math.roundToInt

/**
 * Shared base [AppWidgetProvider] that renders any [DailyWidgetType] via [DailyWidgetRenderer]
 * into a crisp bitmap and binds it to [R.layout.widget_canvas_bitmap].
 * Tapping the widget refreshes its live data and opens DayKit.
 */
abstract class BaseDailyCanvasWidgetProvider(
    private val widgetType: DailyWidgetType,
    private val bitmapWidth: Int,
    private val bitmapHeight: Int,
    private val supportsDynamicAspect: Boolean = false,
    private val ticksEveryMinute: Boolean = false,
) : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        if (ticksEveryMinute) {
            scheduleNextMinuteTick(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (ticksEveryMinute) {
            cancelMinuteTick(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            renderAndPush(context, appWidgetManager, id)
        }
        if (ticksEveryMinute && appWidgetIds.isNotEmpty()) {
            scheduleNextMinuteTick(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (supportsDynamicAspect) {
            renderAndPush(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == ACTION_CLOCK_MINUTE_TICK ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_BATTERY_CHANGED ||
            action == ACTION_REFRESH
        ) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, this::class.java))
            if (ids.isNotEmpty()) {
                onUpdate(context, mgr, ids)
            } else if (ticksEveryMinute) {
                cancelMinuteTick(context)
            }
        }
    }

    protected fun minuteTickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, this::class.java).apply {
            action = ACTION_CLOCK_MINUTE_TICK
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            widgetType.ordinal + 900,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    protected fun scheduleNextMinuteTick(context: Context) {
        val alarm = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        // Fire 150ms past the next exact minute boundary so Calendar reflects the new minute
        val nextMinuteMillis = ((now / 60_000L) + 1L) * 60_000L + 150L
        val pi = minuteTickPendingIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        runCatching {
            if (canExact) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, nextMinuteMillis, pi)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC, nextMinuteMillis, pi)
            }
        }
    }

    protected fun cancelMinuteTick(context: Context) {
        val alarm = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarm.cancel(minuteTickPendingIntent(context)) }
    }

    protected fun renderAndPush(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val (renderW, renderH) = resolveBitmapSize(manager, appWidgetId)
        val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pad = 8f
        val rect = RectF(pad, pad, renderW - pad, renderH - pad)
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

    private fun resolveBitmapSize(manager: AppWidgetManager, appWidgetId: Int): Pair<Int, Int> {
        if (!supportsDynamicAspect) return bitmapWidth to bitmapHeight
        val opts = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
            ?: return bitmapWidth to bitmapHeight
        val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        if (minW <= 0 || minH <= 0) return bitmapWidth to bitmapHeight
        val aspect = (minW.toFloat() / minH.toFloat()).coerceIn(0.80f, 2.50f)
        val basePx = 560
        return if (aspect >= 1f) {
            (basePx * aspect).roundToInt().coerceIn(520, 1080) to basePx
        } else {
            basePx to (basePx / aspect).roundToInt().coerceIn(520, 820)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.daykit.feature.widget.ACTION_REFRESH_DAILY_WIDGET"
        const val ACTION_CLOCK_MINUTE_TICK = "com.daykit.feature.widget.ACTION_CLOCK_MINUTE_TICK"

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
    bitmapWidth = 560,
    bitmapHeight = 560,
    supportsDynamicAspect = true,
)

class RadialClockWidgetProvider : BaseDailyCanvasWidgetProvider(
    widgetType = DailyWidgetType.RADIAL_DIAL_CLOCK,
    bitmapWidth = 840,
    bitmapHeight = 438,
    ticksEveryMinute = true,
)

class GlassClockWidgetProvider : BaseDailyCanvasWidgetProvider(
    widgetType = DailyWidgetType.LIQUID_GLASS_CLOCK,
    bitmapWidth = 880,
    bitmapHeight = 410,
    ticksEveryMinute = true,
)

class StepsChartWidgetProvider : BaseDailyCanvasWidgetProvider(
    widgetType = DailyWidgetType.STEPS_BAR_CHART,
    bitmapWidth = 840,
    bitmapHeight = 488,
) {
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelSessionTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when (action) {
            ACTION_TOGGLE_SESSION -> {
                val appCtx = context.applicationContext
                if (!DeviceStepCounter.hasPermission(appCtx)) {
                    // Open app so user can grant Physical Activity permission once
                    val open = Intent(appCtx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    appCtx.startActivity(open)
                    return
                }
                val pending = goAsync()
                if (DeviceStepCounter.isRunning(appCtx)) {
                    cancelSessionTick(appCtx)
                    DeviceStepCounter.stopSession(appCtx) {
                        try {
                            renderAllStepsWidgets(appCtx)
                        } finally {
                            pending.finish()
                        }
                    }
                } else {
                    DeviceStepCounter.startSession(appCtx) {
                        try {
                            scheduleSessionTick(appCtx)
                            renderAllStepsWidgets(appCtx)
                        } finally {
                            pending.finish()
                        }
                    }
                }
                return
            }

            ACTION_SELECT_BAR -> {
                val barIndex = intent.getIntExtra(EXTRA_BAR_INDEX, -1)
                if (barIndex >= 0) {
                    DeviceStepCounter.selectBar(context, barIndex)
                    renderAllStepsWidgets(context.applicationContext)
                }
                return
            }

            ACTION_SESSION_TICK -> {
                val appCtx = context.applicationContext
                val mgr = AppWidgetManager.getInstance(appCtx)
                val ids = mgr.getAppWidgetIds(ComponentName(appCtx, StepsChartWidgetProvider::class.java))
                if (ids.isEmpty() || !DeviceStepCounter.isRunning(appCtx)) {
                    cancelSessionTick(appCtx)
                    return
                }
                val pending = goAsync()
                DeviceStepCounter.syncActiveSessionOneShot(appCtx, timeoutMs = 1500L) {
                    try {
                        renderAllStepsWidgets(appCtx)
                        if (DeviceStepCounter.isRunning(appCtx)) {
                            scheduleSessionTick(appCtx)
                        }
                    } finally {
                        pending.finish()
                    }
                }
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val appCtx = context.applicationContext
        if (DeviceStepCounter.isRunning(appCtx)) {
            scheduleSessionTick(appCtx)
            val pending = goAsync()
            DeviceStepCounter.syncActiveSessionOneShot(appCtx, timeoutMs = 1500L) {
                try {
                    for (id in appWidgetIds) {
                        renderStepsWidget(appCtx, appWidgetManager, id)
                    }
                } finally {
                    pending.finish()
                }
            }
        } else {
            cancelSessionTick(appCtx)
            for (id in appWidgetIds) {
                renderStepsWidget(appCtx, appWidgetManager, id)
            }
        }
    }

    private fun renderAllStepsWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, StepsChartWidgetProvider::class.java))
        for (id in ids) {
            renderStepsWidget(context, mgr, id)
        }
    }

    private fun renderStepsWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val bmp = Bitmap.createBitmap(840, 488, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pad = 8f
        val rect = RectF(pad, pad, 840f - pad, 488f - pad)
        val state = DailyWidgetDataProvider.loadStepsState(context)
        DailyWidgetRenderer.drawStepsBarChart(canvas, rect, state)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            context,
            710,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val togglePi = PendingIntent.getBroadcast(
            context,
            711,
            Intent(context, StepsChartWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_SESSION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val barViewIds = intArrayOf(
            R.id.steps_bar_hit_0,
            R.id.steps_bar_hit_1,
            R.id.steps_bar_hit_2,
            R.id.steps_bar_hit_3,
            R.id.steps_bar_hit_4,
            R.id.steps_bar_hit_5,
            R.id.steps_bar_hit_6,
        )

        val views = RemoteViews(context.packageName, R.layout.widget_steps_session).apply {
            setImageViewBitmap(R.id.steps_widget_canvas, bmp)
            setOnClickPendingIntent(R.id.steps_hit_open, openPi)
            setOnClickPendingIntent(R.id.steps_hit_toggle, togglePi)
            barViewIds.forEachIndexed { index, viewId ->
                val barPi = PendingIntent.getBroadcast(
                    context,
                    720 + index,
                    Intent(context, StepsChartWidgetProvider::class.java).apply {
                        action = ACTION_SELECT_BAR
                        putExtra(EXTRA_BAR_INDEX, index)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(viewId, barPi)
            }
        }
        manager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        const val ACTION_TOGGLE_SESSION = "com.daykit.feature.widget.ACTION_STEPS_TOGGLE_SESSION"
        const val ACTION_SELECT_BAR = "com.daykit.feature.widget.ACTION_STEPS_SELECT_BAR"
        const val ACTION_SESSION_TICK = "com.daykit.feature.widget.ACTION_STEPS_SESSION_TICK"
        const val EXTRA_BAR_INDEX = "extra_bar_index"

        private fun sessionTickPi(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            712,
            Intent(context.applicationContext, StepsChartWidgetProvider::class.java).apply {
                action = ACTION_SESSION_TICK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun scheduleSessionTick(context: Context) {
            val alarm = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return
            val nextAt = System.currentTimeMillis() + 60_000L
            val pi = sessionTickPi(context)
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
            runCatching {
                if (canExact) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC, nextAt, pi)
                } else {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC, nextAt, pi)
                }
            }
        }

        fun cancelSessionTick(context: Context) {
            val alarm = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return
            runCatching { alarm.cancel(sessionTickPi(context)) }
        }

        fun refreshAll(context: Context) {
            val appCtx = context.applicationContext
            val mgr = AppWidgetManager.getInstance(appCtx)
            val ids = mgr.getAppWidgetIds(ComponentName(appCtx, StepsChartWidgetProvider::class.java))
            if (ids.isEmpty()) return
            appCtx.sendBroadcast(
                Intent(appCtx, StepsChartWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                },
            )
        }
    }
}

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
