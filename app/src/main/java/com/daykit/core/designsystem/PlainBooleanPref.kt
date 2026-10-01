package com.daykit.core.designsystem

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * A boolean in the plain [PREF_APP_PREFS] file, shared by the appearance-type
 * stores (e.g. [HapticStore]). Reads are synchronous, so the first frame
 * is already right, and a change made anywhere propagates live via the listener.
 */
internal class PlainBooleanPref(private val key: String, private val default: Boolean) {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_APP_PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): Boolean = prefs(context).getBoolean(key, default)

    fun set(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    @Composable
    fun rememberState(): State<Boolean> {
        val context = LocalContext.current
        val prefs = remember(context) { prefs(context) }
        val state = remember(prefs) { mutableStateOf(prefs.getBoolean(key, default)) }
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, changed ->
                if (changed == key) state.value = p.getBoolean(key, default)
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }
}
