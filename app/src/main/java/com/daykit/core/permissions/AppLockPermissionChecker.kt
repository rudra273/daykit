package com.rudra.daykit.core.permissions

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.rudra.daykit.feature.applock.service.AppLockAccessibilityService

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

    fun hasAccessibilityService(context: Context): Boolean {
        val expectedService = ComponentName(context, AppLockAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        return splitter.any { service ->
            ComponentName.unflattenFromString(service) == expectedService
        }
    }
}
