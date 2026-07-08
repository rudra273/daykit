package com.rudra.daykit.feature.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.rudra.daykit.DayKitApplication
import com.rudra.daykit.R
import com.rudra.daykit.feature.habit.data.Habit
import com.rudra.daykit.feature.habit.data.HabitGoalType
import com.rudra.daykit.feature.habit.data.HabitLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

class HabitWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return HabitRemoteViewsFactory(applicationContext)
    }
}

private class HabitRemoteViewsFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<HabitWidgetRow> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        rows = runCatching {
            val repository = (context.applicationContext as DayKitApplication).container.habitRepository
            val dashboard = runBlocking { repository.observeDashboard().first() }
            dashboard.buildHabits.map { habit ->
                val log = dashboard.logFor(habit.habitId)
                HabitWidgetRow(
                    habit = habit,
                    log = log,
                    completed = isHabitComplete(habit, log),
                    progressPercent = (habitProgress(habit, log) * 100).roundToInt(),
                )
            }
        }.getOrDefault(emptyList())
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_habit_row)
        val fillInIntent = Intent().apply {
            putExtra(HabitCheckInWidgetProvider.EXTRA_HABIT_ID, row.habit.habitId)
            putExtra(HabitCheckInWidgetProvider.EXTRA_COMPLETED, !row.completed)
        }
        return RemoteViews(context.packageName, R.layout.widget_habit_row).apply {
            setTextViewText(R.id.widget_habit_status, if (row.completed) "✓" else "")
            setInt(
                R.id.widget_habit_status,
                "setBackgroundResource",
                if (row.completed) R.drawable.widget_habit_done_background else R.drawable.widget_habit_open_background,
            )
            setTextViewText(R.id.widget_habit_name, row.habit.name)
            setTextViewText(R.id.widget_habit_progress, "${row.progressPercent}%")
            setOnClickFillInIntent(R.id.widget_habit_row, fillInIntent)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.habit?.habitId?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

private data class HabitWidgetRow(
    val habit: Habit,
    val log: HabitLog?,
    val completed: Boolean,
    val progressPercent: Int,
)

private fun habitProgress(habit: Habit, log: HabitLog?): Float {
    if (log == null) return 0f
    return when (habit.goalType) {
        HabitGoalType.Time -> if (habit.targetMinutes <= 0) {
            if (log.completed) 1f else 0f
        } else {
            log.minutes.toFloat() / habit.targetMinutes
        }
        HabitGoalType.Count -> if (habit.targetCount <= 0) {
            if (log.completed) 1f else 0f
        } else {
            log.progressCount.toFloat() / habit.targetCount
        }
        HabitGoalType.Check -> if (log.completed) 1f else 0f
    }.coerceIn(0f, 1f)
}

private fun isHabitComplete(habit: Habit, log: HabitLog?): Boolean {
    if (log == null) return false
    return when (habit.goalType) {
        HabitGoalType.Time -> if (habit.targetMinutes <= 0) log.minutes > 0 || log.completed else log.minutes >= habit.targetMinutes
        HabitGoalType.Count -> if (habit.targetCount <= 0) log.progressCount > 0 || log.completed else log.progressCount >= habit.targetCount
        HabitGoalType.Check -> log.completed
    }
}
