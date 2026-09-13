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
        if (action != ACTION_COMPLETE) return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()
        if (reminderId.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as DayKitApplication).container.reminderRepository
                if (action == ACTION_COMPLETE) {
                    val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE, Long.MIN_VALUE)
                    if (occurrence != Long.MIN_VALUE) repository.markComplete(reminderId, occurrence)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.daykit.reminder.COMPLETE"
        const val EXTRA_OCCURRENCE = "occurrence_millis"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
