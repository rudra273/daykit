package com.daykit.feature.applock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daykit.DayKitApplication
import com.daykit.core.permissions.AppLockPermissionChecker

/**
 * Restarts app-lock monitoring after a reboot or app update. Without this the
 * service only came back the next time the user opened DayKit, leaving locked
 * apps unprotected in between.
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
        val shouldMonitor = container.credentialRepository.hasCredential() &&
            AppLockPermissionChecker.hasUsageAccess(context) &&
            container.appLockRepository.getLockedPackages().isNotEmpty()
        if (shouldMonitor) {
            AppMonitorService.start(context)
        }
    }
}
