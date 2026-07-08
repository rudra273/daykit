package com.daykit.feature.habit.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.daykit.feature.habit.data.Habit
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class HabitReminderScheduler(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun schedule(habit: Habit) {
        if (!habit.reminderEnabled || !habit.active) {
            cancel(habit.habitId)
            return
        }
        schedule(
            habitId = habit.habitId,
            habitName = habit.name,
            hour = habit.reminderHour,
            minute = habit.reminderMinute,
        )
    }

    fun schedule(habitId: String, habitName: String, hour: Int, minute: Int) {
        val delayMinutes = minutesUntil(hour, minute)
        val request = OneTimeWorkRequestBuilder<HabitReminderWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    HabitReminderWorker.KEY_HABIT_ID to habitId,
                    HabitReminderWorker.KEY_HABIT_NAME to habitName,
                    HabitReminderWorker.KEY_HOUR to hour,
                    HabitReminderWorker.KEY_MINUTE to minute,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(workName(habitId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(habitId: String) {
        workManager.cancelUniqueWork(workName(habitId))
    }

    private fun minutesUntil(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var target = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target).toMinutes().coerceAtLeast(1)
    }

    private fun workName(habitId: String): String = "habit_reminder_$habitId"
}
