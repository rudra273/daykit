package com.daykit.feature.canvas.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.daykit.DayKitApplication
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.background.frostedBackdrop
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.weather.CitySearchResult
import com.daykit.feature.weather.WeatherApiClient
import com.daykit.feature.weather.WeatherIcons
import com.daykit.feature.weather.WeatherLocationHelper
import com.daykit.feature.weather.WeatherPrefs
import com.daykit.feature.weather.WeatherSnapshot
import com.daykit.feature.weather.WeatherSyncWorker
import com.daykit.feature.widget.WeatherGlassWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min as mathMin
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private fun previewConditionLabel(intensity: Float): String = when {
    intensity < 0.22f -> "Clear"
    intensity < 0.52f -> "Drizzle"
    intensity < 0.80f -> "Rain"
    else -> "Heavy Rain"
}

private fun piecewiseLerp(t: Float, a: Float, b: Float, c: Float, d: Float): Float = when {
    t <= 0.333f -> lerpFloat(a, b, t / 0.333f)
    t <= 0.666f -> lerpFloat(b, c, (t - 0.333f) / 0.333f)
    else -> lerpFloat(c, d, (t - 0.666f) / 0.334f)
}

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun markExpectingExternalActivity(context: Context) {
    (context.applicationContext as? DayKitApplication)
        ?.container
        ?.sensitiveKeyManager
        ?.expectingActivityResult = true
}

/**
 * Interactive Daily Weather Glass Widget under Canvas -> Daily Widgets.
 * Syncs live weather for the user's city (GPS or city search via Open-Meteo),
 * previews 60fps clouds/rain/splashes with interactive sliders, pins the transparent
 * 2x2 Home Screen Widget, and configures/launches the 60fps Live Wallpaper.
 */
