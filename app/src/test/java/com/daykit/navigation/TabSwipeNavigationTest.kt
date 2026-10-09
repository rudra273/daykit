package com.daykit.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabSwipeNavigationTest {
    @Test fun swipesMoveInTheBottomNavigationOrder() {
        assertEquals(Routes.TODAY, adjacentTopLevelRoute(Routes.HOME, forward = true))
        assertEquals(Routes.SETTINGS, adjacentTopLevelRoute(Routes.TODAY, forward = true))
        assertEquals(Routes.TODAY, adjacentTopLevelRoute(Routes.SETTINGS, forward = false))
        assertEquals(Routes.HOME, adjacentTopLevelRoute(Routes.TODAY, forward = false))
    }

    @Test fun swipesStopAtTheEnds() {
        assertNull(adjacentTopLevelRoute(Routes.HOME, forward = false))
        assertNull(adjacentTopLevelRoute(Routes.SETTINGS, forward = true))
    }

    @Test fun swipesCannotLeaveAToolOrSettingsSubpage() {
        for (route in listOf(null, Routes.TOOL_KEYSTORE, Routes.SETTINGS_SECURITY)) {
            assertNull(adjacentTopLevelRoute(route, forward = true))
            assertNull(adjacentTopLevelRoute(route, forward = false))
        }
    }
}
