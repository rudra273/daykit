package com.daykit.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickActionTest {
    @Test fun notificationActionsResolveOnlyToTheirIntendedScreens() {
        assertEquals(Routes.TOOL_REMINDERS, QuickAction.fromIntentAction("com.daykit.action.REMINDERS")?.route)
        assertEquals(Routes.TOOL_REMINDERS, QuickAction.fromIntentAction("com.daykit.action.ADD_REMINDER")?.route)
        assertNull(QuickAction.fromIntentAction("com.daykit.action.HABITS"))
        assertNull(QuickAction.fromIntentAction(null))
        assertNull(QuickAction.fromIntentAction("tool/keystore"))
        assertNull(QuickAction.fromIntentAction("android.intent.action.MAIN"))
    }

    @Test fun repeatedActionTapsAreDistinctRequests() {
        assertNotEquals(QuickActionRequest(QuickAction.AddReminder), QuickActionRequest(QuickAction.AddReminder))
    }
}
