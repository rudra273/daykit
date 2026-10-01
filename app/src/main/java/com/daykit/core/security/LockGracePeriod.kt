package com.daykit.core.security

/**
 * How long the MSK survives after DayKit leaves the foreground, so a quick trip
 * to another app (copying an OTP, a share sheet) doesn't force a re-unlock.
 * Turning the screen off or closing the task still wipes the key immediately.
 */
object LockGracePeriod {
    const val DEFAULT_SECONDS = 10

    /** Capped at a minute: anything longer stops behaving like a privacy lock. */
    val OPTIONS_SECONDS = listOf(0, 5, 10, 30, 60)

    /** Unknown or missing values fall back to the default rather than "never". */
    fun sanitize(seconds: Int?): Int =
        if (seconds != null && seconds in OPTIONS_SECONDS) seconds else DEFAULT_SECONDS

    fun label(seconds: Int): String = when (seconds) {
        0 -> "Immediately"
        60 -> "After 1 minute"
        else -> "After $seconds seconds"
    }
}

/**
 * Wall-clock bookkeeping for the grace window. The delayed lock in
 * [SensitiveKeyManager] may never run while the process is frozen in the
 * background, so the deadline is also checked when DayKit returns.
 */
internal class BackgroundLockDeadline {
    private var deadline: Long? = null

    @Synchronized
    fun start(nowMillis: Long, graceMillis: Long) {
        deadline = nowMillis + graceMillis
    }

    @Synchronized
    fun clear() {
        deadline = null
    }

    /** True when a grace window was open and has elapsed. Always closes the window. */
    @Synchronized
    fun expiredOnReturn(nowMillis: Long): Boolean {
        val end = deadline ?: return false
        deadline = null
        return nowMillis >= end
    }
}
