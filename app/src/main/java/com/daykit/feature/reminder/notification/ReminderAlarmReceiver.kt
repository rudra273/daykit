package com.daykit.feature.reminder.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daykit.DayKitApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when an exact alarm scheduled by [ReminderScheduler] triggers. Loads the
 * reminder and posts its notification if it is still pending.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE && intent.action != ACTION_SNOOZE_FIRE) return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()
        if (reminderId.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as DayKitApplication)
                    .container.reminderRepository
                val show: (com.daykit.feature.reminder.data.Reminder) -> Unit = { reminder ->
                    ReminderNotifier.show(context, reminder.reminderId, reminder.title, reminder.scheduledAtMillis)
                }
                if (intent.action == ACTION_SNOOZE_FIRE) {
                    val snoozedUntil = intent.getLongExtra(EXTRA_SNOOZED_UNTIL, Long.MIN_VALUE)
                    if (snoozedUntil != Long.MIN_VALUE) repository.fireSnoozed(reminderId, snoozedUntil, show)
                } else {
                    repository.fireDue(reminderId, show)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.daykit.reminder.FIRE"
        const val ACTION_SNOOZE_FIRE = "com.daykit.reminder.SNOOZE_FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_SNOOZED_UNTIL = "snoozed_until"
    }
}
