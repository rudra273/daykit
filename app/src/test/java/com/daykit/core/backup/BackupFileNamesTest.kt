package com.daykit.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class BackupFileNamesTest {
    @Test
    fun backupName_marksManualBackupsWithM() {
        val name = BackupFileNames.backupName(
            payloadVersion = 1,
            createdAt = Instant.parse("2026-05-05T14:30:12Z"),
            source = DriveBackupSource.Manual,
        )

        assertEquals("daykit-backup-v1-20260505T143012Z-m.daykit", name)
    }

    @Test
    fun backupName_marksAutomaticBackupsWithA() {
        val name = BackupFileNames.backupName(
            payloadVersion = 1,
            createdAt = Instant.parse("2026-05-05T14:30:12Z"),
            source = DriveBackupSource.Automatic,
        )

        assertEquals("daykit-backup-v1-20260505T143012Z-a.daykit", name)
    }

    @Test
    fun parse_readsSourceMarker() {
        assertEquals(
            DriveBackupSource.Manual,
            BackupFileNames.parse("daykit-backup-v1-20260505T143012Z-m.daykit")?.source,
        )
        assertEquals(
            DriveBackupSource.Automatic,
            BackupFileNames.parse("daykit-backup-v1-20260505T143012Z-a.daykit")?.source,
        )
    }

    @Test
    fun parse_readsStableUtcBackupNames() {
        val parsed = BackupFileNames.parse("daykit-backup-v2-20260505T143012Z-a.daykit")

        assertEquals(2, parsed?.version)
        assertEquals(Instant.parse("2026-05-05T14:30:12Z").toEpochMilli(), parsed?.exportedAtMillis)
        assertEquals("05 May, 8:00 PM", parsed?.exportedAt)
    }

    /** Names written before the marker existed are still sitting in users' Drive. */
    @Test
    fun parse_stillReadsPreMarkerNames() {
        val parsed = BackupFileNames.parse("daykit-backup-v2-20260505T143012Z.daykit")

        assertEquals(2, parsed?.version)
        assertEquals(Instant.parse("2026-05-05T14:30:12Z").toEpochMilli(), parsed?.exportedAtMillis)
        assertNull(parsed?.source)
    }

    @Test
    fun parse_ignoresUnknownNames() {
        assertNull(BackupFileNames.parse("notes.json"))
        assertNull(BackupFileNames.parse("daykit-backup-v1-20260505T143012Z-x.daykit"))
    }
}
