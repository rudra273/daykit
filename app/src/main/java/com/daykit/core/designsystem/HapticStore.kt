package com.daykit.core.designsystem

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

const val PREF_HAPTICS_ENABLED = "haptics_enabled"

/**
 * Haptic-feedback preference. Uses the same plain [SharedPreferences] store as
 * [ThemeModeStore] so reads are synchronous and changes propagate live via the
 * listener. Defaults to on.
 */
object HapticStore {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_APP_PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): Boolean =
        prefs(context).getBoolean(PREF_HAPTICS_ENABLED, true)

    fun set(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_HAPTICS_ENABLED, enabled).apply()
    }

    @Composable
    fun rememberHapticsEnabled(): State<Boolean> {
        val context = LocalContext.current
        val prefs = remember(context) { context.getSharedPreferences(PREF_APP_PREFS, Context.MODE_PRIVATE) }
        val state = remember { mutableStateOf(get(context)) }
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                if (key == PREF_HAPTICS_ENABLED) {
                    state.value = p.getBoolean(PREF_HAPTICS_ENABLED, true)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }
}
