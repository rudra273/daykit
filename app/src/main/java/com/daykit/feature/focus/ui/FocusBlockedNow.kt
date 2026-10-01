package com.daykit.feature.focus.ui

import com.daykit.feature.focus.data.ArmedSchedule
import com.daykit.feature.focus.data.FocusAppLimit
import com.daykit.feature.focus.data.FocusBlock
import com.daykit.feature.focus.data.FocusRecurrence
import com.daykit.feature.focus.data.FocusUsageTracker
import java.time.Instant
import java.time.ZoneId

/**
 * One app currently blocked, whichever mode did it. [progress] is the share of the
 * wait still to go (1 → 0 as it unlocks), so a ring around the app drains toward
 * the moment it opens. Shared by the Focus hero and the Today screen.
 */
internal data class BlockedNow(
    val key: String,
    val packageName: String,
    val label: String,
    val mode: FocusMode,
    val progress: Float,
    val caption: String,
)

internal fun buildBlockedNow(
    focusBlocks: List<FocusBlock>,
    appLimits: List<FocusAppLimit>,
    todayUsage: Map<String, Long>,
    activeSessions: List<ArmedSchedule>,
    labelFor: (String) -> String?,
    nowMillis: Long,
): List<BlockedNow> {
    val endOfDay = FocusUsageTracker.getEndOfDayMillis(nowMillis)
    val locks = focusBlocks.sortedBy { it.lockUntilMillis }.map { block ->
        BlockedNow(
            key = "lock-${block.packageName}",
            packageName = block.packageName,
            label = labelFor(block.packageName) ?: block.label,
            mode = FocusMode.LockNow,
            progress = block.remainingFraction(nowMillis),
            caption = formatCompactRemaining(block.lockUntilMillis - nowMillis),
        )
    }
    val limits = appLimits.filter { limit ->
        limit.enabled && (todayUsage[limit.packageName] ?: 0L) >= limit.dailyLimitMinutes * 60_000L
    }.map { limit ->
        BlockedNow(
            key = "limit-${limit.packageName}",
            packageName = limit.packageName,
            label = labelFor(limit.packageName) ?: limit.packageName,
            mode = FocusMode.DailyLimit,
            // When the limit was hit isn't recorded, so this drains across the day to midnight.
            progress = (endOfDay - nowMillis).toFloat() / (24 * 60 * 60_000L),
            caption = "Midnight",
        )
    }
    val routines = activeSessions.flatMap { session ->
        val span = (session.endMillis - session.startMillis).coerceAtLeast(1L)
        val remaining = (session.endMillis - nowMillis).coerceAtLeast(0L)
        session.packageNames.map { pkg ->
            BlockedNow(
                key = "routine-${session.scheduleId}-$pkg",
                packageName = pkg,
                label = labelFor(pkg) ?: pkg,
                mode = FocusMode.Routine,
                progress = remaining.toFloat() / span,
                caption = formatClock(session.endMillis),
            )
        }
    }
    return locks + limits + routines
}

/** "2h 47m", "47m", "8s" — seconds only once under a minute, to fit under an icon. */
internal fun formatCompactRemaining(remainingMillis: Long): String {
    val totalSeconds = remainingMillis.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

/** "5:00 PM" for a timestamp today — the caption for a routine's end. */
internal fun formatClock(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return FocusRecurrence.formatTime(t.hour, t.minute)
}
