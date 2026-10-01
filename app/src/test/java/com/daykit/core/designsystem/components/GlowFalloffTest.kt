package com.daykit.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowFalloffTest {
    @Test fun fullAtTheCenterAndZeroAtTheRim() {
        assertEquals(1f, glowFalloff(0f), 0f)
        assertEquals(0.5f, glowFalloff(0.5f), 1e-6f)
        assertEquals(0f, glowFalloff(1f), 0f)
        assertEquals(0f, glowFalloff(2f), 0f)
    }

    @Test fun decreasesMonotonically() {
        val samples = (0..100).map { glowFalloff(it / 100f) }
        samples.zipWithNext().forEach { (a, b) -> assertTrue(b <= a) }
    }

    /** A flat approach to the rim is what removes the visible edge of a linear fade. */
    @Test fun approachesTheRimFlat() {
        val nearRim = glowFalloff(0.99f)
        assertTrue("rim slope too steep: $nearRim", nearRim < 0.001f)
    }
}
