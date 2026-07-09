package com.daykit.core.permissions

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
