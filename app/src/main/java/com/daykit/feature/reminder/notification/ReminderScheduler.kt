package com.daykit.feature.reminder.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.daykit.feature.reminder.data.Reminder

/**
 * Schedules reminders with exact alarms via [AlarmManager] so they fire at the
 * scheduled minute even while the device is idle (Doze). Falls back to an inexact
 * allow-while-idle alarm when the app is not permitted to schedule exact alarms.
 */
class ReminderScheduler(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: Reminder) {
        if (reminder.completed) {
            cancel(reminder.reminderId)
            return
        }
        val triggerAtMillis = reminder.scheduledAtMillis
            .coerceAtLeast(System.currentTimeMillis() + 1_000L)
        val pendingIntent = alarmPendingIntent(reminder.reminderId)

        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            // No exact-alarm permission: best-effort, still wakes the device from idle.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    fun cancel(reminderId: String) {
        alarmManager.cancel(alarmPendingIntent(reminderId))
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun alarmPendingIntent(reminderId: String): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_FIRE)
            .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        return PendingIntent.getBroadcast(
            appContext,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