@Composable
fun DailyWeatherWidgetShowcase(
    pinnedCount: Int,
    onPin: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf(WeatherPrefs.loadSnapshot(context)) }
    var useFahrenheit by remember { mutableStateOf(WeatherPrefs.useFahrenheit(context)) }

    var windStrength by remember { mutableFloatStateOf(snapshot.windStrength) }
    var intensity by remember { mutableFloatStateOf(snapshot.intensity) }
    var isPreviewOverride by remember { mutableStateOf(false) }

    var isSyncing by remember { mutableStateOf(false) }
    var showCitySearch by remember { mutableStateOf(false) }

    val cloudPuffs = remember { generateCloudPuffBitmaps() }
    val physics = remember { WeatherPhysicsEngine() }
    var frameTick by remember { mutableLongStateOf(0L) }

    fun applyLiveSnapshot(live: WeatherSnapshot) {
        snapshot = live
        intensity = live.intensity
        windStrength = live.windStrength
        isPreviewOverride = false
        WeatherPrefs.saveSnapshot(context, live)
        WeatherSyncWorker.schedulePeriodicSync(context)
        WeatherGlassWidgetProvider.refreshAll(context)
    }

    fun syncCurrentCityWeather() {
        scope.launch {
            isSyncing = true
            val live = WeatherApiClient.fetchLiveWeather(
                city = snapshot.city,
                latitude = snapshot.latitude,
                longitude = snapshot.longitude,
            )
            isSyncing = false
            if (live != null) {
                applyLiveSnapshot(live)
            } else {
                onShowMessage("Could not fetch live weather. Check your internet connection.")
            }
        }
    }

    fun detectLocationAndSync() {
        scope.launch {
            isSyncing = true
            val detected = WeatherLocationHelper.detectCurrentCity(context)
            if (detected == null) {
                isSyncing = false
                onShowMessage("Could not detect your location. Try searching your city instead.")
                return@launch
            }
            val live = WeatherApiClient.fetchLiveWeather(
                city = detected.name,
                latitude = detected.latitude,
                longitude = detected.longitude,
            )
            isSyncing = false
            if (live != null) {
                applyLiveSnapshot(live)
                onShowMessage("Synced live weather for ${live.city}")
            } else {
                onShowMessage("Detected ${detected.name}, but weather fetch failed.")
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            detectLocationAndSync()
        } else {
            onShowMessage("Location permission denied. You can still search your city manually.")
        }
    }

    // Auto-sync on first open if never synced yet (or stale > 30 min)
    LaunchedEffect(Unit) {
        WeatherSyncWorker.schedulePeriodicSync(context)
        val stale = System.currentTimeMillis() - snapshot.updatedAtMillis > 30 * 60_000L
        if (!snapshot.isLiveSynced || stale) {
            if (WeatherLocationHelper.hasCoarseLocationPermission(context) && !snapshot.isLiveSynced) {
                detectLocationAndSync()
            } else {
                syncCurrentCityWeather()
            }
        }
    }

    // Dedicated 60fps simulation loop; only Canvas draw scopes read frameTick.
    LaunchedEffect(physics) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    physics.step(dt, intensity = intensity, windStrength = windStrength)
                }
                lastNanos = now
                frameTick = now
            }
        }
    }

    // Compute displayed values: live real data when synced, or smooth preview interpolation while dragging sliders
    val displayTemp: Int
    val displayHigh: Int
    val displayLow: Int
    val displayCondition: String
    if (!isPreviewOverride && snapshot.isLiveSynced) {
        displayTemp = snapshot.displayTemp(useFahrenheit)
        displayHigh = snapshot.displayHigh(useFahrenheit)
        displayLow = snapshot.displayLow(useFahrenheit)
        displayCondition = snapshot.conditionLabel
    } else {
        val previewTempF = piecewiseLerp(intensity, 72f, 59f, 53f, 49f)
        val previewHighF = piecewiseLerp(intensity, 72f, 70f, 66f, 58f)
        val previewLowF = piecewiseLerp(intensity, 55f, 54f, 52f, 48f)
        displayTemp = if (useFahrenheit) previewTempF.roundToInt() else ((previewTempF - 32f) * 5f / 9f).roundToInt()
        displayHigh = if (useFahrenheit) previewHighF.roundToInt() else ((previewHighF - 32f) * 5f / 9f).roundToInt()
        displayLow = if (useFahrenheit) previewLowF.roundToInt() else ((previewLowF - 32f) * 5f / 9f).roundToInt()
        displayCondition = previewConditionLabel(intensity)
    }
    val unitSymbol = if (useFahrenheit) "°F" else "°C"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Live City & Sync Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.extendedColors.inputField)
                    .clickable { showCitySearch = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = snapshot.city,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = if (isPreviewOverride) "· Preview" else if (snapshot.isLiveSynced) "· Live" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPreviewOverride) {
                        MaterialTheme.extendedColors.accents.orange
                    } else {
                        MaterialTheme.extendedColors.accents.green
                    },
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "Search city",
                    tint = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(Modifier.width(6.dp))

            // GPS Auto-detect button
            IconButton(
                onClick = {
                    if (WeatherLocationHelper.hasCoarseLocationPermission(context)) {
                        detectLocationAndSync()
                    } else {
                        markExpectingExternalActivity(context)
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = "Use current location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }

            // °C / °F toggle
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.extendedColors.inputField)
                    .clickable {
                        val next = !useFahrenheit
                        useFahrenheit = next
                        WeatherPrefs.setUseFahrenheit(context, next)
                        WeatherGlassWidgetProvider.refreshAll(context)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = unitSymbol,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Refresh / Sync Live button
            IconButton(
                onClick = {
                    isPreviewOverride = false
                    syncCurrentCityWeather()
                },
                enabled = !isSyncing,
                modifier = Modifier.size(36.dp),
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Sync live weather",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }

        // 2. Centered Transparent Frosted-Glass Weather Cube
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val cardSize = min(maxWidth * 0.84f, min(maxHeight * 0.88f, 286.dp)).coerceAtLeast(204.dp)
            val cornerRadius = cardSize * 0.22f
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }
            val cardShape = RoundedCornerShape(cornerRadius)

            Box(
                modifier = Modifier
                    .size(cardSize)
                    .shadow(
                        elevation = 20.dp,
                        shape = cardShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = Color.Black.copy(alpha = 0.45f),
                    )
                    .frostedBackdrop(cardShape, Color(0xFF141B24).copy(alpha = 0.22f))
                    .clip(cardShape),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_VARIABLE")
                    val tick = frameTick

                    physics.updateBounds(size.width, size.height, cornerRadiusPx)
                    drawAtmosphericSky(intensity)
                    drawVolumetricClouds(
                        puffs = cloudPuffs,
                        engine = physics,
                        intensity = intensity,
                    )
                    drawRainAndSplashes(
                        engine = physics,
                        intensity = intensity,
                    )
                    drawGlassCubeOptics(cornerRadiusPx, intensity)
                }

                WeatherCubeContent(
                    city = snapshot.city,
                    tempText = "$displayTemp$unitSymbol",
                    conditionLabel = displayCondition,
                    highLowText = "H:${displayHigh}°  L:${displayLow}°",
                    intensity = intensity,
                    windStrength = windStrength,
                    isNight = snapshot.isNight(),
                    frameTickProvider = { frameTick },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = cardSize * 0.105f, vertical = cardSize * 0.095f),
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // 3. Two dark translucent horizontal sliders outside the card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TranslucentControlSlider(
                label = "Wind Strength",
                value = windStrength,
                onValueChange = {
                    windStrength = it
                    isPreviewOverride = true
                    WeatherPrefs.saveManualPreview(context, intensity, it)
                },
                valueLabel = "${(windStrength * 100).roundToInt()}%",
                modifier = Modifier.weight(1f),
            )
            TranslucentControlSlider(
                label = "Intensity",
                value = intensity,
                onValueChange = {
                    intensity = it
                    isPreviewOverride = true
                    WeatherPrefs.saveManualPreview(context, it, windStrength)
                },
                valueLabel = displayCondition,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Spacing.md))

        // 4. Add to Home Screen bar (Live Wallpaper studio lives in the Wallpapers tab)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (pinnedCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.accents.green,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (pinnedCount == 1) "Added to Home" else "$pinnedCount on Home",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.extendedColors.accents.green,
                    )
                }
            } else {
                Text(
                    text = "Transparent 2 × 2 widget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }

            PrimaryButton(
                text = if (pinnedCount > 0) "Add another" else "Add to Home",
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = onPin,
            )
        }
    }

    if (showCitySearch) {
        CitySearchDialog(
            initialQuery = snapshot.city,
            onDismiss = { showCitySearch = false },
            onSelectCity = { city ->
                showCitySearch = false
                scope.launch {
                    isSyncing = true
                    val live = WeatherApiClient.fetchLiveWeather(
                        city = city.name,
                        latitude = city.latitude,
                        longitude = city.longitude,
                    )
                    isSyncing = false
                    if (live != null) {
                        applyLiveSnapshot(live)
                        onShowMessage("Synced live weather for ${live.city}")
                    } else {
                        onShowMessage("Could not fetch weather for ${city.name}")
                    }
                }
            },
        )
    }
}

