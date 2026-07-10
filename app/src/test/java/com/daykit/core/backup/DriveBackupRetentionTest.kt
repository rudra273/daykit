package com.daykit.core.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveBackupRetentionTest {
    @Test
    fun backupsToDelete_keepsLatestThreeBackups() {
        val backups = listOf(
            backup("oldest", 1_000L),
            backup("newest", 5_000L),
            backup("middle", 3_000L),
            backup("second_newest", 4_000L),
            backup("second_oldest", 2_000L),
        )

        val delete = DriveBackupRetention.backupsToDelete(backups, retainCount = 3)

        assertEquals(listOf("second_oldest", "oldest"), delete.map { it.id })
    }

    @Test
    fun backupsToDelete_deletesAllWhenRetainCountIsZero() {
        val backups = listOf(backup("one", 1_000L), backup("two", 2_000L))

        val delete = DriveBackupRetention.backupsToDelete(backups, retainCount = 0)

        assertEquals(listOf("one", "two"), delete.map { it.id })
    }

    @Test
    fun backupsToDelete_protectsNewestManualBackupOutsideRetainWindow() {
        // The one manual backup is the oldest, so it would normally be pruned.
        // Automatic backups can't contain the sensitive tools, so it must survive.
        val backups = listOf(
            backup("auto_newest", 5_000L, DriveBackupSource.Automatic),
            backup("auto_2", 4_000L, DriveBackupSource.Automatic),
            backup("auto_3", 3_000L, DriveBackupSource.Automatic),
            backup("auto_4", 2_000L, DriveBackupSource.Automatic),
            backup("manual_oldest", 1_000L, DriveBackupSource.Manual),
        )

        val delete = DriveBackupRetention.backupsToDelete(backups, retainCount = 3)

        // auto_4 falls outside the window and is deleted; the manual one is kept.
        assertEquals(listOf("auto_4"), delete.map { it.id })
    }

    @Test
    fun backupsToDelete_onlyMostRecentManualIsProtected() {
        // Two manual backups both outside the window: only the newest is spared.
        val backups = listOf(
            backup("auto_newest", 5_000L, DriveBackupSource.Automatic),
            backup("auto_2", 4_000L, DriveBackupSource.Automatic),
            backup("auto_3", 3_000L, DriveBackupSource.Automatic),
            backup("manual_newer", 2_000L, DriveBackupSource.Manual),
            backup("manual_older", 1_000L, DriveBackupSource.Manual),
        )

        val delete = DriveBackupRetention.backupsToDelete(backups, retainCount = 3)

        assertEquals(listOf("manual_older"), delete.map { it.id })
    }

    private fun backup(
        id: String,
        createdAtMillis: Long,
        source: DriveBackupSource = DriveBackupSource.Manual,
    ): DriveBackupFile {
        return DriveBackupFile(
            id = id,
            name = "$id.daykit",
            createdAtMillis = createdAtMillis,
            payloadVersion = 1,
            source = source,
            sizeBytes = null,
        )
    }
}
