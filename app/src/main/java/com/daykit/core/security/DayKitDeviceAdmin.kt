package com.daykit.core.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Admin receiver for anti-theft protection — registered so the app cannot
 * be uninstalled without first deactivating admin in Settings.
 *
 * Since we also lock the Settings app when this is active, users must enter the
 * DayKit PIN (or, if forgotten, the phone's screen lock) to reach the admin
 * deactivation page. See AntiTheftProtection.
 */
class DayKitDeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {}
    override fun onDisabled(context: Context, intent: Intent) {}
}
