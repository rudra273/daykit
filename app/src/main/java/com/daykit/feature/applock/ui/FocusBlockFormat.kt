package com.daykit.feature.applock.ui

/**
 * Formats a remaining-time span (in millis) as a compact human string, e.g.
 * "2h 47m", "47m 12s", "8s". Used by the focus-block countdown screen and the
 * app-lock row's remaining-time chip.
 */
fun formatFocusRemaining(remainingMillis: Long): String {
    if (remainingMillis <= 0L) return "0s"
    val totalSeconds = remainingMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/** Formats a chosen duration (millis) for confirmation copy, e.g. "3h", "1h 30m", "45m". */
fun formatFocusDuration(durationMillis: Long): String {
    val totalMinutes = durationMillis / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
