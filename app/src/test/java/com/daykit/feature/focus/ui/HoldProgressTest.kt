package com.daykit.feature.focus.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HoldProgressTest {
    @Test fun fillsLinearlyOverTheHoldTime() {
        assertEquals(0f, holdProgress(0f, 0, 2_000), 0f)
        assertEquals(0.5f, holdProgress(0f, 1_000, 2_000), 1e-6f)
        assertEquals(1f, holdProgress(0f, 2_000, 2_000), 0f)
    }

    @Test fun resumesFromAPartialFillAndNeverOvershoots() {
        assertEquals(0.75f, holdProgress(0.5f, 500, 2_000), 1e-6f)
        assertEquals(1f, holdProgress(0.5f, 60_000, 2_000), 0f)
    }

    /** A tap must never commit: one frame (~16ms) is nowhere near the hold time. */
    @Test fun aSingleFrameIsNotAHold() {
        assert(holdProgress(0f, 16, 2_000) < 0.01f)
    }
}
