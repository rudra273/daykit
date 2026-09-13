package com.daykit.feature.reminder.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.daykit.DayKitApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-arm alarms after exact-alarm access is granted or the device clock changes. */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED &&
            intent.action != Intent.ACTION_TIME_CHANGED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as DayKitApplication).container.reminderRepository.restoreAlarms {
                    ReminderNotifier.show(context, it.reminderId, it.title, it.scheduledAtMillis, alert = false)
                }
            } catch (error: Exception) {
                Log.w("ReminderRestore", "Could not restore reminder alarms", error)
            } finally {
                pending.finish()
            }
        }
    }
}
