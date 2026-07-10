package com.daykit.core.security

import java.util.concurrent.TimeUnit

/**
 * User-facing message for a failed [PinVerifyResult]. Returns null for
 * [PinVerifyResult.Success] since success has no error to show.
 */
fun PinVerifyResult.errorMessageOrNull(): String? = when (this) {
    is PinVerifyResult.Success -> null
    is PinVerifyResult.Wrong -> "Wrong PIN"
    is PinVerifyResult.LockedOut -> "Too many attempts. Try again in ${formatDuration(remainingMillis)}."
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(1)
    val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds)
    val seconds = totalSeconds - TimeUnit.MINUTES.toSeconds(minutes)
    return when {
        minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
