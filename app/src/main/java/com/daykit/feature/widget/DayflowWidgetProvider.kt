package com.daykit.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.daykit.DayKitApplication
import com.daykit.R
import com.daykit.feature.dayflow.data.PomodoroSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DayflowWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        refreshAsync(context) { result.finish() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in setOf(ACTION_START, ACTION_BREAK, ACTION_PAUSE, ACTION_RESUME,
                ACTION_STOP, ACTION_FINISH, ACTION_TICK, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as DayKitApplication).container.dayflowRepository
                when (intent.action) {
                    ACTION_START -> repository.start("work")
                    ACTION_BREAK -> repository.start("break")
                    ACTION_PAUSE -> repository.pause()
                    ACTION_RESUME -> repository.resume()
                    ACTION_STOP -> repository.stop()
                }
                render(context, repository.currentSession())
            } catch (error: Exception) {
                Log.w("DayflowWidget", "Could not update Pomodoro", error)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private const val ACTION_START = "com.daykit.widget.DAYFLOW_START"
        private const val ACTION_BREAK = "com.daykit.widget.DAYFLOW_BREAK"
        private const val ACTION_PAUSE = "com.daykit.widget.DAYFLOW_PAUSE"
        private const val ACTION_RESUME = "com.daykit.widget.DAYFLOW_RESUME"
        private const val ACTION_STOP = "com.daykit.widget.DAYFLOW_STOP"
        private const val ACTION_FINISH = "com.daykit.widget.DAYFLOW_FINISH"
        private const val ACTION_TICK = "com.daykit.widget.DAYFLOW_TICK"
        private const val TICK_INTERVAL_MILLIS = 60_000L

        private fun action(context: Context, name: String): PendingIntent = PendingIntent.getBroadcast(
            context, name.hashCode(), Intent(context, DayflowWidgetProvider::class.java).apply {
                action = name
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun refreshAsync(context: Context, onDone: () -> Unit = {}) {
            val resultContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = (resultContext as DayKitApplication).container.dayflowRepository
                    render(resultContext, repository.currentSession())
                } catch (error: Exception) {
                    Log.w("DayflowWidget", "Could not refresh Pomodoro", error)
                } finally {
                    onDone()
                }
            }
        }

        private fun render(context: Context, session: PomodoroSessionEntity?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DayflowWidgetProvider::class.java))
            val alarm = context.getSystemService(AlarmManager::class.java)
            alarm.cancel(action(context, ACTION_FINISH))
            alarm.cancel(action(context, ACTION_TICK))
            if (session?.state == "running" && ids.isNotEmpty()) {
                if (alarm.canScheduleExactAlarms()) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.endAtMillis,
                        action(context, ACTION_FINISH))
                } else {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.endAtMillis,
                        action(context, ACTION_FINISH))
                }
                if (session.endAtMillis - System.currentTimeMillis() > TICK_INTERVAL_MILLIS) {
                    alarm.set(AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() + TICK_INTERVAL_MILLIS,
                        action(context, ACTION_TICK))
                }
            }
            val duration = if (session?.kind == "break") 5 * 60_000L else 25 * 60_000L
            val remaining = when (session?.state) {
                "paused" -> session.remainingMillis
                "running" -> (session.endAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
                else -> duration
            }
            val ring = progressRing(context, (remaining.toFloat() / duration).coerceIn(0f, 1f))
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_dayflow)
                views.setImageViewBitmap(R.id.dayflow_widget_ring, ring)
                views.setTextViewText(R.id.dayflow_widget_title,
                    if (session?.kind == "break") "Break" else "Pomodoro")
                if (session == null) {
                    views.setViewVisibility(R.id.dayflow_widget_timer, View.GONE)
                    views.setViewVisibility(R.id.dayflow_widget_ready_time, View.VISIBLE)
                    views.setTextViewText(R.id.dayflow_widget_ready_time, "25:00")
                    views.setTextViewText(R.id.dayflow_widget_status, "Ready to focus")
                    views.setTextViewText(R.id.dayflow_widget_primary, "Start 25m")
                    views.setTextViewText(R.id.dayflow_widget_secondary, "Break 5m")
                    views.setOnClickPendingIntent(R.id.dayflow_widget_primary, action(context, ACTION_START))
                    views.setOnClickPendingIntent(R.id.dayflow_widget_secondary, action(context, ACTION_BREAK))
                } else {
                    views.setViewVisibility(R.id.dayflow_widget_timer, View.VISIBLE)
                    views.setViewVisibility(R.id.dayflow_widget_ready_time, View.GONE)
                    views.setChronometer(R.id.dayflow_widget_timer,
                        SystemClock.elapsedRealtime() + remaining, null, session.state == "running")
                    views.setChronometerCountDown(R.id.dayflow_widget_timer, true)
                    views.setTextViewText(R.id.dayflow_widget_status,
                        if (session.state == "paused") "Paused" else "In progress")
                    views.setTextViewText(R.id.dayflow_widget_primary,
                        if (session.state == "paused") "Resume" else "Pause")
                    views.setTextViewText(R.id.dayflow_widget_secondary, "Stop")
                    views.setOnClickPendingIntent(R.id.dayflow_widget_primary,
                        action(context, if (session.state == "paused") ACTION_RESUME else ACTION_PAUSE))
                    views.setOnClickPendingIntent(R.id.dayflow_widget_secondary, action(context, ACTION_STOP))
                }
                manager.updateAppWidget(id, views)
            }
        }

        private fun progressRing(context: Context, progress: Float): Bitmap {
            val density = context.resources.displayMetrics.density
            val pixels = (112 * density).toInt().coerceAtLeast(1)
            val stroke = 8 * density
            val bounds = RectF(stroke / 2, stroke / 2, pixels - stroke / 2, pixels - stroke / 2)
            val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                color = context.getColor(R.color.divider)
            }
            canvas.drawArc(bounds, 0f, 360f, false, paint)
            if (progress > 0f) {
                paint.color = context.getColor(R.color.brand_primary)
                canvas.drawArc(bounds, -90f, progress * 360f, false, paint)
            }
            return bitmap
        }
    }
}

fun updateDayflowWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, DayflowWidgetProvider::class.java))
    if (ids.isNotEmpty()) DayflowWidgetProvider.refreshAsync(context)
}
