package com.daykit.core.permissions

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings

object PermissionIntents {
    fun usageAccessSettings(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    fun accessibilitySettings(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    fun overlaySettings(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        )
    }
}
