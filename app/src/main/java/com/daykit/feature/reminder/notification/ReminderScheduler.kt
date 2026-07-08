package com.daykit.feature.reminder.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.daykit.feature.reminder.data.Reminder
import java.util.concurrent.TimeUnit

class ReminderScheduler(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun schedule(reminder: Reminder) {
        if (reminder.completed) {
            cancel(reminder.reminderId)
            return
        }
        val delayMillis = (reminder.scheduledAtMillis - System.currentTimeMillis()).coerceAtLeast(1_000L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_REMINDER_ID to reminder.reminderId,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(workName(reminder.reminderId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(reminderId: String) {
        workManager.cancelUniqueWork(workName(reminderId))
    }

    private fun workName(reminderId: String): String = "reminder_$reminderId"
}
