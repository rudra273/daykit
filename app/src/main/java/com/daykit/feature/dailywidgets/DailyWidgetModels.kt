package com.daykit.feature.dailywidgets

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.daykit.feature.weather.WeatherPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class DailyWidgetStyle(val label: String) {
    LIQUID_GLASS("Liquid Glass"),
    SOLID("Solid"),
}

enum class DailyWidgetType(
    val id: String,
    val title: String,
    val subtitle: String,
    val style: DailyWidgetStyle,
    val sizeLabel: String,
    /** width / height ratio */
    val aspectRatio: Float,
    /** default width as fraction of wallpaper screen width */
    val defaultWidthFraction: Float,
) {
    GLASS_WEATHER(
        id = "glass_weather",
        title = "Glass Weather",
        subtitle = "Live atmospheric clouds, rain & sky",
        style = DailyWidgetStyle.LIQUID_GLASS,
        sizeLabel = "2 × 2",
        aspectRatio = 1.0f,
        defaultWidthFraction = 0.62f,
    ),
    SOLAR_ARC(
        id = "solar_arc",
        title = "Solar Arc",
        subtitle = "Sunrise & sunset horizon curve",
        style = DailyWidgetStyle.LIQUID_GLASS,
        sizeLabel = "2 × 2 · 4 × 2",
        aspectRatio = 1.0f,
        defaultWidthFraction = 0.62f,
    ),
    RADIAL_DIAL_CLOCK(
        id = "radial_clock",
        title = "Radial Dial Clock",
        subtitle = "Curved hour dial with glowing arc",
        style = DailyWidgetStyle.LIQUID_GLASS,
        sizeLabel = "4 × 2",
        aspectRatio = 1.92f,
        defaultWidthFraction = 0.88f,
    ),
    LIQUID_GLASS_CLOCK(
        id = "glass_clock",
        title = "Liquid 7-Seg Clock",
        subtitle = "Refractive frosted glass digital clock",
        style = DailyWidgetStyle.LIQUID_GLASS,
        sizeLabel = "4 × 2",
        aspectRatio = 2.15f,
        defaultWidthFraction = 0.88f,
    ),
    STEPS_BAR_CHART(
        id = "steps_chart",
        title = "Run & Walk Tracker",
        subtitle = "Start/stop session steps, km & history",
        style = DailyWidgetStyle.SOLID,
        sizeLabel = "4 × 2",
        aspectRatio = 1.72f,
        defaultWidthFraction = 0.88f,
    ),
    BENTO_BATTERY(
        id = "bento_battery",
        title = "Batteries",
        subtitle = "Minimalist power & charging bars",
        style = DailyWidgetStyle.SOLID,
        sizeLabel = "2 × 2",
        aspectRatio = 1.0f,
        defaultWidthFraction = 0.56f,
    ),
    BENTO_AGENDA(
        id = "bento_agenda",
        title = "Day Agenda",
        subtitle = "Editorial date & today's schedule",
        style = DailyWidgetStyle.SOLID,
        sizeLabel = "4 × 2",
        aspectRatio = 1.96f,
        defaultWidthFraction = 0.88f,
    );

    companion object {
        fun fromId(id: String): DailyWidgetType? = entries.firstOrNull { it.id == id }
    }
}

data class WallpaperWidgetSlot(
    val uid: String = UUID.randomUUID().toString().take(8),
    val type: DailyWidgetType,
    val centerX: Float = 0.50f,
    val centerY: Float = 0.28f,
    val widthFraction: Float = type.defaultWidthFraction,
    val aspectRatioOverride: Float? = null,
) {
    val effectiveAspectRatio: Float
        get() = aspectRatioOverride ?: type.aspectRatio
}

/**
 * Persists multi-widget wallpaper placements inside [WeatherPrefs.prefs] so
 * [com.daykit.feature.wallpaper.WeatherGlassWallpaperService] picks up changes immediately.
 */
object WallpaperLayoutPrefs {
    private const val KEY_SLOTS_JSON = "wallpaper_widget_slots_json"

