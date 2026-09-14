package com.daykit.feature.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetAmountTest {
    @Test fun amountsUseExactMinorUnits() {
        assertEquals(29L, "0.29".toWidgetMinorOrNull())
        assertEquals(99999999999L, "999999999.99".toWidgetMinorOrNull())
        assertEquals(50L, ".5".toWidgetMinorOrNull())
    }
    @Test fun invalidAndOverflowAmountsAreRejected() {
        listOf("", ".", "NaN", "Infinity", "0.001", "999999999999999999999").forEach {
            assertNull(it.toWidgetMinorOrNull())
        }
    }
    @Test fun pastedDecimalsCannotBypassWholePartLimit() {
        assertEquals("123456789.12", "123456789012.123".cleanWidgetAmountInput())
        assertEquals("1.23", "1.2.3".cleanWidgetAmountInput())
    }
}
