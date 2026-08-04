package com.daykit.feature.focus.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.daykit.feature.focus.data.ArmedSchedule

/**
 * Arms focus schedules with exact alarms, modelled on
 * [com.daykit.feature.reminder.notification.ReminderScheduler].
 *
 * Two alarms per armed occurrence — one at the start, one at the end — each with
 * its own request code so they don't overwrite each other. Deliberately **not**
 * `setRepeating`: AlarmManager has no weekday recurrence, so the receiver
 * re-arms the next occurrence after each fire (see [FocusScheduleReceiver]).
 *
 * The end alarm is best-effort. `setExactAndAllowWhileIdle` is OS rate-limited
 * to roughly once per 9–15 minutes per app while dozing, so a short session's
 * end alarm can be deferred. Enforcement therefore also compares against
 * `endMillis` from the projection rather than trusting this alarm to land —
 * see `FocusScheduleCache.activeWindows`.
 */
class FocusScheduleScheduler(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun arm(schedules: List<ArmedSchedule>) {
        schedules.forEach { armed ->
            schedule(armed.startMillis, startIntent(armed))
            schedule(armed.endMillis, endIntent(armed))
        }
    }

    fun cancel(armed: ArmedSchedule) {
        alarmManager.cancel(startIntent(armed))
        alarmManager.cancel(endIntent(armed))
    }

    private fun schedule(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        // An alarm in the past fires immediately, which for a start alarm would
        // re-block an app whose window has already closed.
        if (triggerAtMillis <= System.currentTimeMillis()) return
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            // No exact-alarm permission: best-effort, may drift by minutes. The
            // Focus screen warns the user rather than pretending it's exact.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    private fun startIntent(armed: ArmedSchedule): PendingIntent =
        pendingIntent(FocusScheduleReceiver.ACTION_START, armed, requestCode(armed, start = true))

    private fun endIntent(armed: ArmedSchedule): PendingIntent =
        pendingIntent(FocusScheduleReceiver.ACTION_END, armed, requestCode(armed, start = false))

    // Start and end must not collide, so the end code is offset. Derived from
    // scheduleId only (not the occurrence) so re-arming replaces the old alarm
    // via FLAG_UPDATE_CURRENT instead of leaking one per week.
    private fun requestCode(armed: ArmedSchedule, start: Boolean): Int =
        armed.scheduleId.hashCode() * 31 + if (start) 1 else 2

    private fun pendingIntent(action: String, armed: ArmedSchedule, requestCode: Int): PendingIntent {
        val intent = Intent(appContext, FocusScheduleReceiver::class.java)
            .setAction(action)
            .putExtra(FocusScheduleReceiver.EXTRA_SCHEDULE_ID, armed.scheduleId)
            .putExtra(FocusScheduleReceiver.EXTRA_START_MILLIS, armed.startMillis)
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
