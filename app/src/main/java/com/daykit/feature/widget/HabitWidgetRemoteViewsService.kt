package com.daykit.feature.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.daykit.DayKitApplication
import com.daykit.R
import com.daykit.feature.habit.data.Habit
import com.daykit.feature.habit.data.HabitLog
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
                    date = dashboard.today.toString(),
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
            putExtra(HabitCheckInWidgetProvider.EXTRA_DATE, row.date)
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
            setContentDescription(R.id.widget_habit_row, context.getString(
                if (row.completed) R.string.widget_habit_uncheck else R.string.widget_habit_check,
                row.habit.name, row.progressPercent))
            setOnClickFillInIntent(R.id.widget_habit_row, fillInIntent)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = rows.getOrNull(position)?.habit?.habitId?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

private data class HabitWidgetRow(
    val date: String,
    val habit: Habit,
    val log: HabitLog?,
    val completed: Boolean,
    val progressPercent: Int,
)
