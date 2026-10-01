package com.daykit.core.designsystem

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf

const val PREF_CLAY_CARDS = "clay_cards"

/** "Clay cards" appearance preference (Settings › Appearance). Defaults to on. */
object ClayStore {
    private val pref = PlainBooleanPref(PREF_CLAY_CARDS, default = true)

    fun get(context: Context): Boolean = pref.get(context)

    fun set(context: Context, enabled: Boolean) = pref.set(context, enabled)

    @Composable
    fun rememberClayEnabled(): State<Boolean> = pref.rememberState()
}

/** Whether cards draw the clay lift; provided by [DayKitTheme]. */
val LocalClayCards = staticCompositionLocalOf { true }
