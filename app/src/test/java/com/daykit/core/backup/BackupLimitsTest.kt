package com.daykit.core.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BackupLimitsTest {
    @Test fun base64PreflightAccountsForPaddingInEveryFile() {
        assertEquals(0L, BackupLimits.decodedBase64Size(""))
        assertEquals(1L, BackupLimits.decodedBase64Size("AA=="))
        assertEquals(2L, BackupLimits.decodedBase64Size("AAA="))
        assertEquals(3L, BackupLimits.decodedBase64Size("AAAA"))
        listOf("A", "====", "AA=A", "AA\n=").forEach {
            assertTrue(runCatching { BackupLimits.decodedBase64Size(it) }.isFailure)
        }
    }

    @Test fun totalSizePreflightRejectsOverflowAndOversizedCollections() {
        val limit = BackupLimits.MAX_VAULT_BYTES.toLong()
        BackupLimits.checkVaultSizes(listOf(limit / 2, limit / 2))
        listOf(listOf(limit, 1), listOf(Long.MAX_VALUE, 1), listOf(-1L)).forEach {
            assertTrue(runCatching { BackupLimits.checkVaultSizes(it) }.exceptionOrNull() is BackupSizeException)
        }
    }

    @Test fun dishonestStreamIsStoppedAtLimitPlusOneByte() {
        var bytesRead = 0
        val endless = object : InputStream() {
            override fun read(): Int { bytesRead++; return 1 }
        }
        assertTrue(runCatching { BackupLimits.readBounded(endless, 20, "Too large") }.exceptionOrNull() is BackupSizeException)
        assertEquals(21, bytesRead)
    }

    @Test fun exactLimitAndEmptyFilesAreAccepted() {
        val bytes = ByteArray(20) { it.toByte() }
        assertArrayEquals(bytes, BackupLimits.readBounded(ByteArrayInputStream(bytes), 20, "Too large"))
        assertArrayEquals(ByteArray(0), BackupLimits.readBounded(ByteArrayInputStream(ByteArray(0)), 0, "Too large"))
    }
}
