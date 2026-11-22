package com.daykit.feature.focus.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.PaddingValues
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.designsystem.pressScale
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
    LockNow("Lock now", "Block on a timer", Icons.Rounded.HourglassTop),
    DailyLimit("Daily limit", "Cap daily time", Icons.Rounded.DataUsage),
    Routine("Routine", "Schedule app sets", Icons.Rounded.EventRepeat),
    ;

    val accent: Color
        @Composable get() = when (this) {
            LockNow -> MaterialTheme.extendedColors.accents.orange
            DailyLimit -> MaterialTheme.extendedColors.accents.teal
            Routine -> MaterialTheme.extendedColors.accents.indigo
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
 * Clean Apple HIG neutral action card for one [FocusMode]: uses standard [AppCard]
 * surface with a vibrant [AccentIconTile] squircle instead of a tinted chip wash.
 */
@Composable
fun FocusActionCard(
    mode: FocusMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = mode.example,
) {
    val accent = mode.accent
    AppCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
    ) {
        AccentIconTile(
            icon = mode.icon,
            accent = accent,
            size = 38.dp,
            iconSize = 21.dp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = mode.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Section title with optional mode badge and trailing action button. */
@Composable
fun FocusModeHeader(
    mode: FocusMode?,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode != null) {
            Icon(mode.icon, contentDescription = null, tint = mode.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
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

    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(shape)
            .background(MaterialTheme.extendedColors.inputField)
            .then(
                if (enabled) Modifier.border(1.dp, accent.copy(alpha = 0.45f), shape) else Modifier
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$text. Press and hold for ${holdMillis / 1000} seconds"
                if (!enabled) disabled()
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        holding = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val fill = scope.launch {
                            // Timed by frame timestamps, not an animation: the system
                            // "Remove animations" / animator-scale settings shrink
                            // animations, which would turn this into a plain tap.
                            val startProgress = progress.value
                            val startNanos = withFrameNanos { it }
                            do {
                                val heldMillis = (withFrameNanos { it } - startNanos) / 1_000_000
                                progress.snapTo(holdProgress(startProgress, heldMillis, holdMillis))
                            } while (progress.value < 1f)
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
                        color = accent,
                        size = Size(size.width * progress.value, size.height),
                    )
                },
        )
        Text(
            text = if (holding) "Keep holding…" else text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = when {
                !enabled -> MaterialTheme.extendedColors.textMuted
                holding || progress.value > 0.35f -> Color.White
                else -> accent
            },
        )
    }
}

/** Fill fraction after holding for [heldMillis], resuming from [startProgress]. */
internal fun holdProgress(startProgress: Float, heldMillis: Long, holdMillis: Int): Float =
    (startProgress + heldMillis.toFloat() / holdMillis).coerceIn(0f, 1f)

/** A solid vibrant button in a mode's accent, for the reversible saves (limits, routines). */
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
            .pressScale(enabled = enabled, pressedScale = 0.96f)
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (enabled) accent else MaterialTheme.extendedColors.inputField)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else MaterialTheme.extendedColors.textMuted,
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
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
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
