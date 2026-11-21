package com.daykit.feature.canvas.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.dailywidgets.DailyWidgetDataProvider
import com.daykit.feature.dailywidgets.DailyWidgetRenderer
import com.daykit.feature.dailywidgets.DailyWidgetStyle
import com.daykit.feature.dailywidgets.DailyWidgetType
import com.daykit.feature.weather.WeatherIcons
import com.daykit.feature.weather.WeatherPrefs
import com.daykit.feature.widget.BaseDailyCanvasWidgetProvider
import kotlinx.coroutines.delay

private enum class DailyFilter(val label: String) {
    ALL("All"),
    LIQUID_GLASS("Liquid Glass"),
    SOLID("Solid"),
}

/**
 * Multi-widget gallery under Canvas -> Daily Widgets.
 * Displays all Liquid Glass and Solid daily widgets with live previews and one-tap Home Screen pinning.
 */
@Composable
fun DailyWidgetsGallery(
    refreshTrigger: Int,
    onPinProvider: (Class<*>) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var filter by rememberSaveable { mutableStateOf(DailyFilter.ALL) }
    var expandWeatherStudio by rememberSaveable { mutableStateOf(false) }

    // Live 1-second ticker for clock & solar previews
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val allWidgets = remember { DailyWidgetType.entries }
    val filteredWidgets = remember(filter, allWidgets) {
        when (filter) {
            DailyFilter.ALL -> allWidgets
            DailyFilter.LIQUID_GLASS -> allWidgets.filter { it.style == DailyWidgetStyle.LIQUID_GLASS }
            DailyFilter.SOLID -> allWidgets.filter { it.style == DailyWidgetStyle.SOLID }
        }
    }

    val pinnedCounts = remember(refreshTrigger, allWidgets) {
        val mgr = AppWidgetManager.getInstance(context)
        allWidgets.associateWith { type ->
            val cls = BaseDailyCanvasWidgetProvider.providerClassFor(type)
            runCatching { mgr.getAppWidgetIds(ComponentName(context, cls)).size }.getOrDefault(0)
        }
    }

    val solarState = remember(refreshTrigger, nowMillis / 60_000L) {
        DailyWidgetDataProvider.loadSolarArcState(context, nowMillis)
    }
    val stepsState = remember(refreshTrigger, nowMillis / 60_000L) {
        DailyWidgetDataProvider.loadStepsState(context, nowMillis)
    }
    val batteryState = remember(refreshTrigger, nowMillis / 30_000L) {
        DailyWidgetDataProvider.loadBatteryState(context)
    }
    val agendaState = remember(refreshTrigger, nowMillis / 60_000L) {
        DailyWidgetDataProvider.loadAgendaState(nowMillis)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Style filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DailyFilter.entries.forEach { item ->
                val count = when (item) {
                    DailyFilter.ALL -> allWidgets.size
                    DailyFilter.LIQUID_GLASS -> allWidgets.count { it.style == DailyWidgetStyle.LIQUID_GLASS }
                    DailyFilter.SOLID -> allWidgets.count { it.style == DailyWidgetStyle.SOLID }
                }
                FilterChipButton(
                    text = "${item.label} ($count)",
                    selected = filter == item,
                    onClick = { filter = item },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.xs,
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(filteredWidgets, key = { it.id }) { type ->
                val pinned = pinnedCounts[type] ?: 0
                val providerClass = BaseDailyCanvasWidgetProvider.providerClassFor(type)

                if (type == DailyWidgetType.GLASS_WEATHER) {
                    AppCard {
                        DailyWidgetHeader(
                            type = type,
                            trailingAction = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.extendedColors.inputField)
                                        .clickable { expandWeatherStudio = !expandWeatherStudio }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = if (expandWeatherStudio) "Done" else "Tune",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        if (expandWeatherStudio) {
                            Box(modifier = Modifier.height(520.dp)) {
                                DailyWeatherWidgetShowcase(
                                    pinnedCount = pinned,
                                    onPin = { onPinProvider(providerClass) },
                                    onShowMessage = onShowMessage,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            val weatherSnap = remember(refreshTrigger) { WeatherPrefs.loadSnapshot(context) }
                            val useF = remember(refreshTrigger) { WeatherPrefs.useFahrenheit(context) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color(0xFF102238),
                                            1f to Color(0xFF0A121D),
                                        ),
                                    )
                                    .clickable { expandWeatherStudio = true }
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CompactGlassWeatherCube(
                                    snapshot = weatherSnap,
                                    useFahrenheit = useF,
                                    modifier = Modifier.size(188.dp),
                                )
                            }
                            Spacer(Modifier.height(Spacing.md))
                            DailyWidgetFooter(
                                type = type,
                                pinnedCount = pinned,
                                onPin = { onPinProvider(providerClass) },
                            )
                        }
                    }
                } else {
                    AppCard {
                        DailyWidgetHeader(type = type)
                        Spacer(Modifier.height(Spacing.sm))

                        // Showcase stage backdrop tailored to Liquid Glass vs Solid widgets
                        val stageBrush = when (type) {
                            DailyWidgetType.SOLAR_ARC -> Brush.linearGradient(
                                listOf(Color(0xFF0C1018), Color(0xFF1D283A), Color(0xFF0E131D)),
                            )
                            DailyWidgetType.RADIAL_DIAL_CLOCK -> Brush.linearGradient(
                                listOf(Color(0xFF152414), Color(0xFF284222), Color(0xFF0F1A10)),
                            )
                            DailyWidgetType.LIQUID_GLASS_CLOCK -> Brush.linearGradient(
                                listOf(Color(0xFF0D4E9C), Color(0xFF1D74D8), Color(0xFF0E5BB8)),
                            )
                            DailyWidgetType.STEPS_BAR_CHART -> Brush.linearGradient(
                                listOf(Color(0xFF1E2220), Color(0xFF2B302D)),
                            )
                            else -> Brush.linearGradient(
                                listOf(
                                    if (isDark) Color(0xFF101216) else Color(0xFFE9E9E5),
                                    if (isDark) Color(0xFF181B21) else Color(0xFFE0E0DB),
                                ),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(stageBrush)
                                .padding(horizontal = 14.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val widthFraction = if (type.aspectRatio <= 1.1f) 0.64f else 1.0f
                            DailyWidgetLivePreview(
                                type = type,
                                nowMillis = nowMillis,
                                solarState = solarState,
                                stepsState = stepsState,
                                batteryState = batteryState,
                                agendaState = agendaState,
                                darkSurface = isDark,
                                modifier = Modifier
                                    .fillMaxWidth(widthFraction)
                                    .aspectRatio(type.aspectRatio),
                            )
                        }

                        Spacer(Modifier.height(Spacing.md))

                        // Footer: pinned status + Add to Home
                        DailyWidgetFooter(
                            type = type,
                            pinnedCount = pinned,
                            onPin = { onPinProvider(providerClass) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyWidgetHeader(
    type: DailyWidgetType,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = type.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val isGlass = type.style == DailyWidgetStyle.LIQUID_GLASS
                val pillBg = if (isGlass) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.extendedColors.inputField
                }
                val pillFg = if (isGlass) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.extendedColors.textMuted
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(pillBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = type.style.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = pillFg,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = type.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
            )
        }
        if (trailingAction != null) {
            Spacer(Modifier.width(8.dp))
            trailingAction()
        }
    }
}

@Composable
fun DailyWidgetLivePreview(
    type: DailyWidgetType,
    nowMillis: Long,
    solarState: com.daykit.feature.dailywidgets.SolarArcState,
    stepsState: com.daykit.feature.dailywidgets.StepsChartState,
    batteryState: com.daykit.feature.dailywidgets.BatteryState,
    agendaState: com.daykit.feature.dailywidgets.AgendaState,
    darkSurface: Boolean,
    overWallpaperBlur: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val rect = RectF(0f, 0f, size.width, size.height)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            when (type) {
                DailyWidgetType.SOLAR_ARC ->
                    DailyWidgetRenderer.drawSolarArc(nc, rect, solarState, overWallpaperBlur = overWallpaperBlur)
                DailyWidgetType.RADIAL_DIAL_CLOCK ->
                    DailyWidgetRenderer.drawRadialDialClock(nc, rect, nowMillis, overWallpaperBlur = overWallpaperBlur)
                DailyWidgetType.LIQUID_GLASS_CLOCK ->
                    DailyWidgetRenderer.drawLiquidGlassClock(nc, rect, nowMillis, overWallpaperBlur = overWallpaperBlur)
                DailyWidgetType.STEPS_BAR_CHART ->
                    DailyWidgetRenderer.drawStepsBarChart(nc, rect, stepsState)
                DailyWidgetType.BENTO_BATTERY ->
                    DailyWidgetRenderer.drawBentoBattery(nc, rect, batteryState, darkSurface = darkSurface)
                DailyWidgetType.BENTO_AGENDA ->
                    DailyWidgetRenderer.drawBentoAgenda(nc, rect, agendaState, darkSurface = darkSurface)
                DailyWidgetType.GLASS_WEATHER -> Unit
            }
        }
    }
}

@Composable
private fun DailyWidgetFooter(
    type: DailyWidgetType,
    pinnedCount: Int,
    onPin: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                text = "${type.style.label} · ${type.sizeLabel}",
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

@Composable
private fun CompactGlassWeatherCube(
    snapshot: com.daykit.feature.weather.WeatherSnapshot,
    useFahrenheit: Boolean,
    modifier: Modifier = Modifier,
) {
    val isNight = snapshot.isNight()
    val shape = RoundedCornerShape(36.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (isNight) {
                        listOf(Color(0xFF1C263B), Color(0xFF131A29), Color(0xFF1D273A))
                    } else {
                        listOf(Color(0xFF3E9BFF), Color(0xFF2162A6), Color(0xFF284B70))
                    },
                ),
            )
            .border(1.25.dp, Color.White.copy(alpha = 0.55f), shape)
            .padding(18.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = snapshot.city,
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Text(
                text = "${snapshot.displayTemp(useFahrenheit)}${snapshot.unitSymbol(useFahrenheit)}",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Canvas(modifier = Modifier.size(28.dp)) {
                drawIntoCanvas { c ->
                    if (isNight) {
                        WeatherIcons.drawMoon(c.nativeCanvas, size.width * 0.45f, size.height * 0.5f, size.minDimension)
                    } else {
                        WeatherIcons.drawSun(c.nativeCanvas, size.width * 0.45f, size.height * 0.5f, size.minDimension)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.conditionLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Text(
                text = "H:${snapshot.displayHigh(useFahrenheit)}°  L:${snapshot.displayLow(useFahrenheit)}°",
                color = Color.White.copy(alpha = 0.84f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}
