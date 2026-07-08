package com.daykit.feature.applock.domain

import android.content.Context
import android.content.Intent
import android.provider.Settings

object SettingsPackageResolver {
    private const val FALLBACK_SETTINGS_PACKAGE = "com.android.settings"

    fun resolve(context: Context): String {
        val packageManager = context.packageManager
        val intent = Intent(Settings.ACTION_SETTINGS)

        return packageManager.resolveActivity(intent, 0)
            ?.activityInfo
            ?.packageName
            ?: FALLBACK_SETTINGS_PACKAGE
    }
}
