package com.daykit.feature.eventlight.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

const val PREF_EVENT_LIGHT = "event_light_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_COLOR_ARGB = "color_argb"
private const val KEY_THICKNESS_DP = "thickness_dp"
private const val KEY_BRIGHTNESS = "brightness"
private const val KEY_OPACITY = "opacity"
private const val KEY_TOP_ENABLED = "top_enabled"
private const val KEY_BOTTOM_ENABLED = "bottom_enabled"
private const val KEY_LEFT_ENABLED = "left_enabled"
private const val KEY_RIGHT_ENABLED = "right_enabled"

private const val DEFAULT_COLOR_ARGB = 0xFFFFFFFF.toInt()
private const val DEFAULT_THICKNESS_DP = 24f
private const val DEFAULT_BRIGHTNESS = 1f
private const val DEFAULT_OPACITY = 1f

data class EventLightSettings(
    val enabled: Boolean = false,
    val colorArgb: Int = DEFAULT_COLOR_ARGB,
    val thicknessDp: Float = DEFAULT_THICKNESS_DP,
    val brightness: Float = DEFAULT_BRIGHTNESS,
    // Independent of brightness: lets the border be see-through (down to fully
    // transparent) instead of only dimming the light's intensity.
    val opacity: Float = DEFAULT_OPACITY,
    val topEnabled: Boolean = true,
    val bottomEnabled: Boolean = true,
    val leftEnabled: Boolean = true,
    val rightEnabled: Boolean = true,
)

/**
 * Event Light settings persistence. Plain [SharedPreferences] (own file, not the
 * encrypted store) since none of these values are sensitive, following the same
 * synchronous-read + listener pattern as [com.daykit.core.designsystem.ThemeModeStore].
 */
object EventLightStore {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_EVENT_LIGHT, Context.MODE_PRIVATE)

    fun get(context: Context): EventLightSettings {
        val p = prefs(context)
        return EventLightSettings(
            enabled = p.getBoolean(KEY_ENABLED, false),
            colorArgb = p.getInt(KEY_COLOR_ARGB, DEFAULT_COLOR_ARGB),
            thicknessDp = p.getFloat(KEY_THICKNESS_DP, DEFAULT_THICKNESS_DP),
            brightness = p.getFloat(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS),
            opacity = p.getFloat(KEY_OPACITY, DEFAULT_OPACITY),
            topEnabled = p.getBoolean(KEY_TOP_ENABLED, true),
            bottomEnabled = p.getBoolean(KEY_BOTTOM_ENABLED, true),
            leftEnabled = p.getBoolean(KEY_LEFT_ENABLED, true),
            rightEnabled = p.getBoolean(KEY_RIGHT_ENABLED, true),
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setColor(context: Context, colorArgb: Int) {
        prefs(context).edit().putInt(KEY_COLOR_ARGB, colorArgb).apply()
    }

    fun setThickness(context: Context, thicknessDp: Float) {
        prefs(context).edit().putFloat(KEY_THICKNESS_DP, thicknessDp).apply()
    }

    fun setBrightness(context: Context, brightness: Float) {
        prefs(context).edit().putFloat(KEY_BRIGHTNESS, brightness).apply()
    }

    fun setOpacity(context: Context, opacity: Float) {
        prefs(context).edit().putFloat(KEY_OPACITY, opacity).apply()
    }

    fun setTopEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TOP_ENABLED, enabled).apply()
    }

    fun setBottomEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BOTTOM_ENABLED, enabled).apply()
    }

    fun setLeftEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LEFT_ENABLED, enabled).apply()
    }

    fun setRightEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RIGHT_ENABLED, enabled).apply()
    }

    @Composable
    fun rememberSettings(): State<EventLightSettings> {
        val context = LocalContext.current
        val prefs = remember(context) { prefs(context) }
        val state = remember { mutableStateOf(get(context)) }
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                state.value = get(context)
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }
}
