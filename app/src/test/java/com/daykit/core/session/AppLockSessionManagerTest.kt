package com.daykit.core.session

import com.daykit.core.data.AppLockRelock
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionManagerTest {
    private val bank = "com.example.bank"
    private val sms = "com.example.sms"

    @After fun reset() {
        AppLockSessionManager.clearAll()
        AppLockSessionManager.relock = AppLockRelock.OnLeave
    }

    @Test fun onLeaveDropsTheGrantOnSwitch() {
        AppLockSessionManager.relock = AppLockRelock.OnLeave
        AppLockSessionManager.allow(bank)
        AppLockSessionManager.onForegroundChanged(sms)
        assertFalse(AppLockSessionManager.isAllowed(bank))
    }

    @Test fun relaxedModeAllowsAQuickReturn() {
        AppLockSessionManager.relock = AppLockRelock.OneMinute
        AppLockSessionManager.allow(bank)
        val now = System.currentTimeMillis()
        AppLockSessionManager.onForegroundChanged(sms, now)
        AppLockSessionManager.onForegroundChanged(bank, now + 30_000)
        assertTrue(AppLockSessionManager.isAllowed(bank))
    }

    @Test fun relaxedModeRelocksAfterTheAwayTime() {
        AppLockSessionManager.relock = AppLockRelock.OneMinute
        AppLockSessionManager.allow(bank)
        val now = System.currentTimeMillis()
        AppLockSessionManager.onForegroundChanged(sms, now - 120_000)
        // Returning must not clear the stamp before expiry is checked.
        AppLockSessionManager.onForegroundChanged(bank, now)
        assertFalse(AppLockSessionManager.isAllowed(bank))
    }

    @Test fun screenOffModeKeepsTheGrantAcrossSwitches() {
        AppLockSessionManager.relock = AppLockRelock.ScreenOff
        AppLockSessionManager.allow(bank)
        val now = System.currentTimeMillis()
        AppLockSessionManager.onForegroundChanged(sms, now - 3_600_000)
        AppLockSessionManager.onForegroundChanged(bank, now)
        assertTrue(AppLockSessionManager.isAllowed(bank))
    }
}
