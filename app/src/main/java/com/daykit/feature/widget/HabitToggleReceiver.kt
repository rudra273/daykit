package com.daykit.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.daykit.DayKitApplication
import com.daykit.feature.habit.data.HabitGoalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles the habit widget's check-in taps. Not exported: the widget's PendingIntent
 * runs as DayKit, so it still reaches this receiver, but other apps cannot.
 */
class HabitToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HabitCheckInWidgetProvider.ACTION_TOGGLE_HABIT) return
        val habitId = intent.getStringExtra(HabitCheckInWidgetProvider.EXTRA_HABIT_ID) ?: return
        val completed = intent.getBooleanExtra(HabitCheckInWidgetProvider.EXTRA_COMPLETED, false)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val repository = (context.applicationContext as DayKitApplication).container.habitRepository
                val dashboard = repository.observeDashboard().first()
                // A row rendered yesterday must never modify today's log.
                if (intent.getStringExtra(HabitCheckInWidgetProvider.EXTRA_DATE) != dashboard.today.toString()) {
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
}
