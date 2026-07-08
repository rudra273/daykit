package com.rudra.daykit.feature.reminder.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rudra.daykit.DayKitApplication

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val reminderId = inputData.getString(KEY_REMINDER_ID).orEmpty()
        if (reminderId.isBlank()) return Result.success()

        val repository = (applicationContext as DayKitApplication).container.reminderRepository
        val reminder = repository.getReminder(reminderId) ?: return Result.success()
        if (reminder.completed) return Result.success()

        ReminderNotifier.show(applicationContext, reminder.reminderId, reminder.title)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
    }
}
