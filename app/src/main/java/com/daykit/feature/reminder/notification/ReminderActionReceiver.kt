package com.daykit.feature.reminder.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.daykit.DayKitApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_COMPLETE && action != ACTION_SNOOZE && action != ACTION_DISMISSED) return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()
        if (reminderId.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as DayKitApplication).container.reminderRepository
                val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE, Long.MIN_VALUE)
                if (occurrence != Long.MIN_VALUE) {
                    when (action) {
                        ACTION_COMPLETE -> repository.markComplete(reminderId, occurrence)
                        ACTION_SNOOZE -> repository.snooze(
                            reminderId,
                            occurrence,
                            intent.getLongExtra(EXTRA_SNOOZE_DURATION, TEN_MINUTES),
                        )
                        ACTION_DISMISSED -> {
                            repository.getReminder(reminderId)
                                ?.takeIf { !it.completed && !it.paused && it.pendingOccurrenceMillis == occurrence }
                                ?.let { ReminderNotifier.show(context, it.reminderId, it.title, occurrence, alert = false) }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.daykit.reminder.COMPLETE"
        const val ACTION_SNOOZE = "com.daykit.reminder.SNOOZE"
        const val ACTION_DISMISSED = "com.daykit.reminder.DISMISSED"
        const val EXTRA_OCCURRENCE = "occurrence_millis"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_SNOOZE_DURATION = "snooze_duration"
        const val TEN_MINUTES = 10 * 60_000L
    }
}
