package com.daykit.feature.applock.domain

import android.content.Context
import android.os.Build

object SamsungSecureFolderSupport {
    const val PACKAGE_NAME = "com.samsung.knox.securefolder"

    fun isAvailable(context: Context): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
            runCatching { context.packageManager.getApplicationInfo(PACKAGE_NAME, 0) }.isSuccess
    }

    fun shouldBypassLock(packageName: String, className: String?, settingsPackage: String): Boolean {
        return packageName == PACKAGE_NAME ||
            packageName == settingsPackage &&
            className?.contains("KnoxWorkChallengeActivity", ignoreCase = true) == true
    }
}
