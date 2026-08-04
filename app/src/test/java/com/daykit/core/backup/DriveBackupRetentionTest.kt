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
    fun backupsToDelete_keepsEverythingWhenUnderRetainCount() {
        val backups = listOf(backup("one", 1_000L), backup("two", 2_000L))

        val delete = DriveBackupRetention.backupsToDelete(backups, retainCount = 3)

        assertEquals(emptyList<String>(), delete.map { it.id })
    }

    @Test
    fun backupsToDelete_treatsManualAndAutomaticAlike() {
        // Both kinds run with the PIN-derived key available, so both contain the same
        // tools and neither is protected from rotation — purely newest-N by date.
        val backups = listOf(
            backup("auto_newest", 5_000L, DriveBackupSource.Automatic),
            backup("auto_2", 4_000L, DriveBackupSource.Automatic),
            backup("auto_3", 3_000L, DriveBackupSource.Automatic),
            backup("manual_older", 2_000L, DriveBackupSource.Manual),
            backup("manual_oldest", 1_000L, DriveBackupSource.Manual),
        )

        val delete = DriveBackupRetention.backupsToDelete(backups, retainCount = 3)

        assertEquals(listOf("manual_older", "manual_oldest"), delete.map { it.id })
    }

    @Test
    fun backupsToDelete_breaksTiesOnNameSoOrderIsDeterministic() {
        // Two backups with the same timestamp must not rotate arbitrarily between runs.
        val backups = listOf(
            backup("b", 1_000L),
            backup("a", 1_000L),
            backup("c", 1_000L),
        )

        val delete = DriveBackupRetention.backupsToDelete(backups, retainCount = 2)

        assertEquals(listOf("a"), delete.map { it.id })
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
