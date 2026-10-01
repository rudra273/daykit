package com.daykit.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test fun twelveHour() {
        assertEquals("12:00 AM", TimeFormat.format(0, 0, use24Hour = false))
        assertEquals("12:30 PM", TimeFormat.format(12, 30, use24Hour = false))
        assertEquals("11:59 PM", TimeFormat.format(23, 59, use24Hour = false))
    }

    @Test fun twentyFourHour() {
        assertEquals("00:00", TimeFormat.format(0, 0, use24Hour = true))
        assertEquals("09:05", TimeFormat.format(9, 5, use24Hour = true))
        assertEquals("23:59", TimeFormat.format(23, 59, use24Hour = true))
    }
}