@Composable
private fun CitySearchDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onSelectCity: (CitySearchResult) -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<CitySearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(280)
        searching = true
        results = WeatherApiClient.searchCities(query)
        searching = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose city") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search city (e.g. Bengaluru, London)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (searching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else if (results.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(results) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectCity(item) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (item.subtitle.isNotBlank()) {
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.extendedColors.textMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun WeatherCubeContent(
    city: String,
    tempText: String,
    conditionLabel: String,
    highLowText: String,
    intensity: Float,
    windStrength: Float,
    isNight: Boolean,
    frameTickProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val textShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.38f),
            offset = Offset(0f, 3f),
            blurRadius = 10f,
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = city,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.2).sp,
                shadow = textShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = tempText,
            style = TextStyle(
                color = Color.White,
                fontSize = 54.sp,
                lineHeight = 58.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.8).sp,
                fontFeatureSettings = "tnum",
                shadow = textShadow,
            ),
            maxLines = 1,
        )

        Spacer(Modifier.weight(1f))

        AnimatedWeatherIcon(
            intensity = intensity,
            windStrength = windStrength,
            isNight = isNight,
            frameTickProvider = frameTickProvider,
            modifier = Modifier.size(34.dp),
        )

        Spacer(Modifier.height(6.dp))

        AnimatedContent(
            targetState = conditionLabel,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 4 })
            },
            label = "WeatherConditionLabel",
        ) { label ->
            Text(
                text = label,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    shadow = textShadow,
                ),
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = highLowText,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                fontFeatureSettings = "tnum",
                shadow = textShadow,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun AnimatedWeatherIcon(
    intensity: Float,
    windStrength: Float,
    isNight: Boolean,
    frameTickProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val timeSec = (frameTickProvider() / 1_000_000_000.0).toFloat()
        val cloudMix = smoothstep(0.14f, 0.28f, intensity)
        val sunAlpha = 1f - cloudMix

        val cx = size.width * 0.48f
        val cy = size.height * 0.50f

        if (sunAlpha > 0.01f) {
            val iconBox = size.minDimension
            drawIntoCanvas { c ->
                if (isNight) {
                    WeatherIcons.drawMoon(c.nativeCanvas, cx, cy, iconBox, sunAlpha)
                } else {
                    WeatherIcons.drawSun(c.nativeCanvas, cx, cy, iconBox, sunAlpha)
                }
            }
        }

        if (cloudMix > 0.01f) {
            val cloudColor = Color.White.copy(alpha = cloudMix)
            val w = size.width
            val h = size.height

            val baseBottom = h * 0.56f
            drawCircle(color = cloudColor, radius = w * 0.16f, center = Offset(w * 0.27f, baseBottom - w * 0.14f))
            drawCircle(color = cloudColor, radius = w * 0.23f, center = Offset(w * 0.48f, baseBottom - w * 0.22f))
            drawCircle(color = cloudColor, radius = w * 0.17f, center = Offset(w * 0.70f, baseBottom - w * 0.15f))
            drawRoundRect(
                color = cloudColor,
                topLeft = Offset(w * 0.14f, baseBottom - w * 0.24f),
                size = Size(w * 0.69f, w * 0.24f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
            )

            val rainLevel = smoothstep(0.20f, 1.0f, intensity)
            val dropCount = if (intensity < 0.52f) 3 else 4
            val dashLength = lerpFloat(h * 0.09f, h * 0.17f, rainLevel)
            val slant = lerpFloat(0.14f, 0.36f, windStrength) * dashLength
            val strokeW = lerpFloat(w * 0.072f, w * 0.082f, rainLevel)
            val speed = lerpFloat(1.6f, 3.6f, rainLevel)

            val spanLeft = w * 0.24f
            val spanRight = w * 0.72f
            for (i in 0 until dropCount) {
                val frac = if (dropCount == 1) 0.5f else i.toFloat() / (dropCount - 1)
                val xBase = lerpFloat(spanLeft, spanRight, frac)
                val phase = ((timeSec * speed + i * 0.31f) % 1f)
                val yTop = baseBottom + h * 0.06f + phase * (h * 0.18f)
                val dropAlpha = cloudMix * sin(phase * PI.toFloat()).coerceIn(0.25f, 1f)
                drawLine(
                    color = Color.White.copy(alpha = dropAlpha),
                    start = Offset(xBase, yTop),
                    end = Offset(xBase - slant, yTop + dashLength),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun TranslucentControlSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(
        modifier = modifier
            .height(50.dp)
            .clip(shape)
            .background(Color(0xFF0D1014).copy(alpha = 0.74f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onValueChange(fraction)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onValueChange(fraction)
                }
            },
    ) {
        val clamped = value.coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped.coerceAtLeast(0.001f))
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color.White.copy(alpha = 0.14f),
                        0.85f to Color.White.copy(alpha = 0.24f),
                        1.0f to Color.White.copy(alpha = 0.34f),
                    ),
                ),
        )

        if (clamped > 0.02f && clamped < 0.99f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * clamped - 2.dp)
                    .width(2.5.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.78f)),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = 0.08f),
                        1.0f to Color.Transparent,
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                ),
                maxLines = 1,
            )
            Text(
                text = valueLabel,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                maxLines = 1,
            )
        }
    }
}

