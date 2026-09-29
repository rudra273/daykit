package com.daykit.feature.focus.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.focus.data.FocusGroup
import com.daykit.feature.focus.data.FocusRecurrence
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/**
 * The three things Focus does, each with one icon and one accent used on every
 * surface that belongs to it — tiles, sheets, section headers, and the ring
 * around a blocked app. Consistent color is what lets the screen explain itself
 * without paragraphs: a coral ring always means "a timer you can't undo", teal
 * always means "a daily budget", purple always means "a routine".
 *
 * [example] is a sample value shown on the tile instead of a description.
 */
enum class FocusMode(val label: String, val example: String, val icon: ImageVector) {
    LockNow("Lock now", "Instagram · 1h", Icons.Rounded.HourglassTop),
    DailyLimit("Daily limit", "YouTube · 30m a day", Icons.Rounded.DataUsage),
    Routine("Routine", "Social · Mon–Fri 9–5", Icons.Rounded.EventRepeat),
    ;

    val accent: Color
        @Composable get() = when (this) {
            LockNow -> MaterialTheme.extendedColors.accents.orange
            DailyLimit -> MaterialTheme.extendedColors.accents.teal
            Routine -> MaterialTheme.extendedColors.accents.purple
        }
}

/** Colors an app set can take; indexed by `FocusGroup.colorIndex`. */
@Composable
fun focusSetPalette(): List<Color> {
    val accents = MaterialTheme.extendedColors.accents
    return listOf(
        accents.blue, accents.teal, accents.green, accents.red,
        accents.orange, accents.yellow, accents.purple, accents.pink, accents.indigo,
    )
}

@Composable
fun focusSetColor(colorIndex: Int): Color {
    val palette = focusSetPalette()
    return palette[colorIndex.coerceIn(palette.indices)]
}

/**
 * Entry point for one [FocusMode]. [expanded] is the first-visit form — a full
 * row with the example underneath — and doubles as onboarding; once the user has
 * anything set up the tiles collapse to compact pills side by side.
 */
@Composable
fun FocusModeTile(
    mode: FocusMode,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = mode.accent
    val shape = MaterialTheme.shapes.large
    if (expanded) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(accent.asAccentContainer())
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(mode.icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = mode.example,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = accent)
        }
    } else {
        Row(
            modifier = modifier
                .clip(shape)
                .background(accent.asAccentContainer())
                .clickable(onClick = onClick)
                .heightIn(min = 44.dp)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(mode.icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Section title prefixed by its mode's icon, so lists inherit the tile's color. */
@Composable
fun FocusModeHeader(
    mode: FocusMode?,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode != null) {
            Icon(mode.icon, contentDescription = null, tint = mode.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.extendedColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * A ring that is full at [progress] = 1 and drains clockwise toward 0. Wraps
 * [content] (usually an app icon) so a blocked app visibly carries its timer.
 */
@Composable
fun CountdownRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    strokeWidth: Dp = 3.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val track = MaterialTheme.extendedColors.divider
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** Monday-first single letters, the picked days in [accent] and the rest muted. */
@Composable
fun DayLetters(daysMask: Int, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val on = FocusRecurrence.includes(daysMask, day)
            Text(
                text = FocusRecurrence.shortLabel(day).take(1),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                color = if (on) accent else MaterialTheme.extendedColors.textMuted.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * A 24-hour strip with the blocked window filled in. A window that ends at or
 * before it starts wraps past midnight, so it's drawn as two segments. [nowMinute]
 * adds a tick for the current time, which makes "is it on right now?" a glance.
 */
@Composable
fun DayBand(
    startMinute: Int,
    endMinute: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    nowMinute: Int? = null,
    height: Dp = 6.dp,
) {
    val track = MaterialTheme.extendedColors.inputField
    val tick = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.small),
    ) {
        val day = 24f * 60f
        drawRect(track)
        fun span(from: Int, to: Int) {
            drawRect(
                color = accent,
                topLeft = Offset(size.width * from / day, 0f),
                size = Size(size.width * (to - from) / day, size.height),
            )
        }
        if (endMinute > startMinute) {
            span(startMinute, endMinute)
        } else {
            span(startMinute, 24 * 60)
            span(0, endMinute)
        }
        if (nowMinute != null) {
            val x = size.width * nowMinute / day
            drawRect(tick, topLeft = Offset(x - 1.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
        }
    }
}

/**
 * Commits only after being held for [holdMillis]; letting go early drains the
 * fill back. Replaces a typed confirmation for irreversible actions — it is just
 * as deliberate, but needs no keyboard and shows its own progress.
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    accent: Color,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 2_000,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val latestConfirm by rememberUpdatedState(onConfirm)
    var holding by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (enabled) accent.asAccentContainer() else MaterialTheme.extendedColors.inputField)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        holding = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val fill = scope.launch {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = (holdMillis * (1f - progress.value)).toInt(),
                                    easing = LinearEasing,
                                ),
                            )
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            latestConfirm()
                        }
                        tryAwaitRelease()
                        holding = false
                        if (progress.value < 1f) {
                            fill.cancel()
                            scope.launch { progress.animateTo(0f, tween(250)) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        color = accent.copy(alpha = 0.35f),
                        size = Size(size.width * progress.value, size.height),
                    )
                },
        )
        Text(
            text = if (holding) "Keep holding…" else text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) accent else MaterialTheme.extendedColors.textMuted,
        )
    }
}

/** A plain tap button in a mode's accent, for the reversible saves (limits, routines). */
@Composable
fun FocusAccentButton(
    text: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (enabled) accent.asAccentContainer() else MaterialTheme.extendedColors.inputField)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) accent else MaterialTheme.extendedColors.textMuted,
        )
    }
}

/** A rounded budget bar: [progress] of the track filled in [color]. */
@Composable
fun FocusBudgetBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val track = MaterialTheme.extendedColors.inputField
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.small),
    ) {
        drawRect(track)
        drawRect(color, size = Size(size.width * progress.coerceIn(0f, 1f), size.height))
    }
}

/**
 * An app set as a pill: its color dot, name, and app count. [selected] outlines
 * it in the set's own color (used by the routine editor's set picker).
 */
@Composable
fun FocusSetChip(
    set: FocusGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val color = focusSetColor(set.colorIndex)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) color.asAccentContainer() else MaterialTheme.extendedColors.inputField)
            .then(if (selected) Modifier.border(1.dp, color, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${set.name} · ${set.packageNames.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Outlined "+ New set" pill that sits at the end of a row of [FocusSetChip]s. */
@Composable
fun FocusNewSetChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.extendedColors.divider, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "New set",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extendedColors.textMuted,
        )
    }
}
