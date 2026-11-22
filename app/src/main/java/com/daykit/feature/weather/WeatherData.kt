package com.daykit.feature.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daykit.feature.widget.WeatherGlassWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class WeatherSnapshot(
    val city: String = "Cupertino",
    val latitude: Double = 37.3230,
    val longitude: Double = -122.0322,
    val tempC: Float = 22.2f,
    val highC: Float = 22.2f,
    val lowC: Float = 12.8f,
    val windKmh: Float = 10f,
    val wmoCode: Int = 0,
    val intensity: Float = 0.0f,
    val windStrength: Float = 0.24f,
    val conditionLabel: String = "Clear",
    val updatedAtMillis: Long = 0L,
    val isLiveSynced: Boolean = false,
    /** Today's sunrise/sunset for the selected city (epoch millis, 0 = unknown). */
    val sunriseMillis: Long = 0L,
    val sunsetMillis: Long = 0L,
) {
    /**
     * True when it's currently night at the selected city. Uses the synced sunrise/sunset
     * time-of-day (valid for following days too since it shifts only minutes per day);
     * falls back to the device clock (before 6 AM / after 6 PM) when not yet synced.
     */
    fun isNight(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (sunriseMillis > 0L && sunsetMillis > 0L) {
            val day = 86_400_000L
            val now = Math.floorMod(nowMillis, day)
            val rise = Math.floorMod(sunriseMillis, day)
            val set = Math.floorMod(sunsetMillis, day)
            val isDay = if (rise < set) now in rise until set else now >= rise || now < set
            return !isDay
        }
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour < 6 || hour >= 18
    }
    fun displayTemp(useFahrenheit: Boolean): Int =
        if (useFahrenheit) (tempC * 9f / 5f + 32f).roundToInt() else tempC.roundToInt()

    fun displayHigh(useFahrenheit: Boolean): Int =
        if (useFahrenheit) (highC * 9f / 5f + 32f).roundToInt() else highC.roundToInt()

    fun displayLow(useFahrenheit: Boolean): Int =
        if (useFahrenheit) (lowC * 9f / 5f + 32f).roundToInt() else lowC.roundToInt()

    fun unitSymbol(useFahrenheit: Boolean): String = if (useFahrenheit) "°F" else "°C"
}

data class CitySearchResult(
    val name: String,
    val admin1: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val subtitle: String
        get() = listOfNotNull(admin1?.takeIf { it.isNotBlank() }, country?.takeIf { it.isNotBlank() })
            .joinToString(", ")
}

/**
 * Plain-prefs store shared across Canvas -> Daily Widgets, Home Screen Widget,
 * Live Wallpaper Service, and WorkManager sync worker.
 */
object WeatherPrefs {
    private const val PREFS_NAME = "daykit_weather_glass_widget"
    private const val KEY_CITY = "city"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_TEMP_C = "temp_c"
    private const val KEY_HIGH_C = "high_c"
    private const val KEY_LOW_C = "low_c"
    private const val KEY_WIND_KMH = "wind_kmh"
    private const val KEY_WMO_CODE = "wmo_code"
    private const val KEY_INTENSITY = "intensity"
    private const val KEY_WIND_STRENGTH = "wind_strength"
    private const val KEY_CONDITION_LABEL = "condition_label"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_IS_LIVE = "is_live"
    private const val KEY_SUNRISE = "sunrise_millis"
    private const val KEY_SUNSET = "sunset_millis"
    private const val KEY_USE_FAHRENHEIT = "use_fahrenheit"
    private const val KEY_ONLY_FIRST_PAGE = "wallpaper_only_first_page"
    private const val KEY_WALLPAPER_PHOTO_VERSION = "wallpaper_photo_version"
    private const val KEY_WALLPAPER_PRESET = "wallpaper_preset_id"
    private const val KEY_WALLPAPER_CUBE_X = "wallpaper_cube_x"
    private const val KEY_WALLPAPER_CUBE_Y = "wallpaper_cube_y"
    private const val KEY_WALLPAPER_CUBE_SCALE = "wallpaper_cube_scale"
    private const val KEY_WALLPAPER_WEATHER_ENABLED = "wallpaper_weather_enabled"