// =============================================================================
// Drawing Layers (60fps, zero per-frame allocations)
// =============================================================================

private fun DrawScope.drawAtmosphericSky(intensity: Float) {
    val t = intensity.coerceIn(0f, 1f)

    val skyTopColor = when {
        t < 0.33f -> lerp(Color(0xFF3E9BFF), Color(0xFF6B7987), t / 0.33f)
        t < 0.66f -> lerp(Color(0xFF6B7987), Color(0xFF28313B), (t - 0.33f) / 0.33f)
        else -> lerp(Color(0xFF28313B), Color(0xFF10151C), (t - 0.66f) / 0.34f)
    }
    val skyTopAlpha = lerpFloat(0.36f, 0.78f, t)
    val midTintColor = lerp(Color(0xFF1A293D), Color(0xFF0A0F14), t)
    val midTintAlpha = lerpFloat(0.20f, 0.52f, t)
    val bottomMistAlpha = lerpFloat(0.10f, 0.28f, t)

    drawRect(
        brush = Brush.verticalGradient(
            0.0f to skyTopColor.copy(alpha = skyTopAlpha),
            0.40f to midTintColor.copy(alpha = midTintAlpha),
            0.78f to Color(0xFF0C1118).copy(alpha = lerpFloat(0.24f, 0.46f, t)),
            1.0f to Color(0xFFD8E4F0).copy(alpha = bottomMistAlpha),
        ),
    )

    val clearFactor = 1f - smoothstep(0.0f, 0.35f, t)
    if (clearFactor > 0.01f) {
        drawRect(
            brush = Brush.radialGradient(
                0.0f to Color(0xFF8CD2FF).copy(alpha = 0.34f * clearFactor),
                0.55f to Color(0xFF4CA3FF).copy(alpha = 0.14f * clearFactor),
                1.0f to Color.Transparent,
                center = Offset(size.width * 0.74f, size.height * 0.16f),
                radius = size.width * 0.78f,
            ),
        )
    }
}

private fun DrawScope.drawVolumetricClouds(
    puffs: List<Bitmap>,
    engine: WeatherPhysicsEngine,
    intensity: Float,
) {
    if (puffs.isEmpty()) return
    val t = intensity.coerceIn(0f, 1f)
    val w = size.width
    val h = size.height

    val highlightColor = when {
        t < 0.35f -> lerp(Color(0xFFFFFFFF), Color(0xFFDCE2E8), t / 0.35f)
        t < 0.70f -> lerp(Color(0xFFDCE2E8), Color(0xFFB4BDC7), (t - 0.35f) / 0.35f)
        else -> lerp(Color(0xFFB4BDC7), Color(0xFF9AA4AF), (t - 0.70f) / 0.30f)
    }
    val midColor = when {
        t < 0.35f -> lerp(Color(0xFFE9EEF5), Color(0xFF69737D), t / 0.35f)
        t < 0.70f -> lerp(Color(0xFF69737D), Color(0xFF343C44), (t - 0.35f) / 0.35f)
        else -> lerp(Color(0xFF343C44), Color(0xFF1D232A), (t - 0.70f) / 0.30f)
    }
    val shadowColor = when {
        t < 0.30f -> lerp(Color(0xFF9EB0C4), Color(0xFF1A2027), t / 0.30f)
        else -> lerp(Color(0xFF1A2027), Color(0xFF070A0E), (t - 0.30f) / 0.70f)
    }

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val paint = engine.cloudPaint
        val dst = engine.tempRect

        for (i in 0 until engine.cloudCount) {
            val c = engine.clouds[i]
            val visibility = smoothstep(c.minIntensity - 0.14f, c.minIntensity + 0.18f, t)
            if (visibility <= 0.01f) continue

            val bmp = puffs[c.variant % puffs.size]
            val puffColor = when (c.layer) {
                0 -> shadowColor
                1 -> midColor
                else -> highlightColor
            }
            val baseAlpha = when (c.layer) {
                0 -> lerpFloat(0.28f, 0.88f, t)
                1 -> lerpFloat(0.74f, 0.84f, t)
                else -> lerpFloat(0.92f, 0.66f, t)
            } * c.alphaScale * visibility

            val cx = c.x * w
            val cy = (c.y + sin(engine.elapsed * c.bobSpeed + c.phase) * 0.009f) * h
            val rx = c.scaleX * w * lerpFloat(0.88f, 1.18f, t)
            val ry = c.scaleY * h * lerpFloat(0.86f, 1.16f, t)

            dst.set(cx - rx, cy - ry, cx + rx, cy + ry)
            paint.colorFilter = PorterDuffColorFilter(puffColor.toArgb(), PorterDuff.Mode.SRC_IN)
            paint.alpha = (baseAlpha.coerceIn(0f, 1f) * 255f).toInt()
            native.drawBitmap(bmp, null, dst, paint)
        }
    }
}

