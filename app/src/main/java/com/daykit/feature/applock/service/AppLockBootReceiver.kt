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
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                val container = (context.applicationContext as DayKitApplication).container
                val shouldMonitor = container.credentialRepository.hasCredential() &&
                    AppLockPermissionChecker.hasUsageAccess(context) &&
                    container.appLockRepository.getLockedPackages().isNotEmpty()
                if (shouldMonitor) {
                    AppMonitorService.start(context)
                }
            }
        }
    }
}
