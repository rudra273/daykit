package com.daykit.feature.eventlight.ui

import android.content.ActivityNotFoundException
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppSwitch
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.permissions.PermissionIntents
import com.daykit.feature.eventlight.data.EVENT_LIGHT_MAX_THICKNESS_DP
import com.daykit.feature.eventlight.data.EVENT_LIGHT_MIN_THICKNESS_DP
import com.daykit.feature.eventlight.data.EventLightGradientStops
import com.daykit.feature.eventlight.data.EventLightStore
import com.daykit.feature.eventlight.data.edgeLightTintColor
import com.daykit.feature.eventlight.service.EventLightRingCanvas
import com.daykit.feature.eventlight.service.EventLightService
import com.daykit.feature.widget.EventLightWidgetProvider
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun EventLightScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by EventLightStore.rememberSettings()
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    fun refreshPermission() {
        overlayGranted = Settings.canDrawOverlays(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Event Light", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = if (overlayGranted) 10.dp else Spacing.md,
                ),
        ) {
            if (!overlayGranted) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.extendedColors.textMuted,
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column {
                            Text(
                                text = "Display over other apps",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Needed so the edge light can glow on top of video call apps.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.extendedColors.textMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton(
                        text = "Grant permission",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            try {
                                context.startActivity(PermissionIntents.overlaySettings(context))
                            } catch (_: ActivityNotFoundException) {
                                refreshPermission()
                            }
                        },
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }

            // Live macOS-style edge light preview
            EdgeLightPreviewCard(
                thicknessDp = settings.thicknessDp,
                colorTemp = settings.colorTemp,
                enabled = settings.enabled,
            )

            Spacer(Modifier.height(Spacing.md))

            AppCard(contentPadding = PaddingValues(0.dp)) {
                AppListRow(
                    headline = "Edge Light",
                    supporting = "Soft glowing ring around your screen during video calls.",
                    enabled = overlayGranted,
                    trailing = {
                        AppSwitch(
                            checked = settings.enabled,
                            enabled = overlayGranted,
                            onCheckedChange = { enabled ->
                                refreshPermission()
                                if (enabled && !overlayGranted) return@AppSwitch
                                EventLightStore.setEnabled(context, enabled)
                                if (enabled) {
                                    EventLightService.start(context)
                                } else {
                                    EventLightService.stop(context)
                                }
                                EventLightWidgetProvider.refreshAll(context)
                            },
                        )
                    },
                )
            }

            SectionHeader("Light controls")
            AppCard(modifier = Modifier.fillMaxWidth()) {
                val thicknessFraction = (
                    (settings.thicknessDp - EVENT_LIGHT_MIN_THICKNESS_DP) /
                        (EVENT_LIGHT_MAX_THICKNESS_DP - EVENT_LIGHT_MIN_THICKNESS_DP)
                    ).coerceIn(0f, 1f)

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    // Option 1: Thickness (macOS slim track + white pill thumb)
                    MacThicknessSlider(
                        fraction = thicknessFraction,
                        onFractionChange = { f ->
                            val dp = EVENT_LIGHT_MIN_THICKNESS_DP +
                                f * (EVENT_LIGHT_MAX_THICKNESS_DP - EVENT_LIGHT_MIN_THICKNESS_DP)
                            EventLightStore.setThickness(context, dp)
                        },
                    )

                    // Option 2: Color temperature (macOS yellow -> white -> blue pill)
                    MacColorTempSlider(
                        value = settings.colorTemp,
                        onValueChange = { temp ->
                            EventLightStore.setColorTemp(context, temp)
                            EventLightWidgetProvider.refreshAll(context)
                        },
                    )
                }
            }

            Spacer(
                Modifier.height(
                    Spacing.xl +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }
    }
}

