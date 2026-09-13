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
        cancel(reminder.reminderId)
        if (!reminder.completed && !reminder.paused && reminder.pendingOccurrenceMillis != reminder.scheduledAtMillis) {
            scheduleAt(reminder.scheduledAtMillis, alarmPendingIntent(reminder.reminderId))
        }
        val snoozeAt = reminder.snoozedUntilMillis
        if (!reminder.completed && !reminder.paused && reminder.pendingOccurrenceMillis != null && snoozeAt != null) {
            scheduleAt(snoozeAt, snoozePendingIntent(reminder.reminderId, snoozeAt))
        }
    }

    private fun scheduleAt(requestedMillis: Long, pendingIntent: PendingIntent) {
        val triggerAtMillis = requestedMillis.coerceAtLeast(System.currentTimeMillis() + 1_000L)

        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: SecurityException) {
                // Permission can change between checking and scheduling.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
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
        alarmManager.cancel(snoozePendingIntent(reminderId, 0L))
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun alarmPendingIntent(reminderId: String): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_FIRE)
            .setData(android.net.Uri.parse("daykit://reminder/$reminderId"))
            .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        return PendingIntent.getBroadcast(
            appContext,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozePendingIntent(reminderId: String, snoozedUntilMillis: Long): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_SNOOZE_FIRE)
            .setData(android.net.Uri.parse("daykit://reminder/$reminderId/snooze"))
            .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
            .putExtra(ReminderAlarmReceiver.EXTRA_SNOOZED_UNTIL, snoozedUntilMillis)
        return PendingIntent.getBroadcast(
            appContext,
            reminderId.hashCode() xor SNOOZE_REQUEST_CODE_MASK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val SNOOZE_REQUEST_CODE_MASK = 0x51_00_2E
    }
}