private fun DrawScope.drawRainAndSplashes(
    engine: WeatherPhysicsEngine,
    intensity: Float,
) {
    val rainFactor = smoothstep(0.06f, 1.0f, intensity)
    if (rainFactor <= 0.005f) return

    val mistHeight = size.height * lerpFloat(0.10f, 0.24f, rainFactor)
    drawRect(
        brush = Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.55f to Color(0xFFE6F0FA).copy(alpha = 0.08f * rainFactor),
            1.0f to Color(0xFFFFFFFF).copy(alpha = 0.24f * rainFactor),
            startY = size.height - mistHeight,
            endY = size.height,
        ),
        topLeft = Offset(0f, size.height - mistHeight),
        size = Size(size.width, mistHeight),
    )

    val streakColor = Color(0xFFF2F7FF)
    for (i in 0 until engine.raindrops.size) {
        val drop = engine.raindrops[i]
        if (!drop.active) continue

        val emergeAlpha = smoothstep(drop.spawnY, drop.spawnY + size.height * 0.09f, drop.y)
        val alpha = (drop.alpha * emergeAlpha * rainFactor).coerceIn(0f, 0.92f)
        if (alpha <= 0.01f) continue

        val speed = hypot(drop.vx.toDouble(), drop.vy.toDouble()).toFloat().coerceAtLeast(1f)
        val dirX = drop.vx / speed
        val dirY = drop.vy / speed
        val tailX = drop.x - dirX * drop.length
        val tailY = drop.y - dirY * drop.length

        drawLine(
            color = streakColor.copy(alpha = alpha * 0.42f),
            start = Offset(tailX, tailY),
            end = Offset(drop.x, drop.y),
            strokeWidth = drop.thickness * 1.45f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = alpha * 0.92f),
            start = Offset(
                lerpFloat(tailX, drop.x, 0.45f),
                lerpFloat(tailY, drop.y, 0.45f),
            ),
            end = Offset(drop.x, drop.y),
            strokeWidth = drop.thickness,
            cap = StrokeCap.Round,
        )
    }

    for (i in 0 until engine.ripples.size) {
        val rip = engine.ripples[i]
        if (!rip.active) continue
        val lifeFrac = (rip.age / rip.maxAge).coerceIn(0f, 1f)
        val alpha = ((1f - lifeFrac) * 0.55f * rainFactor).coerceIn(0f, 0.6f)
        val radiusX = lerpFloat(2.2f, rip.maxRadius, lifeFrac)
        val radiusY = radiusX * 0.34f
        drawOval(
            color = Color.White.copy(alpha = alpha),
            topLeft = Offset(rip.x - radiusX, rip.y - radiusY),
            size = Size(radiusX * 2f, radiusY * 2f),
            style = Stroke(width = 1.2f),
        )
    }

    for (i in 0 until engine.splashes.size) {
        val p = engine.splashes[i]
        if (!p.active) continue
        val lifeFrac = (p.age / p.maxAge).coerceIn(0f, 1f)
        val alpha = ((1f - lifeFrac * lifeFrac) * p.alpha * rainFactor).coerceIn(0f, 0.95f)
        if (alpha <= 0.01f) continue

        if (p.radius > 1.7f) {
            drawCircle(
                color = Color(0xFFDCEBFF).copy(alpha = alpha * 0.30f),
                radius = p.radius * 2.1f,
                center = Offset(p.x, p.y),
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = p.radius,
            center = Offset(p.x, p.y),
        )
    }

    for (i in 0 until engine.beads.size) {
        val b = engine.beads[i]
        val beadVisibility = smoothstep(b.minIntensity, b.minIntensity + 0.25f, intensity)
        if (beadVisibility <= 0.01f) continue

        val shimmer = 0.62f + 0.38f * sin(engine.elapsed * b.shimmerSpeed + b.phase)
        val x = b.normX * size.width
        val floorY = engine.bottomFloorY(x)
        val y = floorY - b.heightOffset * lerpFloat(0.45f, 1.35f, rainFactor)
        val alpha = (b.alpha * shimmer * beadVisibility).coerceIn(0f, 0.92f)

        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = b.radius * lerpFloat(0.75f, 1.15f, rainFactor),
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawGlassCubeOptics(
    cornerRadiusPx: Float,
    intensity: Float,
) {
    val cr = CornerRadius(cornerRadiusPx, cornerRadiusPx)

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val innerGlowPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeWidth = size.minDimension * 0.055f
            color = Color.White.copy(alpha = lerpFloat(0.26f, 0.20f, intensity)).toArgb()
            maskFilter = BlurMaskFilter(size.minDimension * 0.048f, BlurMaskFilter.Blur.NORMAL)
        }
        val inset = -size.minDimension * 0.008f
        native.drawRoundRect(
            inset,
            inset,
            size.width - inset,
            size.height - inset,
            cornerRadiusPx,
            cornerRadiusPx,
            innerGlowPaint,
        )
    }

    drawRoundRect(
        brush = Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = 0.22f),
            0.14f to Color.Transparent,
            0.84f to Color.Transparent,
            1.0f to Color.White.copy(alpha = 0.26f),
        ),
        cornerRadius = cr,
        style = Stroke(width = 6.dp.toPx()),
    )

    drawRoundRect(
        brush = Brush.linearGradient(
            0.0f to Color.White.copy(alpha = 0.72f),
            0.35f to Color.White.copy(alpha = 0.24f),
            0.70f to Color.White.copy(alpha = 0.18f),
            1.0f to Color.White.copy(alpha = 0.54f),
        ),
        cornerRadius = cr,
        style = Stroke(width = 1.25.dp.toPx()),
    )
}

// =============================================================================
// Procedural Volumetric Cloud Puff Generator (runs once in remember)
// =============================================================================

private fun generateCloudPuffBitmaps(): List<Bitmap> {
    return listOf(101, 277, 509).map { seed ->
        createProceduralCloudPuff(size = 112, seed = seed)
    }
}

