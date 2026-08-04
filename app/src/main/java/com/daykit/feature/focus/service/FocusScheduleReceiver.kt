package com.daykit.feature.focus.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daykit.DayKitApplication
import com.daykit.core.permissions.AppLockPermissionChecker
import com.daykit.feature.applock.service.AppMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires at the start and end of a scheduled focus session.
 *
 * On start it makes sure the monitor is running so the session is actually
 * enforced — a schedule may block apps the user never PIN-locked, in which case
 * the service might not be up. On both edges it re-projects, which arms the next
 * occurrence (AlarmManager has no weekday recurrence, so nothing re-arms itself).
 *
 * Not exported: only this app's own alarms reach it.
 */
class FocusScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_START && action != ACTION_END) return

        val container = (context.applicationContext as DayKitApplication).container

        // The projection read/write touches SharedPreferences and Room, so hold
        // the broadcast open rather than racing receiver teardown.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (action == ACTION_START &&
                    AppLockPermissionChecker.hasUsageAccess(context)
                ) {
                    AppMonitorService.start(context)
                }

                // Re-projecting keeps a live window intact and arms the next
                // occurrence of everything that isn't running.
                val armed = container.focusScheduleRepository.reproject()
                FocusScheduleScheduler(context).arm(armed)
            } catch (t: Throwable) {
                // Never let a failed re-arm crash the alarm broadcast; the user
                // can still toggle the schedule to recover.
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_START = "com.daykit.focus.SESSION_START"
        const val ACTION_END = "com.daykit.focus.SESSION_END"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_START_MILLIS = "start_millis"
    }
}
