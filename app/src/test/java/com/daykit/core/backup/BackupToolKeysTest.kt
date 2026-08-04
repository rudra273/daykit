package com.daykit.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupToolKeysTest {
    @Test
    fun keyStoreAndNotesAreAlwaysIncluded() {
        // Even with every optional toggle off, the two tools the user cannot
        // reconstruct are present.
        val keys = includedBackupToolKeys(
            includeExpenses = false,
            includeHabits = false,
            includeVault = false,
        )

        assertEquals(setOf(BackupToolKeys.KEY_STORE, BackupToolKeys.NOTES), keys)
    }

    @Test
    fun vaultIsOptInIndependentOfKeyStoreAndNotes() {
        val without = includedBackupToolKeys(
            includeExpenses = false,
            includeHabits = false,
            includeVault = false,
        )
        val with = includedBackupToolKeys(
            includeExpenses = false,
            includeHabits = false,
            includeVault = true,
        )

        assertFalse(BackupToolKeys.VAULT in without)
        assertTrue(BackupToolKeys.VAULT in with)
        // Turning the vault toggle on must not change anything else.
        assertEquals(without + BackupToolKeys.VAULT, with)
    }

    @Test
    fun allThreeTogglesOnIncludesEverything() {
        val keys = includedBackupToolKeys(
            includeExpenses = true,
            includeHabits = true,
            includeVault = true,
        )

        assertEquals(
            setOf(
                BackupToolKeys.KEY_STORE,
                BackupToolKeys.NOTES,
                BackupToolKeys.VAULT,
                BackupToolKeys.EXPENSES,
                BackupToolKeys.HABITS,
            ),
            keys,
        )
    }

    @Test
    fun aBackupIsNeverEmpty() {
        // With no background worker there is no longer a path that produces an empty
        // set, so no caller needs an "is there anything to upload?" guard.
        assertTrue(
            includedBackupToolKeys(
                includeExpenses = false,
                includeHabits = false,
                includeVault = false,
            ).isNotEmpty(),
        )
    }

    @Test
    fun onlyRecurringSchedulesHaveAnInterval() {
        assertEquals(24L * 60 * 60 * 1000, DriveBackupSchedule.Daily.intervalMillis())
        assertEquals(7L * 24 * 60 * 60 * 1000, DriveBackupSchedule.Weekly.intervalMillis())
        // Manual and Off must have no interval, or DriveBackupRunner would treat
        // them as due and back up against the user's choice.
        assertEquals(null, DriveBackupSchedule.Manual.intervalMillis())
        assertEquals(null, DriveBackupSchedule.Off.intervalMillis())
    }

    @Test
    fun unknownSourceFallsBackToManual() {
        assertEquals(DriveBackupSource.Manual, DriveBackupSource.fromValue(null))
        assertEquals(DriveBackupSource.Manual, DriveBackupSource.fromValue("nonsense"))
        // Backups uploaded by the old background worker carried this value; it must
        // still parse to something sane rather than crash the backup list.
        assertEquals(DriveBackupSource.Manual, DriveBackupSource.fromValue("automatic_partial"))
        assertEquals(DriveBackupSource.Automatic, DriveBackupSource.fromValue("automatic"))
    }
}