private fun createProceduralCloudPuff(size: Int, seed: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size)
    val half = size * 0.5f

    for (y in 0 until size) {
        val ny = (y - half) / half
        for (x in 0 until size) {
            val nx = (x - half) / half
            val dist = sqrt(nx * nx + ny * ny)
            if (dist >= 1f) {
                pixels[y * size + x] = 0
                continue
            }

            val fx = (x.toFloat() / size) * 4.2f
            val fy = (y.toFloat() / size) * 4.2f
            val n1 = valueNoise2D(fx, fy, seed)
            val n2 = valueNoise2D(fx * 2.0f + 11.3f, fy * 2.0f + 7.1f, seed + 17)
            val n3 = valueNoise2D(fx * 4.0f + 23.7f, fy * 4.0f + 19.4f, seed + 43)
            val n4 = valueNoise2D(fx * 8.0f + 5.2f, fy * 8.0f + 31.8f, seed + 89)
            val fbm = n1 * 0.52f + n2 * 0.26f + n3 * 0.14f + n4 * 0.08f

            val radial = smoothstep(1.0f, 0.18f, dist)
            val cloudDensity = ((radial * (0.58f + 0.72f * fbm)) - 0.14f).coerceIn(0f, 1f)
            val alpha = (cloudDensity * cloudDensity * 255f).toInt().coerceIn(0, 255)
            pixels[y * size + x] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    return bmp
}

private fun valueNoise2D(x: Float, y: Float, seed: Int): Float {
    val xi = floor(x).toInt()
    val yi = floor(y).toInt()
    val xf = x - xi
    val yf = y - yi
    val u = xf * xf * (3f - 2f * xf)
    val v = yf * yf * (3f - 2f * yf)

    val a = hash2D(xi, yi, seed)
    val b = hash2D(xi + 1, yi, seed)
    val c = hash2D(xi, yi + 1, seed)
    val d = hash2D(xi + 1, yi + 1, seed)

    val x1 = a + (b - a) * u
    val x2 = c + (d - c) * u
    return x1 + (x2 - x1) * v
}

private fun hash2D(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()
}

// =============================================================================
// 60fps Zero-Allocation Weather Physics Engine
// =============================================================================

private class CloudPuff(
    var x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val speed: Float,
    val layer: Int,
    val variant: Int,
    val minIntensity: Float,
    val alphaScale: Float,
    val bobSpeed: Float,
    val phase: Float,
)

private class Raindrop {
    var active = false
    var x = 0f
    var y = 0f
    var spawnY = 0f
    var vx = 0f
    var vy = 0f
    var length = 22f
    var thickness = 1.6f
    var alpha = 0.7f
    var depth = 1f
}

private class SplashParticle {
    var active = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var radius = 1.6f
    var alpha = 0.85f
    var age = 0f
    var maxAge = 0.45f
    var bounced = false
}

private class ImpactRipple {
    var active = false
    var x = 0f
    var y = 0f
    var age = 0f
    var maxAge = 0.25f
    var maxRadius = 10f
}

private class FloorBead(
    val normX: Float,
    val heightOffset: Float,
    val radius: Float,
    val alpha: Float,
    val minIntensity: Float,
    val shimmerSpeed: Float,
    val phase: Float,
)

private class WeatherPhysicsEngine {
    private val rng = Random(42)

    var width = 600f
        private set
    var height = 600f
        private set
    var cornerRadius = 120f
        private set
    var elapsed = 0f
        private set

    val cloudPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
    val tempRect = RectF()

    val clouds: Array<CloudPuff>
    val cloudCount: Int

    val raindrops = Array(180) { Raindrop() }
    val splashes = Array(260) { SplashParticle() }
    val ripples = Array(36) { ImpactRipple() }
    val beads: Array<FloorBead>

    private var rainSpawnAccumulator = 0f
    private var ambientSplashAccumulator = 0f
    private var splashCursor = 0
    private var rippleCursor = 0

    init {
        val list = mutableListOf<CloudPuff>()

        val underbellySpecs = arrayOf(
            floatArrayOf(0.18f, 0.22f, 0.34f, 0.22f, 0.20f),
            floatArrayOf(0.46f, 0.24f, 0.38f, 0.24f, 0.16f),
            floatArrayOf(0.74f, 0.23f, 0.36f, 0.24f, 0.00f),
            floatArrayOf(0.95f, 0.20f, 0.32f, 0.22f, 0.26f),
            floatArrayOf(0.32f, 0.29f, 0.34f, 0.20f, 0.45f),
            floatArrayOf(0.64f, 0.30f, 0.36f, 0.21f, 0.40f),
            floatArrayOf(0.84f, 0.28f, 0.32f, 0.20f, 0.55f),
        )
        underbellySpecs.forEachIndexed { idx, s ->
            list += CloudPuff(
                x = s[0],
                y = s[1],
                scaleX = s[2],
                scaleY = s[3],
                speed = 0.014f + (idx % 3) * 0.005f,
                layer = 0,
                variant = idx,
                minIntensity = s[4],
                alphaScale = 0.92f,
                bobSpeed = 0.45f + (idx % 4) * 0.12f,
                phase = idx * 1.3f,
            )
        }

        val midSpecs = arrayOf(
            floatArrayOf(0.14f, 0.12f, 0.32f, 0.22f, 0.22f),
            floatArrayOf(0.38f, 0.14f, 0.35f, 0.23f, 0.18f),
            floatArrayOf(0.62f, 0.15f, 0.34f, 0.24f, 0.00f),
            floatArrayOf(0.80f, 0.17f, 0.32f, 0.23f, 0.00f),
            floatArrayOf(0.98f, 0.13f, 0.30f, 0.21f, 0.28f),
            floatArrayOf(0.26f, 0.19f, 0.31f, 0.20f, 0.38f),
            floatArrayOf(0.55f, 0.21f, 0.35f, 0.22f, 0.32f),
            floatArrayOf(0.76f, 0.21f, 0.32f, 0.20f, 0.48f),
        )
        midSpecs.forEachIndexed { idx, s ->
            list += CloudPuff(
                x = s[0],
                y = s[1],
                scaleX = s[2],
                scaleY = s[3],
                speed = 0.019f + (idx % 3) * 0.006f,
                layer = 1,
                variant = idx + 1,
                minIntensity = s[4],
                alphaScale = 0.90f,
                bobSpeed = 0.55f + (idx % 3) * 0.14f,
                phase = idx * 0.9f + 0.4f,
            )
        }

        val topSpecs = arrayOf(
            floatArrayOf(0.54f, 0.14f, 0.22f, 0.17f, 0.00f),
            floatArrayOf(0.69f, 0.11f, 0.26f, 0.20f, 0.00f),
            floatArrayOf(0.81f, 0.15f, 0.21f, 0.16f, 0.00f),
            floatArrayOf(0.22f, 0.08f, 0.28f, 0.19f, 0.20f),
            floatArrayOf(0.44f, 0.07f, 0.30f, 0.19f, 0.25f),
            floatArrayOf(0.92f, 0.09f, 0.26f, 0.18f, 0.35f),
        )
        topSpecs.forEachIndexed { idx, s ->
            list += CloudPuff(
                x = s[0],
                y = s[1],
                scaleX = s[2],
                scaleY = s[3],
                speed = 0.024f + (idx % 3) * 0.007f,
                layer = 2,
                variant = idx + 2,
                minIntensity = s[4],
                alphaScale = 0.96f,
                bobSpeed = 0.65f + (idx % 3) * 0.15f,
                phase = idx * 1.7f,
            )
        }

        clouds = list.toTypedArray()
        cloudCount = clouds.size

        beads = Array(96) { i ->
            val normX = 0.06f + rng.nextFloat() * 0.88f
            val clusterBias = rng.nextFloat() * rng.nextFloat()
            FloorBead(
                normX = normX,
                heightOffset = 3f + clusterBias * 42f,
                radius = 1.0f + rng.nextFloat() * 2.1f,
                alpha = 0.35f + rng.nextFloat() * 0.60f,
                minIntensity = 0.16f + (i.toFloat() / 96f) * 0.62f,
                shimmerSpeed = 2.0f + rng.nextFloat() * 4.5f,
                phase = rng.nextFloat() * (PI.toFloat() * 2f),
            )
        }
    }

    fun updateBounds(w: Float, h: Float, r: Float) {
        width = w.coerceAtLeast(1f)
        height = h.coerceAtLeast(1f)
        cornerRadius = r.coerceIn(1f, mathMin(w, h) * 0.5f)
    }

    fun bottomFloorY(x: Float): Float {
        val r = cornerRadius
        val cx = x.coerceIn(0f, width)
        return when {
            cx < r -> {
                val dx = r - cx
                val dy = sqrt((r * r - dx * dx).coerceAtLeast(0f))
                height - r + dy
            }
            cx > width - r -> {
                val dx = cx - (width - r)
                val dy = sqrt((r * r - dx * dx).coerceAtLeast(0f))
                height - r + dy
            }
            else -> height
        }
    }

    fun step(dt: Float, intensity: Float, windStrength: Float) {
        elapsed += dt
        val rainFactor = smoothstep(0.08f, 1.0f, intensity)

        val windDriftMultiplier = 0.35f + windStrength * 2.6f
        for (i in 0 until cloudCount) {
            val c = clouds[i]
            c.x += c.speed * windDriftMultiplier * dt
            if (c.x > 1.38f) {
                c.x -= 1.76f
            }
        }

        if (rainFactor > 0.01f) {
            val dropsPerSecond = lerpFloat(16f, 250f, rainFactor * rainFactor * 0.65f + rainFactor * 0.35f)
            rainSpawnAccumulator += dropsPerSecond * dt
            val toSpawn = rainSpawnAccumulator.toInt().coerceAtMost(14)
            rainSpawnAccumulator -= toSpawn

            repeat(toSpawn) {
                spawnRaindrop(rainFactor, windStrength)
            }

            val ambientRate = lerpFloat(4f, 65f, rainFactor * rainFactor)
            ambientSplashAccumulator += ambientRate * dt
            val ambientCount = ambientSplashAccumulator.toInt().coerceAtMost(5)
            ambientSplashAccumulator -= ambientCount
            repeat(ambientCount) {
                val rx = width * (0.08f + rng.nextFloat() * 0.84f)
                val ry = bottomFloorY(rx) - 2f
                spawnSplashParticle(rx, ry, rainFactor, windStrength, isAmbient = true)
            }
        } else {
            rainSpawnAccumulator = 0f
            ambientSplashAccumulator = 0f
        }

        for (i in 0 until raindrops.size) {
            val drop = raindrops[i]
            if (!drop.active) continue

            val targetVx = (windStrength * 1.15f - 0.10f) * drop.vy * 0.42f
            drop.vx += (targetVx - drop.vx) * (dt * 7f).coerceAtMost(1f)

            drop.x += drop.vx * dt
            drop.y += drop.vy * dt

            if (drop.x < -width * 0.25f || drop.x > width * 1.25f) {
                drop.active = false
                continue
            }

            val floorY = bottomFloorY(drop.x)
            if (drop.y >= floorY - 2f) {
                drop.active = false
                if (rainFactor > 0.05f && drop.x in 4f..(width - 4f)) {
                    triggerFloorImpact(drop.x, floorY - 1.5f, rainFactor, windStrength, drop.depth)
                }
            }
        }

        val gravity = height * 1.18f
        val windPush = (windStrength - 0.15f) * width * 0.45f
        for (i in 0 until splashes.size) {
            val p = splashes[i]
            if (!p.active) continue

            p.age += dt
            if (p.age >= p.maxAge) {
                p.active = false
                continue
            }

            p.vx += (windPush * 0.35f - p.vx * 1.4f) * dt
            p.vy += gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt

            val floorY = bottomFloorY(p.x)
            if (p.y >= floorY - p.radius) {
                p.y = floorY - p.radius
                if (!p.bounced && p.vy > 45f) {
                    p.vy = -p.vy * 0.30f
                    p.vx *= 0.65f
                    p.bounced = true
                } else {
                    p.vy = 0f
                    p.vx *= 0.85f
                }
            }
        }

        for (i in 0 until ripples.size) {
            val rip = ripples[i]
            if (!rip.active) continue
            rip.age += dt
            if (rip.age >= rip.maxAge) {
                rip.active = false
            }
        }
    }

    private fun spawnRaindrop(rainFactor: Float, windStrength: Float) {
        var slot: Raindrop? = null
        for (i in 0 until raindrops.size) {
            if (!raindrops[i].active) {
                slot = raindrops[i]
                break
            }
        }
        val drop = slot ?: return

        val depth = 0.35f + rng.nextFloat() * 0.65f
        val baseVy = height * lerpFloat(0.95f, 1.85f, rainFactor) * (0.62f + 0.56f * depth)
        val vx = (windStrength * 1.15f - 0.10f) * baseVy * 0.42f

        val upwindPad = max(0f, vx / baseVy * height * 0.85f)
        val spawnX = rng.nextFloat() * (width + upwindPad * 1.2f) - upwindPad
        val spawnY = height * (0.04f + rng.nextFloat() * 0.19f)

        drop.active = true
        drop.x = spawnX
        drop.y = spawnY
        drop.spawnY = spawnY
        drop.vx = vx
        drop.vy = baseVy
        drop.depth = depth
        drop.length = height * lerpFloat(0.036f, 0.088f, rainFactor) * (0.55f + 0.65f * depth)
        drop.thickness = lerpFloat(1.1f, 2.3f, rainFactor) * (0.55f + 0.60f * depth)
        drop.alpha = (0.30f + 0.62f * depth) * lerpFloat(0.55f, 1.0f, rainFactor)
    }

    private fun triggerFloorImpact(
        x: Float,
        y: Float,
        rainFactor: Float,
        windStrength: Float,
        depth: Float,
    ) {
        val count = when {
            rainFactor < 0.30f -> if (rng.nextFloat() < 0.6f) 1 else 2
            rainFactor < 0.65f -> 2 + rng.nextInt(2)
            else -> 3 + rng.nextInt(2)
        }
        repeat(count) {
            spawnSplashParticle(x, y, rainFactor, windStrength, isAmbient = false, depth = depth)
        }

        if (depth > 0.62f && rng.nextFloat() < 0.45f) {
            val rip = ripples[rippleCursor % ripples.size]
            rippleCursor++
            rip.active = true
            rip.x = x
            rip.y = y
            rip.age = 0f
            rip.maxAge = 0.18f + rng.nextFloat() * 0.14f
            rip.maxRadius = 6f + rng.nextFloat() * 9f * rainFactor
        }
    }

    private fun spawnSplashParticle(
        x: Float,
        y: Float,
        rainFactor: Float,
        windStrength: Float,
        isAmbient: Boolean,
        depth: Float = 0.7f,
    ) {
        val p = splashes[splashCursor % splashes.size]
        splashCursor++

        val speedScale = if (isAmbient) 0.55f else 1.0f
        val upward = height * lerpFloat(0.14f, 0.42f, rainFactor) * (0.45f + rng.nextFloat() * 0.85f) * speedScale
        val spread = width * lerpFloat(0.10f, 0.24f, rainFactor) * (rng.nextFloat() * 2f - 1f) * speedScale
        val windBias = (windStrength - 0.15f) * width * 0.16f

        p.active = true
        p.x = x + (rng.nextFloat() * 6f - 3f)
        p.y = y - rng.nextFloat() * 3f
        p.vx = spread + windBias
        p.vy = -upward
        p.radius = lerpFloat(1.0f, 2.5f, rng.nextFloat()) * (0.65f + 0.45f * depth)
        p.alpha = 0.48f + rng.nextFloat() * 0.48f
        p.age = 0f
        p.maxAge = (0.24f + rng.nextFloat() * 0.36f) * lerpFloat(0.8f, 1.25f, rainFactor)
        p.bounced = false
    }
}
