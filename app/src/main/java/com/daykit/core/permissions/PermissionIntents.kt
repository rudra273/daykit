package com.daykit.core.permissions

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings

object PermissionIntents {
    fun usageAccessSettings(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    fun overlaySettings(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        )
    }

    /**
     * Where the user grants "Alarms & reminders". Needed for focus schedules to
     * start on the minute — without it AlarmManager silently downgrades to an
     * inexact alarm that can drift by many minutes.
     */
    fun exactAlarmSettings(context: Context): Intent {
        return Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            "package:${context.packageName}".toUri(),
        )
    }
}