    private const val WALLPAPER_BG_FILENAME = "weather_live_wallpaper_bg.jpg"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSnapshot(context: Context): WeatherSnapshot {
        val p = prefs(context)
        return WeatherSnapshot(
            city = p.getString(KEY_CITY, "Cupertino") ?: "Cupertino",
            latitude = java.lang.Double.longBitsToDouble(
                p.getLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(37.3230)),
            ),
            longitude = java.lang.Double.longBitsToDouble(
                p.getLong(KEY_LON, java.lang.Double.doubleToRawLongBits(-122.0322)),
            ),
            tempC = p.getFloat(KEY_TEMP_C, 22.2f),
            highC = p.getFloat(KEY_HIGH_C, 22.2f),
            lowC = p.getFloat(KEY_LOW_C, 12.8f),
            windKmh = p.getFloat(KEY_WIND_KMH, 10f),
            wmoCode = p.getInt(KEY_WMO_CODE, 0),
            intensity = p.getFloat(KEY_INTENSITY, 0.0f).coerceIn(0f, 1f),
            windStrength = p.getFloat(KEY_WIND_STRENGTH, 0.24f).coerceIn(0f, 1f),
            conditionLabel = p.getString(KEY_CONDITION_LABEL, "Clear") ?: "Clear",
            updatedAtMillis = p.getLong(KEY_UPDATED_AT, 0L),
            isLiveSynced = p.getBoolean(KEY_IS_LIVE, false),
            sunriseMillis = p.getLong(KEY_SUNRISE, 0L),
            sunsetMillis = p.getLong(KEY_SUNSET, 0L),
        )
    }

    fun saveSnapshot(context: Context, snapshot: WeatherSnapshot) {
        prefs(context).edit()
            .putString(KEY_CITY, snapshot.city)
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(snapshot.latitude))
            .putLong(KEY_LON, java.lang.Double.doubleToRawLongBits(snapshot.longitude))
            .putFloat(KEY_TEMP_C, snapshot.tempC)
            .putFloat(KEY_HIGH_C, snapshot.highC)
            .putFloat(KEY_LOW_C, snapshot.lowC)
            .putFloat(KEY_WIND_KMH, snapshot.windKmh)
            .putInt(KEY_WMO_CODE, snapshot.wmoCode)
            .putFloat(KEY_INTENSITY, snapshot.intensity.coerceIn(0f, 1f))
            .putFloat(KEY_WIND_STRENGTH, snapshot.windStrength.coerceIn(0f, 1f))
            .putString(KEY_CONDITION_LABEL, snapshot.conditionLabel)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAtMillis)
            .putBoolean(KEY_IS_LIVE, snapshot.isLiveSynced)
            .putLong(KEY_SUNRISE, snapshot.sunriseMillis)
            .putLong(KEY_SUNSET, snapshot.sunsetMillis)
            .apply()
    }

    fun saveManualPreview(context: Context, intensity: Float, windStrength: Float) {
        prefs(context).edit()
            .putFloat(KEY_INTENSITY, intensity.coerceIn(0f, 1f))
            .putFloat(KEY_WIND_STRENGTH, windStrength.coerceIn(0f, 1f))
            .apply()
    }

    fun useFahrenheit(context: Context): Boolean {
        val p = prefs(context)
        if (!p.contains(KEY_USE_FAHRENHEIT)) {
            val country = Locale.getDefault().country
            return country in setOf("US", "LR", "MM")
        }
        return p.getBoolean(KEY_USE_FAHRENHEIT, false)
    }

    fun setUseFahrenheit(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_FAHRENHEIT, value).apply()
    }

    fun onlyOnFirstPage(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONLY_FIRST_PAGE, false)

    fun setOnlyOnFirstPage(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONLY_FIRST_PAGE, value).apply()
    }

    /** Normalized horizontal center of the weather cube on the live wallpaper (0.0..1.0). */
    fun wallpaperCubeX(context: Context): Float =
        prefs(context).getFloat(KEY_WALLPAPER_CUBE_X, 0.50f).coerceIn(0.15f, 0.85f)

    /** Normalized vertical center of the weather cube on the live wallpaper (0.0..1.0). */
    fun wallpaperCubeY(context: Context): Float =
        prefs(context).getFloat(KEY_WALLPAPER_CUBE_Y, 0.28f).coerceIn(0.12f, 0.88f)

    /** Width of the weather cube as a fraction of screen width (0.35..0.92). */
    fun wallpaperCubeScale(context: Context): Float =
        prefs(context).getFloat(KEY_WALLPAPER_CUBE_SCALE, 0.68f).coerceIn(0.35f, 0.92f)

    fun saveWallpaperCubeLayout(context: Context, centerX: Float, centerY: Float, scale: Float) {
        prefs(context).edit()
            .putFloat(KEY_WALLPAPER_CUBE_X, centerX.coerceIn(0.15f, 0.85f))
            .putFloat(KEY_WALLPAPER_CUBE_Y, centerY.coerceIn(0.12f, 0.88f))
            .putFloat(KEY_WALLPAPER_CUBE_SCALE, scale.coerceIn(0.35f, 0.92f))
            .apply()
    }

    fun wallpaperWeatherEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WALLPAPER_WEATHER_ENABLED, true)

    fun setWallpaperWeatherEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WALLPAPER_WEATHER_ENABLED, enabled).apply()
    }

    fun wallpaperPhotoFile(context: Context): File =
        File(context.applicationContext.filesDir, WALLPAPER_BG_FILENAME)

    fun hasWallpaperPhoto(context: Context): Boolean {
        val file = wallpaperPhotoFile(context)
        return file.exists() && file.length() > 0L
    }

    fun wallpaperPhotoVersion(context: Context): Long =
        prefs(context).getLong(KEY_WALLPAPER_PHOTO_VERSION, 0L)

    fun wallpaperPresetId(context: Context): String? =
        prefs(context).getString(KEY_WALLPAPER_PRESET, null)?.takeIf { it.isNotBlank() }

    fun setWallpaperPreset(context: Context, presetId: String?) {
        prefs(context).edit()
            .apply {
                if (presetId.isNullOrBlank()) remove(KEY_WALLPAPER_PRESET)
                else putString(KEY_WALLPAPER_PRESET, presetId)
            }
            .putLong(KEY_WALLPAPER_PHOTO_VERSION, System.currentTimeMillis())
            .apply()
    }

    suspend fun importWallpaperPhoto(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = wallpaperPhotoFile(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false
            prefs(context).edit()
                .remove(KEY_WALLPAPER_PRESET)
                .putLong(KEY_WALLPAPER_PHOTO_VERSION, System.currentTimeMillis())
                .apply()
            true
        }.getOrDefault(false)
    }

    fun clearWallpaperPhoto(context: Context) {
        runCatching { wallpaperPhotoFile(context).delete() }
        prefs(context).edit()
            .putLong(KEY_WALLPAPER_PHOTO_VERSION, System.currentTimeMillis())
            .apply()
    }
}

