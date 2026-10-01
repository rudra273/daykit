package com.daykit.feature.focus

import com.daykit.feature.focus.data.FocusBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusBlockProgressTest {
    private fun block(start: Long, until: Long) = FocusBlock("pkg", "App", lockUntilMillis = until, startedAtMillis = start)

    @Test
    fun drainsFromFullToEmptyOverTheBlock() {
        val b = block(start = 1_000, until = 61_000)
        assertEquals(1f, b.remainingFraction(1_000), 0.0001f)
        assertEquals(0.5f, b.remainingFraction(31_000), 0.0001f)
        assertEquals(0f, b.remainingFraction(61_000), 0.0001f)
    }

    @Test
    fun shortAndLongBlocksBothStartFull() {
        // No fixed scale: a 15-minute lock and a 10-hour lock each begin as a full ring.
        assertEquals(1f, block(0 + 1, 15 * 60_000L + 1).remainingFraction(1), 0.0001f)
        assertEquals(1f, block(1, 10 * 3_600_000L + 1).remainingFraction(1), 0.0001f)
    }

    @Test
    fun legacyBlockWithoutStartReadsFullUntilExpiry() {
        val legacy = FocusBlock("pkg", "App", lockUntilMillis = 50_000)
        assertEquals(1f, legacy.remainingFraction(10_000), 0.0001f)
        assertEquals(0f, legacy.remainingFraction(50_000), 0.0001f)
    }
}
