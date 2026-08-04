package com.daykit.feature.applock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daykit.DayKitApplication
import com.daykit.core.permissions.AppLockPermissionChecker
import com.daykit.feature.focus.service.FocusScheduleScheduler
import com.daykit.feature.reminder.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restarts app-lock monitoring and re-arms reminder alarms after a reboot or app
 * update. Without this the service only came back the next time the user opened
 * DayKit, leaving locked apps unprotected in between.
 *
 * Android clears every [android.app.AlarmManager] alarm on both boot and package
 * replacement, so pending reminders must be rescheduled here too — otherwise they
 * silently never fire again while still showing as pending in the UI.
 */
class AppLockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // This receiver is exported (required to receive BOOT_COMPLETED), so a
        // malicious app could send it a spoofed intent. Guard strictly: only the
        // two system broadcasts we registered for are honored — anything else is
        // ignored. Even if spoofed, the only effect is re-checking real state and
        // starting the monitor, which itself requires the app's own permissions.
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val container = (context.applicationContext as DayKitApplication).container
        // PIN-locked apps need a credential to be challengeable at all, so their
        // monitoring is gated on hasCredential(). A focus block is not — it is
        // enforced by a countdown that ignores the PIN, so it must survive a
        // reboot even for a user who never set one. Gating both on hasCredential()
        // silently dropped enforcement for exactly that case.
        val hasUsageAccess = AppLockPermissionChecker.hasUsageAccess(context)
        val hasPinLockedApps = container.credentialRepository.hasCredential() &&
            container.appLockRepository.getLockedPackages().isNotEmpty()
        val hasActiveFocusBlocks = container.focusRepository.activeFocusPackages().isNotEmpty()
        // Read the prefs projection, not the DB: this runs before any unlock.
        val hasArmedSessions = container.focusScheduleCache.getArmed().isNotEmpty()
        if (hasUsageAccess && (hasPinLockedApps || hasActiveFocusBlocks || hasArmedSessions)) {
            AppMonitorService.start(context)
        }

        // Reminders live in the encrypted DB, so this read can block: hold the
        // broadcast open with goAsync() instead of racing the receiver teardown.
        // Reminder titles are not sensitive-key material, so this works while the
        // vault is still locked.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val scheduler = ReminderScheduler(context)
                container.reminderRepository.getPendingFutureReminders()
                    .forEach(scheduler::schedule)

                // Focus schedules lose their alarms on boot too. Re-project first
                // so a window that elapsed while powered off is replaced by the
                // next occurrence rather than being armed in the past.
                val armed = container.focusScheduleRepository.reproject()
                FocusScheduleScheduler(context).arm(armed)
            } catch (t: Throwable) {
                // Never let a failed reschedule crash the boot broadcast — the
                // user can still re-arm a reminder by editing it.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
