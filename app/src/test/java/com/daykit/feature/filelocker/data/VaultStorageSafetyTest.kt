package com.daykit.feature.filelocker.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.UUID

class VaultStorageSafetyTest {
    @Test fun malformedAndTraversalIdsAreRejected() {
        listOf("../vault/${UUID.randomUUID()}", "../../files/secret", "/tmp/file", "1-1-1-1-1", "", "a\\b").forEach {
            assertTrue(runCatching { VaultStorageSafety.validateId(it) }.isFailure)
        }
        VaultStorageSafety.validateId(UUID.randomUUID().toString())
    }

    @Test fun newStorageNeverOverwritesExistingFile() {
        val dir = Files.createTempDirectory("vault-safety").toFile()
        try {
            val first = VaultStorageSafety.createBlob(dir)
            first.writeText("Original")
            val second = VaultStorageSafety.createBlob(dir)
            assertNotEquals(first, second)
            assertEquals("Original", first.readText())
            assertEquals(dir.canonicalFile, second.canonicalFile.parentFile)
        } finally { dir.deleteRecursively() }
    }

    @Test fun sourceCleanupFailureNeverDeletesPublishedCopy() = runBlocking {
        var destinationExists = false
        var cleanupErrorReported = false
        val result = restoreVaultCopy(
            publish = { destinationExists = true },
            cleanupSource = { throw IllegalStateException("Database unavailable") },
            discardUnpublished = { destinationExists = false },
            cleanupFailed = { cleanupErrorReported = true },
        )
        assertTrue(result)
        assertTrue(destinationExists)
        assertTrue(cleanupErrorReported)
    }

    @Test fun publishFailureKeepsSourceAndDiscardsPartialDestination() = runBlocking {
        var sourceExists = true
        var discarded = false
        val result = restoreVaultCopy(
            publish = { throw IllegalStateException("Cannot publish") },
            cleanupSource = { sourceExists = false },
            discardUnpublished = { discarded = true },
            cleanupFailed = { fail("Source cleanup must not run") },
        )
        assertFalse(result)
        assertTrue(sourceExists)
        assertTrue(discarded)
    }
}