    fun loadSlots(context: Context): List<WallpaperWidgetSlot> {
        val prefs = WeatherPrefs.prefs(context)
        val raw = prefs.getString(KEY_SLOTS_JSON, null)
        if (raw == null) {
            // Migrate legacy single-weather setting seamlessly
            return if (WeatherPrefs.wallpaperWeatherEnabled(context)) {
                listOf(
                    WallpaperWidgetSlot(
                        uid = "weather_default",
                        type = DailyWidgetType.GLASS_WEATHER,
                        centerX = WeatherPrefs.wallpaperCubeX(context),
                        centerY = WeatherPrefs.wallpaperCubeY(context),
                        widthFraction = WeatherPrefs.wallpaperCubeScale(context),
                    ),
                )
            } else {
                emptyList()
            }
        }
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val type = DailyWidgetType.fromId(obj.optString("type")) ?: continue
                    val arOverride = if (obj.has("ar")) {
                        obj.optDouble("ar", Double.NaN).takeIf { !it.isNaN() }?.toFloat()?.coerceIn(0.75f, 2.4f)
                    } else {
                        null
                    }
                    add(
                        WallpaperWidgetSlot(
                            uid = obj.optString("uid", "w_$i"),
                            type = type,
                            centerX = obj.optDouble("cx", 0.5).toFloat().coerceIn(0.10f, 0.90f),
                            centerY = obj.optDouble("cy", 0.3).toFloat().coerceIn(0.08f, 0.92f),
                            widthFraction = obj.optDouble("wf", type.defaultWidthFraction.toDouble())
                                .toFloat()
                                .coerceIn(0.32f, 0.94f),
                            aspectRatioOverride = arOverride,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveSlots(context: Context, slots: List<WallpaperWidgetSlot>) {
        val arr = JSONArray()
        for (slot in slots) {
            val obj = JSONObject()
                .put("uid", slot.uid)
                .put("type", slot.type.id)
                .put("cx", slot.centerX.coerceIn(0.10f, 0.90f))
                .put("cy", slot.centerY.coerceIn(0.08f, 0.92f))
                .put("wf", slot.widthFraction.coerceIn(0.32f, 0.94f))
            if (slot.aspectRatioOverride != null) {
                obj.put("ar", slot.aspectRatioOverride.coerceIn(0.75f, 2.4f))
            }
            arr.put(obj)
        }
        WeatherPrefs.prefs(context).edit()
            .putString(KEY_SLOTS_JSON, arr.toString())
            .apply()
    }
}

data class SolarArcState(
    val sunriseText: String,
    val sunsetText: String,
    /** 0.0..1.0 across the full 24h day where 0.25 = sunrise, 0.50 = solar noon, 0.75 = sunset */
    val curveProgress: Float,
    val isDaytime: Boolean,
)

data class StepRunSession(
    val id: String,
    val startMillis: Long,
    val endMillis: Long,
    val steps: Int,
    val isActive: Boolean = false,
) {
    val distanceKm: Float get() = steps * 0.00076f
    val durationMinutes: Int get() = (((endMillis - startMillis).coerceAtLeast(0L)) / 60_000L).toInt()
}

data class StepsChartState(
    val isRunning: Boolean,
    val selectedIndex: Int,
    val sessions: List<StepRunSession>,
    val totalSlots: Int = 7,
) {
    val selectedSession: StepRunSession?
        get() = sessions.getOrNull(selectedIndex) ?: sessions.lastOrNull()
}

data class BatteryState(
    val levelPercent: Int,
    val isCharging: Boolean,
    val temperatureC: Float,
    val statusLabel: String,
    val healthPercent: Int,
)

data class AgendaEventItem(
    val time: String,
    val title: String,
    val subtitle: String,
    val badge: String? = null,
)

data class AgendaState(
    val dayOfWeek: String,
    val dayOfMonth: String,
    val monthName: String,
    val eventCountLabel: String,
    val items: List<AgendaEventItem>,
)

object DailyWidgetDataProvider {
    private const val STEPS_PREFS = "daykit_daily_steps_v1"

    fun loadSolarArcState(context: Context, nowMillis: Long = System.currentTimeMillis()): SolarArcState {
        val snapshot = WeatherPrefs.loadSnapshot(context)
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val (riseMinutes, setMinutes, riseLabel, setLabel) = if (snapshot.sunriseMillis > 0L && snapshot.sunsetMillis > 0L) {
            val rCal = Calendar.getInstance().apply { timeInMillis = snapshot.sunriseMillis }
            val sCal = Calendar.getInstance().apply { timeInMillis = snapshot.sunsetMillis }
            val rMin = rCal.get(Calendar.HOUR_OF_DAY) * 60 + rCal.get(Calendar.MINUTE)
            val sMin = sCal.get(Calendar.HOUR_OF_DAY) * 60 + sCal.get(Calendar.MINUTE)
            Quad(rMin, sMin, fmt.format(Date(snapshot.sunriseMillis)), fmt.format(Date(snapshot.sunsetMillis)))
        } else {
            Quad(6 * 60 + 28, 18 * 60 + 19, "06:28", "18:19")
        }

        val isDay = nowMinutes in riseMinutes until setMinutes
        // Map sunrise -> 0.25, solar noon -> 0.50, sunset -> 0.75 on the single cosine arch
        val curveProgress = if (isDay) {
            val daySpan = (setMinutes - riseMinutes).coerceAtLeast(60).toFloat()
            0.25f + ((nowMinutes - riseMinutes) / daySpan).coerceIn(0f, 1f) * 0.50f
        } else {
            val nightSpan = (1440 - (setMinutes - riseMinutes)).coerceAtLeast(60).toFloat()
            val nightElapsed = if (nowMinutes >= setMinutes) {
                nowMinutes - setMinutes
            } else {
                (1440 - setMinutes) + nowMinutes
            }
            val frac = (nightElapsed / nightSpan).coerceIn(0f, 1f)
            if (frac < 0.5f) {
                0.75f + frac * 0.50f
            } else {
                (frac - 0.5f) * 0.50f
            }
        }.coerceIn(0.04f, 0.96f)

        return SolarArcState(
            sunriseText = riseLabel,
            sunsetText = setLabel,
            curveProgress = curveProgress,
            isDaytime = isDay,
        )
    }

    fun loadBatteryState(context: Context): BatteryState {
        val intent = runCatching {
            context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else 76
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 310) ?: 310
        val tempC = tempTenths / 10f
        val statusLabel = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> "Full"
            charging -> "Charging"
            pct <= 20 -> "Low"
            else -> "Optimal"
        }
        return BatteryState(
            levelPercent = pct,
            isCharging = charging,
            temperatureC = tempC,
            statusLabel = statusLabel,
            healthPercent = 96,
        )
    }

    fun loadStepsState(context: Context, nowMillis: Long = System.currentTimeMillis()): StepsChartState =
        DeviceStepCounter.loadSessionState(context, nowMillis)

    fun loadAgendaState(nowMillis: Long = System.currentTimeMillis()): AgendaState {
        val date = Date(nowMillis)
        val dow = SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
        val dom = SimpleDateFormat("d", Locale.getDefault()).format(date)
        val month = SimpleDateFormat("MMMM", Locale.getDefault()).format(date)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        val h1 = ((hour + 1) % 24).toString().padStart(2, '0')
        val h2 = ((hour + 3) % 24).toString().padStart(2, '0')
        val h3 = ((hour + 5) % 24).toString().padStart(2, '0')

        val items = listOf(
            AgendaEventItem("$h1:00", "Deep focus block", "Dayflow · 45 min", badge = "up next"),
            AgendaEventItem("$h2:30", "Daily habits check", "DayKit · 15 min"),
            AgendaEventItem("$h3:00", "Evening wrap-up", "Review & notes"),
        )
        return AgendaState(
            dayOfWeek = dow,
            dayOfMonth = dom,
            monthName = month,
            eventCountLabel = "${items.size} blocks today",
            items = items,
        )
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
