package com.daykit.feature.applock.domain

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.daykit.core.security.DayKitDeviceAdmin
import com.daykit.feature.applock.data.AppLockRepository

/**
 * Anti-theft protection: DayKit as a device admin (so it can't be uninstalled)
 * plus the Settings app locked behind the master credential (so whoever is holding
 * the unlocked phone can't switch the admin off, revoke Usage Access, or force-stop us).
 *
 * The owner must always have a way out, or Play treats this as an app that resists
 * removal. The normal path is the PIN-gated switch in Security & Privacy; the
 * forgot-PIN path is [disable] behind the phone's own screen lock in LockActivity.
 * Never expose [disable] without one of those two checks, or a thief can use it.
 */
object AntiTheftProtection {
    const val SETTINGS_LABEL = "Settings"

    private fun component(context: Context) = ComponentName(context, DayKitDeviceAdmin::class.java)

    private fun devicePolicyManager(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isActive(context: Context): Boolean = devicePolicyManager(context).isAdminActive(component(context))

    /**
     * The forgot-PIN escape is gated on the phone's screen lock, so without one there
     * is nothing a thief doesn't already have — don't offer protection we can't back.
     */
    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    fun activationIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Stops someone else from uninstalling DayKit. Turn it off any time from " +
                    "DayKit > Settings > Security & Privacy.",
            )
    }

    suspend fun setSettingsLocked(
        appLockRepository: AppLockRepository,
        settingsPackage: String,
        locked: Boolean,
    ) {
        // Drop a stale "Settings" entry left by a different resolver result (e.g. after an OEM update).
        appLockRepository.getLockedApps()
            .filter { app -> app.label == SETTINGS_LABEL && app.packageName != settingsPackage }
            .forEach { app -> appLockRepository.setLocked(app.packageName, app.label, locked = false) }

        appLockRepository.setLocked(settingsPackage, SETTINGS_LABEL, locked)
    }

    /** Unlocks Settings and removes the admin. Callers must have verified the owner first. */
    suspend fun disable(context: Context, appLockRepository: AppLockRepository) {
        setSettingsLocked(appLockRepository, SettingsPackageResolver.resolve(context), locked = false)
        val dpm = devicePolicyManager(context)
        if (dpm.isAdminActive(component(context))) dpm.removeActiveAdmin(component(context))
    }
}
