package com.daykit.core.designsystem

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

const val PREF_HAPTICS_ENABLED = "haptics_enabled"

/** Haptic-feedback preference, in the same plain store as [ThemeModeStore]. Defaults to on. */
object HapticStore {
    private val pref = PlainBooleanPref(PREF_HAPTICS_ENABLED, default = true)

    fun get(context: Context): Boolean = pref.get(context)

    fun set(context: Context, enabled: Boolean) = pref.set(context, enabled)

    @Composable
    fun rememberHapticsEnabled(): State<Boolean> = pref.rememberState()
}
