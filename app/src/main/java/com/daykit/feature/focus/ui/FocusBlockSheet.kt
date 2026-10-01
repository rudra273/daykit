package com.daykit.feature.focus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.focus.data.FocusRecurrence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private data class DurationPreset(val label: String, val millis: Long)

private val PRESETS = listOf(
    DurationPreset("15m", 15 * 60_000L),
    DurationPreset("30m", 30 * 60_000L),
    DurationPreset("1h", 60 * 60_000L),
    DurationPreset("3h", 3 * 60 * 60_000L),
    DurationPreset("6h", 6 * 60 * 60_000L),
)

/** The ring is full at this length, so a longer lock visibly weighs more. */
private const val RING_FULL_MILLIS = 6 * 60 * 60_000L

/**
 * Bottom sheet to start a Lock now block on [appLabel] (one app or an app set).
 *
 * Rather than explaining the rules, it shows the outcome: a coral ring sized to
 * the duration and the wall-clock time the app opens again. The block is
 * irreversible (no early cancel, not even with the PIN), so it commits through
 * [HoldToConfirmButton] rather than a tap. [leading] is the app icon or set
 * swatch shown beside the title.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FocusBlockSheet(
    appLabel: String,
    onConfirm: (durationMillis: Long) -> Unit,
    onDismiss: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val accent = FocusMode.LockNow.accent
    var selectedPreset by remember { mutableStateOf(PRESETS[2].millis) }
    var customOpen by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf(60) }

    val customMillis = customMinutes * 60_000L
    val durationMillis = if (customOpen) customMillis else selectedPreset
    val valid = durationMillis > 0L

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.let {
                    it()
                    Spacer(Modifier.width(Spacing.md))
                }
                Text(
                    text = "Lock $appLabel",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            CountdownRing(
                progress = (durationMillis.toFloat() / RING_FULL_MILLIS).coerceIn(0.04f, 1f),
                color = accent,
                size = 120.dp,
                strokeWidth = 8.dp,
            ) {
                Text(
                    text = if (valid) formatFocusDuration(durationMillis) else "–",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = if (valid) {
                    "Opens again ${formatOpensAt(System.currentTimeMillis() + durationMillis)}"
                } else {
                    "Pick how long"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.lg))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PRESETS.forEach { preset ->
                    FilterChipButton(
                        text = preset.label,
                        selected = !customOpen && selectedPreset == preset.millis,
                        onClick = {
                            selectedPreset = preset.millis
                            customOpen = false
                        },
                    )
                }
                FilterChipButton(
                    text = "Custom",
                    selected = customOpen,
                    onClick = {
                        if (!customOpen) customMinutes = (selectedPreset / 60_000L).toInt().coerceAtLeast(5)
                        customOpen = true
                    },
                )
            }

            if (customOpen) {
                Spacer(Modifier.height(Spacing.md))
                FocusDurationStepper(
                    minutes = customMinutes,
                    onMinutesChange = { customMinutes = it },
                    maxMinutes = 99 * 60,
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "No way out, not even your PIN",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            HoldToConfirmButton(
                text = "Hold to lock",
                accent = accent,
                enabled = valid,
                onConfirm = { onConfirm(durationMillis) },
            )
        }
    }
}

/** "at 4:30 PM", "tomorrow at 9:00 AM", or "on Fri at 9:00 AM". */
internal fun formatOpensAt(untilMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(untilMillis).atZone(zone).toLocalDateTime()
    val time = FocusRecurrence.formatTime(at.hour, at.minute)
    val today = LocalDate.now(zone)
    return when (at.toLocalDate()) {
        today -> "at $time"
        today.plusDays(1) -> "tomorrow at $time"
        else -> "on ${FocusRecurrence.shortLabel(at.dayOfWeek)} at $time"
    }
}