/**
 * Zero-dependency Open-Meteo client (no API key, HTTPS).
 */
object WeatherApiClient {
    private const val TAG = "WeatherApiClient"
    private const val TIMEOUT_MS = 10_000

    suspend fun searchCities(query: String): List<CitySearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        runCatching {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=6&language=en&format=json")
            val body = httpGet(url) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val arr = root.optJSONArray("results") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val lat = obj.optDouble("latitude", Double.NaN)
                    val lon = obj.optDouble("longitude", Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue
                    add(
                        CitySearchResult(
                            name = name,
                            admin1 = obj.optString("admin1").takeIf { it.isNotBlank() },
                            country = obj.optString("country").takeIf { it.isNotBlank() },
                            latitude = lat,
                            longitude = lon,
                        ),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "City search failed", it) }.getOrDefault(emptyList())
    }

    suspend fun fetchLiveWeather(
        city: String,
        latitude: Double,
        longitude: Double,
    ): WeatherSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,wind_speed_10m,precipitation" +
                    "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset" +
                    "&timezone=auto&timeformat=unixtime&forecast_days=1",
            )
            val body = httpGet(url) ?: return@withContext null
            val root = JSONObject(body)
            val current = root.optJSONObject("current") ?: return@withContext null
            val daily = root.optJSONObject("daily")

            val tempC = current.optDouble("temperature_2m", 20.0).toFloat()
            val wmoCode = current.optInt("weather_code", 0)
            val windKmh = current.optDouble("wind_speed_10m", 8.0).toFloat().coerceAtLeast(0f)
            val precipMm = current.optDouble("precipitation", 0.0).toFloat().coerceAtLeast(0f)

            val highC = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, tempC.toDouble())?.toFloat() ?: tempC
            val lowC = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, tempC.toDouble())?.toFloat() ?: tempC
            val sunriseMillis = (daily?.optJSONArray("sunrise")?.optLong(0, 0L) ?: 0L) * 1000L
            val sunsetMillis = (daily?.optJSONArray("sunset")?.optLong(0, 0L) ?: 0L) * 1000L

            val (conditionLabel, intensity) = mapWmoToVisuals(wmoCode, precipMm)
            val windStrength = (windKmh / 48f).coerceIn(0.08f, 1.0f)

            WeatherSnapshot(
                city = city,
                latitude = latitude,
                longitude = longitude,
                tempC = tempC,
                highC = highC,
                lowC = lowC,
                windKmh = windKmh,
                wmoCode = wmoCode,
                intensity = intensity,
                windStrength = windStrength,
                conditionLabel = conditionLabel,
                updatedAtMillis = System.currentTimeMillis(),
                isLiveSynced = true,
                sunriseMillis = sunriseMillis,
                sunsetMillis = sunsetMillis,
            )
        }.onFailure { Log.w(TAG, "Live weather fetch failed", it) }.getOrNull()
    }

    private fun mapWmoToVisuals(wmoCode: Int, precipMm: Float): Pair<String, Float> {
        return when (wmoCode) {
            0 -> "Clear" to 0.0f
            1 -> "Mostly Clear" to 0.05f
            2 -> "Partly Cloudy" to 0.12f
            3 -> "Cloudy" to 0.19f
            45, 48 -> "Foggy" to 0.21f
            51, 56 -> "Light Drizzle" to 0.30f
            53, 55, 57 -> "Drizzle" to 0.42f
            61, 80 -> "Light Rain" to 0.56f
            63, 66, 81 -> "Rain" to 0.72f
            65, 67, 82 -> "Heavy Rain" to 0.92f
            71, 73, 75, 77, 85, 86 -> "Snow" to 0.62f
            95, 96, 99 -> "Thunderstorm" to 1.00f
            else -> when {
                precipMm >= 4.0f -> "Heavy Rain" to 0.94f
                precipMm >= 1.0f -> "Rain" to 0.68f
                precipMm > 0.05f -> "Drizzle" to 0.36f
                else -> "Clear" to 0.0f
            }
        }
    }

    private fun httpGet(url: URL): String? {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "DayKit-Android")
        }
        return try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Native Android LocationManager + Geocoder city detector (city-level ACCESS_COARSE_LOCATION).
 */