@Composable
private fun EdgeLightPreviewCard(
    thicknessDp: Float,
    colorTemp: Float,
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.55f)
            .clip(shape)
            .background(Color(0xFF0E1014))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
        contentAlignment = Alignment.Center,
    ) {
        // Scale ring thickness proportionally so the preview card matches full-screen proportions
        val previewThickness = (thicknessDp * 0.42f).coerceIn(3f, 16f)
        EventLightRingCanvas(
            thicknessDp = previewThickness,
            colorTemp = colorTemp,
            cornerRadiusDp = 24.dp,
            edgeInsetDp = 7.dp,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = if (enabled) "Edge Light On" else "Preview",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

/**
 * Top macOS Edge Light slider: hollow sun icon on the left, slim rounded track,
 * white capsule thumb. Controls ring thickness.
 */
@Composable
private fun MacThicknessSlider(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onFractionChange)
    val density = LocalDensity.current
    val thumbWidthDp = 30.dp
    val thumbHeightDp = 22.dp
    val trackHeightDp = 7.dp
    val thumbWidthPx = with(density) { thumbWidthDp.toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    val iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    val activeTrackColor = if (MaterialTheme.extendedColors.isDark) {
        Color(0xFF9EA2AA)
    } else {
        Color(0xFF8E939C)
    }
    val inactiveTrackColor = if (MaterialTheme.extendedColors.isDark) {
        Color(0xFF474A50)
    } else {
        Color(0xFFD5D8DF)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Edge light thickness" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MacSunIcon(
            striped = false,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (trackWidthPx > thumbWidthPx) {
                            val usable = trackWidthPx - thumbWidthPx
                            val f = ((offset.x - thumbWidthPx / 2f) / usable).coerceIn(0f, 1f)
                            currentOnChange(f)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        if (trackWidthPx > thumbWidthPx) {
                            val usable = trackWidthPx - thumbWidthPx
                            val f = ((change.position.x - thumbWidthPx / 2f) / usable).coerceIn(0f, 1f)
                            currentOnChange(f)
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // Inactive track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeightDp)
                    .clip(CircleShape)
                    .background(inactiveTrackColor),
            )

            // Active track up to thumb center
            val usablePx = (trackWidthPx - thumbWidthPx).coerceAtLeast(0f)
            val thumbOffsetPx = (fraction.coerceIn(0f, 1f) * usablePx)
            val activeWidthDp = with(density) { (thumbOffsetPx + thumbWidthPx / 2f).toDp() }
            Box(
                modifier = Modifier
                    .width(activeWidthDp)
                    .height(trackHeightDp)
                    .clip(CircleShape)
                    .background(activeTrackColor),
            )

            // White pill thumb (matching macOS Control Center)
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                    .size(width = thumbWidthDp, height = thumbHeightDp)
                    .shadow(elevation = 3.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.08f), CircleShape),
            )
        }
    }
}

/**
 * Bottom macOS Edge Light slider: striped-sun temperature icon on the left,
 * thick warm-amber -> white -> cool-blue gradient pill, circular thumb filled
 * with the selected color inside a crisp white ring border.
 */
@Composable
private fun MacColorTempSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onValueChange)
    val density = LocalDensity.current
    val barHeightDp = 32.dp
    val thumbSizeDp = 26.dp
    val edgePaddingDp = 3.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val edgePaddingPx = with(density) { edgePaddingDp.toPx() }
    var barWidthPx by remember { mutableFloatStateOf(0f) }

    val iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    val selectedColor = edgeLightTintColor(value)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Edge light color temperature" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MacSunIcon(
            striped = true,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(barHeightDp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(EventLightGradientStops))
                .border(0.75.dp, Color.Black.copy(alpha = 0.14f), CircleShape)
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val usable = (barWidthPx - thumbSizePx - edgePaddingPx * 2f).coerceAtLeast(1f)
                        val f = ((offset.x - edgePaddingPx - thumbSizePx / 2f) / usable).coerceIn(0f, 1f)
                        currentOnChange(f)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val usable = (barWidthPx - thumbSizePx - edgePaddingPx * 2f).coerceAtLeast(1f)
                        val f = ((change.position.x - edgePaddingPx - thumbSizePx / 2f) / usable).coerceIn(0f, 1f)
                        currentOnChange(f)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val usablePx = (barWidthPx - thumbSizePx - edgePaddingPx * 2f).coerceAtLeast(0f)
            val thumbOffsetPx = edgePaddingPx + value.coerceIn(0f, 1f) * usablePx

            // Circular thumb showing selected color inside a crisp white ring
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                    .size(thumbSizeDp)
                    .shadow(elevation = 2.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(selectedColor)
                    .border(3.dp, Color.White, CircleShape)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.16f), CircleShape),
            )
        }
    }
}

/**
 * Crisp macOS-style sun glyph:
 * - [striped] = false: hollow center circle with 8 short radial rays (thickness slider)
 * - [striped] = true: circle with horizontal shading lines in lower half + 8 rays (temperature slider)
 */
@Composable
private fun MacSunIcon(
    striped: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension * 0.22f
        val rayInner = size.minDimension * 0.34f
        val rayOuter = size.minDimension * 0.46f
        val stroke = size.minDimension * 0.085f

        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = stroke),
        )

        if (striped) {
            // Subtle horizontal lines across the lower half of the sun disc
            for (step in 0..2) {
                val y = cy + radius * (0.15f + step * 0.32f)
                val dy = y - cy
                val halfChord = kotlin.math.sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
                if (halfChord > 0.5f) {
                    drawLine(
                        color = tint,
                        start = Offset(cx - halfChord, y),
                        end = Offset(cx + halfChord, y),
                        strokeWidth = stroke * 0.72f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                color = tint,
                start = Offset(cx + dx * rayInner, cy + dy * rayInner),
                end = Offset(cx + dx * rayOuter, cy + dy * rayOuter),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
