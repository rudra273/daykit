package com.rudra.daykit.feature.reminder.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.rudra.daykit.DayKitApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_COMPLETE && action != ACTION_DISMISSED) return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()
        if (reminderId.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as DayKitApplication).container.reminderRepository
                when (action) {
                    ACTION_COMPLETE -> {
                        repository.markComplete(reminderId)
                        ReminderScheduler(context).cancel(reminderId)
                        NotificationManagerCompat.from(context).cancel(ReminderNotifier.notificationId(reminderId))
                    }

                    ACTION_DISMISSED -> {
                        val reminder = repository.getReminder(reminderId)
                        if (reminder != null && !reminder.completed) {
                            ReminderNotifier.show(context, reminder.reminderId, reminder.title)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.rudra.daykit.reminder.COMPLETE"
        const val ACTION_DISMISSED = "com.rudra.daykit.reminder.DISMISSED"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
