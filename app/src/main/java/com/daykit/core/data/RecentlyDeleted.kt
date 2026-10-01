package com.daykit.core.data

import java.util.concurrent.TimeUnit

/**
 * Retention rules for the "Recently deleted" bin shared by Secure Notes, Key
 * Store and the File Vault.
 *
 * Deleting an item only stamps `deletedAtMillis`; the row (and the vault blob)
 * stays encrypted exactly as before. Items are purged for good once they are
 * older than [RETENTION_DAYS], on the next app start. Trashed items are left out
 * of backups.
 */
object RecentlyDeleted {
    const val RETENTION_DAYS = 30
    private val RETENTION_MILLIS = TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())

    /** Items deleted before this instant are due to be purged. */
    fun purgeCutoff(nowMillis: Long): Long = nowMillis - RETENTION_MILLIS

    /** Whole days until [deletedAtMillis] is purged, never negative; rounds up so "0 days" only shows at the very end. */
    fun daysLeft(deletedAtMillis: Long, nowMillis: Long): Int {
        val remaining = deletedAtMillis + RETENTION_MILLIS - nowMillis
        if (remaining <= 0) return 0
        return ((remaining + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)).toInt()
    }

    fun daysLeftLabel(deletedAtMillis: Long, nowMillis: Long): String =
        when (val days = daysLeft(deletedAtMillis, nowMillis)) {
            0 -> "Deleted today"
            1 -> "1 day left"
            else -> "$days days left"
        }
}
