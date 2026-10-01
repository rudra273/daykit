package com.daykit.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class RecentlyDeletedTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val deletedAt = 1_759_000_000_000L

    @Test fun purgeCutoffIsThirtyDaysBack() {
        assertEquals(deletedAt, RecentlyDeleted.purgeCutoff(deletedAt + 30 * day))
    }

    @Test fun daysLeftCountsDownAndRoundsUp() {
        assertEquals(30, RecentlyDeleted.daysLeft(deletedAt, deletedAt))
        assertEquals(30, RecentlyDeleted.daysLeft(deletedAt, deletedAt + 1))
        assertEquals(29, RecentlyDeleted.daysLeft(deletedAt, deletedAt + day))
        assertEquals(1, RecentlyDeleted.daysLeft(deletedAt, deletedAt + 30 * day - 1))
        assertEquals(0, RecentlyDeleted.daysLeft(deletedAt, deletedAt + 30 * day))
        assertEquals(0, RecentlyDeleted.daysLeft(deletedAt, deletedAt + 400 * day))
    }

    @Test fun labels() {
        assertEquals("30 days left", RecentlyDeleted.daysLeftLabel(deletedAt, deletedAt))
        assertEquals("1 day left", RecentlyDeleted.daysLeftLabel(deletedAt, deletedAt + 29 * day + 1))
        assertEquals("Deleted today", RecentlyDeleted.daysLeftLabel(deletedAt, deletedAt + 31 * day))
    }
}
