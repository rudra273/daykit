package com.daykit.core.permissions

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.provider.Settings

object AppLockPermissionChecker {
    fun check(context: Context): AppLockPermissionState {
        return AppLockPermissionState(
            usageAccess = hasUsageAccess(context),
            overlay = Settings.canDrawOverlays(context),
        )
    }

    /**
     * Whether exact alarms may be scheduled. Only focus schedules need this —
     * it is deliberately **not** part of [AppLockPermissionState.allGranted],
     * because App Lock itself works fine without it and gating the onboarding
     * flow on it would demand a permission most users don't need.
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return alarmManager?.canScheduleExactAlarms() != false
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
