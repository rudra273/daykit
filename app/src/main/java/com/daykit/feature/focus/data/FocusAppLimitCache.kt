package com.daykit.feature.focus.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Plain-SharedPreferences projection of active (enabled) daily app usage limits.
 *
 * Stored as a JSON object mapping packageName -> dailyLimitMinutes.
 * Read synchronously by [com.daykit.feature.applock.service.AppMonitorService]
 * so background enforcement never blocks on SQLCipher or Keystore unlocking.
 */
class FocusAppLimitCache(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEnabledLimits(): Map<String, Int> {
        val raw = prefs.getString(KEY_LIMITS, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    put(pkg, json.getInt(pkg))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun putEnabledLimits(limits: Map<String, Int>) {
        val json = JSONObject()
        limits.forEach { (pkg, limitMinutes) ->
            json.put(pkg, limitMinutes)
        }
        prefs.edit(commit = true) {
            putString(KEY_LIMITS, json.toString())
        }
    }

    fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val PREFS_NAME = "focus_app_limits_cache"
        const val KEY_LIMITS = "enabled_limits"
    }
}
