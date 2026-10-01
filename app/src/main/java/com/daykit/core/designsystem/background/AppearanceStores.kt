package com.daykit.core.designsystem.background

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.daykit.core.designsystem.PREF_APP_PREFS

/** How cards are drawn. Exactly one at a time, so Clay and Glass can never stack. */
enum class CardStyle(val label: String) {
    Flat("Flat"),
    Clay("Clay"),
    Glass("Liquid glass"),
}

/** The page behind every screen. [Custom] is the user's own image (see [CustomWallpaper]). */
enum class PageBackgroundKind(val label: String) {
    Plain("Plain"),
    Aurora("Aurora"),
    Dusk("Dusk"),
    Lagoon("Lagoon"),
    Custom("Your photo"),
}

const val PREF_CARD_STYLE = "card_style"
const val PREF_PAGE_BACKGROUND = "page_background"

/** Card style (Settings › Appearance). Defaults to [CardStyle.Flat]. */
object CardStyleStore : EnumPref<CardStyle>(PREF_CARD_STYLE, CardStyle.Flat, CardStyle::valueOf)

/** Page background (Settings › Appearance). Defaults to [PageBackgroundKind.Plain]. */
object PageBackgroundStore : EnumPref<PageBackgroundKind>(PREF_PAGE_BACKGROUND, PageBackgroundKind.Plain, PageBackgroundKind::valueOf)

/**
 * An enum in the plain [PREF_APP_PREFS] file: synchronous first read (no wrong
 * first frame) and live updates via the listener, like the theme mode.
 * An unknown stored name (e.g. from a newer build) falls back to [default].
 */
abstract class EnumPref<T : Enum<T>>(
    private val key: String,
    private val default: T,
    private val parse: (String) -> T,
) {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_APP_PREFS, Context.MODE_PRIVATE)

    private fun read(prefs: SharedPreferences): T =
        prefs.getString(key, null)?.let { runCatching { parse(it) }.getOrNull() } ?: default

    fun get(context: Context): T = read(prefs(context))

    fun set(context: Context, value: T) {
        prefs(context).edit().putString(key, value.name).apply()
    }

    @Composable
    fun rememberState(): State<T> {
        val context = LocalContext.current
        val prefs = remember(context) { prefs(context) }
        val state = remember(prefs) { mutableStateOf(read(prefs)) }
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, changed ->
                if (changed == key) state.value = read(p)
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }
}

/** The active [CardStyle]; provided by `DayKitTheme`. */
val LocalCardStyle = staticCompositionLocalOf { CardStyle.Flat }