object WeatherLocationHelper {
    fun hasCoarseLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun detectCurrentCity(context: Context): CitySearchResult? = withContext(Dispatchers.IO) {
        if (!hasCoarseLocationPermission(context)) return@withContext null
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null

        val location = requestFreshLocation(context, manager) ?: bestLastKnownLocation(manager)
            ?: return@withContext null

        val cityName = resolveCityName(context, location.latitude, location.longitude) ?: "My Location"
        CitySearchResult(
            name = cityName,
            admin1 = null,
            country = null,
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(
        context: Context,
        manager: LocationManager,
    ): Location? {
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    manager.getCurrentLocation(
                        provider,
                        signal,
                        context.mainExecutor,
                    ) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnownLocation(manager: LocationManager): Location? {
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        return providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveCityName(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(4_000L) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            val addr = addresses.firstOrNull()
                            val name = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
                            if (cont.isActive) cont.resume(name)
                        }
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } else {
            runCatching {
                val addr = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
            }.getOrNull()
        }
    }
}

/**
 * Periodic + on-demand WorkManager job keeping the Weather Glass Widget and
 * Live Wallpaper synced to the user's real city weather.
 */
class WeatherSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val current = WeatherPrefs.loadSnapshot(applicationContext)
        val live = WeatherApiClient.fetchLiveWeather(
            city = current.city,
            latitude = current.latitude,
            longitude = current.longitude,
        ) ?: return Result.retry()

        WeatherPrefs.saveSnapshot(applicationContext, live)
        WeatherGlassWidgetProvider.refreshAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "daykit_weather_periodic_sync"
        private const val IMMEDIATE_WORK_NAME = "daykit_weather_immediate_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<WeatherSyncWorker>(45, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
        }
    }
}
