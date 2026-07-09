package com.daykit.core.designsystem

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

enum class ThemeMode { SYSTEM, LIGHT, DARK }

const val PREF_APP_PREFS = "app_prefs"
const val PREF_THEME_MODE = "theme_mode"

// Stale keys from the old glassmorphism theme; cleaned up on first read.
private val STALE_THEME_KEYS = listOf("app_theme", "app_background", "glass_opacity", "glass_blur")

/**
 * Theme-mode persistence. Uses plain [SharedPreferences] (not the encrypted
 * SecureSettingRepository) so all four theme entry points — including the
 * non-Activity overlay window — get a synchronous first-frame read with no
 * wrong-theme flash, and a change made anywhere propagates live via the listener.
 */
object ThemeModeStore {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_APP_PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): ThemeMode {
        val prefs = prefs(context)
        if (prefs.contains(STALE_THEME_KEYS.first()) || prefs.contains(STALE_THEME_KEYS.last())) {
            prefs.edit().apply { STALE_THEME_KEYS.forEach { remove(it) } }.apply()
        }
        val name = prefs.getString(PREF_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun set(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(PREF_THEME_MODE, mode.name).apply()
    }

    @Composable
    fun rememberThemeMode(): State<ThemeMode> {
        val context = LocalContext.current
        val prefs = remember(context) { context.getSharedPreferences(PREF_APP_PREFS, Context.MODE_PRIVATE) }
        val state = remember { mutableStateOf(get(context)) }
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                if (key == PREF_THEME_MODE) {
                    val name = p.getString(PREF_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
                    state.value = runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }
}

/** Resolved dark/light boolean for components that need it outside the color scheme. */
@Composable
@ReadOnlyComposable
fun isAppInDarkTheme(): Boolean = LocalExtendedColors.current.isDark

/** Convenience accessor for the semantic color layer. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable @ReadOnlyComposable get() = LocalExtendedColors.current

@Composable
fun DayKitTheme(content: @Composable () -> Unit) {
    val mode by ThemeModeStore.rememberThemeMode()
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DayKitDarkColorScheme else DayKitLightColorScheme
    val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors

    val context = LocalContext.current
    val window = (context as? Activity)?.window
    if (window != null) {
        DisposableEffect(window, darkTheme) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            onDispose {}
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DayKitTypography,
            shapes = DayKitShapes,
        ) {
            Surface(color = colorScheme.background, content = content)
        }
    }
}
