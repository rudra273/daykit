package com.daykit.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.util.Log
import com.daykit.MainActivity
import com.daykit.DayKitApplication
import com.daykit.R
import com.daykit.feature.habit.data.HabitGoalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HabitCheckInWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            updateHabitWidgets(context)
            return
        }
        if (intent.action != ACTION_TOGGLE_HABIT) return
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
        val completed = intent.getBooleanExtra(EXTRA_COMPLETED, false)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val repository = (context.applicationContext as DayKitApplication).container.habitRepository
                val dashboard = repository.observeDashboard().first()
                // A row rendered yesterday must never modify today's log.
                if (intent.getStringExtra(EXTRA_DATE) != dashboard.today.toString()) {
                    updateHabitWidgets(context)
                    return@runCatching
                }
                val habit = dashboard.buildHabits.firstOrNull { it.habitId == habitId } ?: return@runCatching
                val log = dashboard.logFor(habitId)
                repository.saveDailyProgress(
                    habitId = habit.habitId,
                    date = dashboard.today,
                    minutes = if (completed && habit.goalType == HabitGoalType.Time) maxOf(habit.targetMinutes, log?.minutes ?: 0, 1) else 0,
                    progressCount = if (completed && habit.goalType == HabitGoalType.Count) maxOf(habit.targetCount, log?.progressCount ?: 0, 1) else 0,
                    completed = completed,
                    note = log?.note.orEmpty(),
                )
                updateHabitWidgets(context)
            }.onFailure { Log.w("HabitWidget", "Could not save check-in", it) }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_TOGGLE_HABIT = "com.daykit.widget.TOGGLE_HABIT"
        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_COMPLETED = "extra_completed"

        fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            appWidgetIds.forEach { appWidgetId ->
                val serviceIntent = Intent(context, HabitWidgetRemoteViewsService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                val toggleIntent = Intent(context, HabitCheckInWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_HABIT
                }
                val togglePendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                val views = RemoteViews(context.packageName, R.layout.widget_habit_checkin).apply {
                    val openApp = PendingIntent.getActivity(context, appWidgetId,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    setOnClickPendingIntent(R.id.widget_title, openApp)
                    setOnClickPendingIntent(R.id.widget_empty, openApp)
                    setRemoteAdapter(R.id.widget_habit_list, serviceIntent)
                    setEmptyView(R.id.widget_habit_list, R.id.widget_empty)
                    setPendingIntentTemplate(R.id.widget_habit_list, togglePendingIntent)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_habit_list)
            }
        }
    }
}
