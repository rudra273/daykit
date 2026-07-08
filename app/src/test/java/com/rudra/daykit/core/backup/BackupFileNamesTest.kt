package com.rudra.daykit.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class BackupFileNamesTest {
    @Test
    fun backupName_usesStableUtcFormat() {
        val name = BackupFileNames.backupName(
            payloadVersion = 1,
            createdAt = Instant.parse("2026-05-05T14:30:12Z"),
        )

        assertEquals("daykit-backup-v1-20260505T143012Z.daykit", name)
    }

    @Test
    fun parse_readsStableUtcBackupNames() {
        val parsed = BackupFileNames.parse("daykit-backup-v2-20260505T143012Z.daykit")

        assertEquals(2, parsed?.version)
        assertEquals(Instant.parse("2026-05-05T14:30:12Z").toEpochMilli(), parsed?.exportedAtMillis)
        assertEquals("05 May, 8:00 PM", parsed?.exportedAt)
    }

    @Test
    fun parse_ignoresUnknownNames() {
        assertNull(BackupFileNames.parse("notes.json"))
    }
}
