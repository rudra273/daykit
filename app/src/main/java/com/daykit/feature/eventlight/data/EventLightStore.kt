package com.daykit.feature.eventlight.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

const val PREF_EVENT_LIGHT = "event_light_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_THICKNESS_DP = "thickness_dp"
private const val KEY_COLOR_TEMP = "color_temp"

const val EVENT_LIGHT_MIN_THICKNESS_DP = 6f
const val EVENT_LIGHT_MAX_THICKNESS_DP = 36f
const val EVENT_LIGHT_DEFAULT_THICKNESS_DP = 14f
const val EVENT_LIGHT_DEFAULT_COLOR_TEMP = 0.5f

/**
 * macOS Edge Light color temperature stops:
 * 0.0 = warm amber/yellow, 0.5 = neutral white, 1.0 = cool ice blue.
 */
val EventLightGradientStops: List<Color> = listOf(
    Color(0xFFFFAD3B), // 0.00 — warm golden amber
    Color(0xFFFFD89E), // 0.25 — warm soft cream
    Color(0xFFFFFFFF), // 0.50 — pure luminous white
    Color(0xFFE0EEFF), // 0.75 — cool daylight white
    Color(0xFFBCD8FF), // 1.00 — cool ice blue
)

fun edgeLightTintColor(colorTemp: Float): Color {
    val t = colorTemp.coerceIn(0f, 1f)
    val scaled = t * (EventLightGradientStops.lastIndex)
    val index = scaled.toInt().coerceIn(0, EventLightGradientStops.lastIndex - 1)
    val fraction = scaled - index
    return lerp(EventLightGradientStops[index], EventLightGradientStops[index + 1], fraction)
}

data class EventLightSettings(
    val enabled: Boolean = false,
    val thicknessDp: Float = EVENT_LIGHT_DEFAULT_THICKNESS_DP,
    val colorTemp: Float = EVENT_LIGHT_DEFAULT_COLOR_TEMP,
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
        val rawThickness = p.getFloat(KEY_THICKNESS_DP, EVENT_LIGHT_DEFAULT_THICKNESS_DP)
        val rawTemp = p.getFloat(KEY_COLOR_TEMP, EVENT_LIGHT_DEFAULT_COLOR_TEMP)
        return EventLightSettings(
            enabled = p.getBoolean(KEY_ENABLED, false),
            thicknessDp = rawThickness.coerceIn(EVENT_LIGHT_MIN_THICKNESS_DP, EVENT_LIGHT_MAX_THICKNESS_DP),
            colorTemp = rawTemp.coerceIn(0f, 1f),
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setThickness(context: Context, thicknessDp: Float) {
        val clamped = thicknessDp.coerceIn(EVENT_LIGHT_MIN_THICKNESS_DP, EVENT_LIGHT_MAX_THICKNESS_DP)
        prefs(context).edit().putFloat(KEY_THICKNESS_DP, clamped).apply()
    }

    fun setColorTemp(context: Context, colorTemp: Float) {
        val clamped = colorTemp.coerceIn(0f, 1f)
        prefs(context).edit().putFloat(KEY_COLOR_TEMP, clamped).apply()
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
