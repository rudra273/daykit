package com.daykit.core.security

import org.junit.Assert.*
import org.junit.Test

class LockGracePeriodTest {
    @Test fun sanitizeFallsBackToDefaultForUnknownValues() {
        assertEquals(LockGracePeriod.DEFAULT_SECONDS, LockGracePeriod.sanitize(null))
        assertEquals(LockGracePeriod.DEFAULT_SECONDS, LockGracePeriod.sanitize(-1))
        assertEquals(LockGracePeriod.DEFAULT_SECONDS, LockGracePeriod.sanitize(3600))
        assertEquals(0, LockGracePeriod.sanitize(0))
        assertEquals(30, LockGracePeriod.sanitize(30))
    }

    @Test fun returningWithinTheWindowKeepsTheKey() {
        val deadline = BackgroundLockDeadline()
        deadline.start(nowMillis = 1_000, graceMillis = 10_000)
        assertFalse(deadline.expiredOnReturn(nowMillis = 10_999))
    }

    @Test fun returningAfterTheWindowLocks() {
        val deadline = BackgroundLockDeadline()
        deadline.start(nowMillis = 1_000, graceMillis = 10_000)
        assertTrue(deadline.expiredOnReturn(nowMillis = 11_000))
    }

    @Test fun aReturnClosesTheWindow() {
        val deadline = BackgroundLockDeadline()
        deadline.start(nowMillis = 0, graceMillis = 5_000)
        assertFalse(deadline.expiredOnReturn(nowMillis = 1_000))
        assertFalse(deadline.expiredOnReturn(nowMillis = 60_000))
    }

    @Test fun noWindowNeverExpires() {
        val deadline = BackgroundLockDeadline()
        assertFalse(deadline.expiredOnReturn(nowMillis = Long.MAX_VALUE))
        deadline.start(nowMillis = 0, graceMillis = 5_000)
        deadline.clear()
        assertFalse(deadline.expiredOnReturn(nowMillis = 60_000))
    }
}
