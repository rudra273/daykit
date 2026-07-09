package com.daykit.core.data

import android.content.Context
import androidx.core.content.edit

/**
 * Plain SharedPreferences mirror of non-secret boolean settings so screens can
 * render instantly instead of waiting on the encrypted database + Keystore.
 * The encrypted database stays the source of truth; every read/write of a
 * boolean setting refreshes this cache.
 */
class SettingFlagCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun contains(key: String): Boolean = prefs.contains(key)

    fun get(key: String): Boolean? {
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    }

    fun put(key: String, value: Boolean?) {
        prefs.edit {
            if (value == null) remove(key) else putBoolean(key, value)
        }
    }

    private companion object {
        const val PREFS_NAME = "daykit_setting_flags"
    }
}
