package com.daykit.feature.focus.data

import com.daykit.feature.focus.service.SHORT_VIDEO_CONSENT
import com.daykit.feature.focus.service.ShortVideoTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryShortVideoBackend : ShortVideoPrefsBackend {
    val strings = mutableMapOf<String, String>()
    val ints = mutableMapOf<String, Int>()
    val longs = mutableMapOf<String, Long>()
    val booleans = mutableMapOf<String, Boolean>()

    override fun getString(key: String): String? = strings[key]
    override fun putString(key: String, value: String) {
        strings[key] = value
    }

    override fun getInt(key: String, default: Int): Int = ints[key] ?: default
    override fun putInt(key: String, value: Int) {
        ints[key] = value
    }

    override fun getLong(key: String, default: Long): Long = longs[key] ?: default
    override fun putLong(key: String, value: Long) {
        longs[key] = value
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default
    override fun putBoolean(key: String, value: Boolean) {
        booleans[key] = value
    }
}

class ShortVideoStoreTest {

    @Test
    fun legacyBooleanTrueMigratesToBlockAll() {
        val backend = InMemoryShortVideoBackend()
        backend.putBoolean(SHORT_VIDEO_CONSENT, true)
        backend.putBoolean(ShortVideoTarget.YouTube.packageName, true)
        val store = ShortVideoStore(backend)

        val youtubeRule = store.getRule(ShortVideoTarget.YouTube, today = "2026-11-23")
        val instagramRule = store.getRule(ShortVideoTarget.Instagram, today = "2026-11-23")

        assertEquals(ShortVideoMode.BlockAll, youtubeRule.mode)
        assertTrue(youtubeRule.shouldRedirectNow)
        assertEquals(ShortVideoMode.Off, instagramRule.mode)
        assertFalse(instagramRule.shouldRedirectNow)
        assertEquals(listOf(ShortVideoTarget.YouTube), store.activeTargets("2026-11-23"))
    }

    @Test
    fun dailyLimitAccumulatesUsageAndTriggersRedirectAtThreshold() {
        val backend = InMemoryShortVideoBackend()
        val store = ShortVideoStore(backend)
        store.hasConsent = true
        store.saveRule(ShortVideoTarget.Instagram, ShortVideoMode.DailyLimit, dailyLimitMinutes = 10)

        var rule = store.getRule(ShortVideoTarget.Instagram, today = "2026-11-23")
        assertEquals(ShortVideoMode.DailyLimit, rule.mode)
        assertEquals(10, rule.dailyLimitMinutes)
        assertFalse(rule.isLimitReached)
        assertFalse(rule.shouldRedirectNow)

        // Add 4 minutes
        rule = store.addUsageMillis(ShortVideoTarget.Instagram, 4 * 60_000L, today = "2026-11-23")
        assertEquals(4 * 60_000L, rule.usedTodayMillis)
        assertEquals(6 * 60_000L, rule.remainingMillis)
        assertFalse(rule.isLimitReached)

        // Add remaining 6 minutes -> hits 10m limit
        rule = store.addUsageMillis(ShortVideoTarget.Instagram, 6 * 60_000L, today = "2026-11-23")
        assertEquals(10 * 60_000L, rule.usedTodayMillis)
        assertEquals(0L, rule.remainingMillis)
        assertTrue(rule.isLimitReached)
        assertTrue(rule.shouldRedirectNow)
    }

    @Test
    fun dateRolloverResetsUsageAndBlocksAtMidnight() {
        val backend = InMemoryShortVideoBackend()
        val store = ShortVideoStore(backend)
        store.saveRule(ShortVideoTarget.YouTube, ShortVideoMode.DailyLimit, dailyLimitMinutes = 5)
        store.addUsageMillis(ShortVideoTarget.YouTube, 5 * 60_000L, today = "2026-11-23")
        store.recordBlock(ShortVideoTarget.YouTube, today = "2026-11-23")
        store.recordBlock(ShortVideoTarget.YouTube, today = "2026-11-23")

        val day1 = store.getRule(ShortVideoTarget.YouTube, today = "2026-11-23")
        assertTrue(day1.isLimitReached)
        assertEquals(2, day1.blocksToday)

        // Next calendar day: counters reset automatically while mode & limit persist
        val day2 = store.getRule(ShortVideoTarget.YouTube, today = "2026-11-24")
        assertEquals(ShortVideoMode.DailyLimit, day2.mode)
        assertEquals(5, day2.dailyLimitMinutes)
        assertEquals(0L, day2.usedTodayMillis)
        assertEquals(0, day2.blocksToday)
        assertFalse(day2.isLimitReached)
    }
}
