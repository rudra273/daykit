package com.daykit.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.daykit.DayKitApplication
import com.daykit.R
import com.daykit.feature.habit.data.HabitGoalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class HabitCheckInWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE_HABIT) return
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
        val completed = intent.getBooleanExtra(EXTRA_COMPLETED, false)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val repository = (context.applicationContext as DayKitApplication).container.habitRepository
                val dashboard = repository.observeDashboard().first()
                val habit = dashboard.buildHabits.firstOrNull { it.habitId == habitId } ?: return@runCatching
                repository.saveDailyProgress(
                    habitId = habit.habitId,
                    date = LocalDate.now(),
                    minutes = if (completed && habit.goalType == HabitGoalType.Time) habit.targetMinutes.coerceAtLeast(1) else 0,
                    progressCount = if (completed && habit.goalType == HabitGoalType.Count) habit.targetCount.coerceAtLeast(1) else 0,
                    completed = completed,
                    note = "",
                )
                updateHabitWidgets(context)
            }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_TOGGLE_HABIT = "com.daykit.widget.TOGGLE_HABIT"
        const val EXTRA_HABIT_ID = "extra_habit_id"
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
                    setRemoteAdapter(R.id.widget_habit_list, serviceIntent)
                    setEmptyView(R.id.widget_habit_list, R.id.widget_empty)
                    setPendingIntentTemplate(R.id.widget_habit_list, togglePendingIntent)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
