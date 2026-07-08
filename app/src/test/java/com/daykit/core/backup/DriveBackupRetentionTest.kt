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

    private fun backup(id: String, createdAtMillis: Long): DriveBackupFile {
        return DriveBackupFile(
            id = id,
            name = "$id.daykit",
            createdAtMillis = createdAtMillis,
            payloadVersion = 1,
            source = DriveBackupSource.Manual,
            sizeBytes = null,
        )
    }
}
