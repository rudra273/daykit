package com.daykit.feature.filelocker.data

import java.io.File
import java.util.UUID

internal object VaultStorageSafety {
    fun validateId(id: String) {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
            "Invalid vault file ID in backup"
        }
    }

    /** Storage names are generated locally, never derived from backup-controlled paths. */
    fun createBlob(directory: File): File {
        val file = File(directory, "${UUID.randomUUID()}.bin")
        check(file.canonicalFile.parentFile == directory.canonicalFile) { "Invalid vault path" }
        check(file.createNewFile()) { "Could not create a new vault file" }
        return file
    }
}

/** The published destination is the commit point. Source cleanup can never roll it back. */
internal suspend fun restoreVaultCopy(
    publish: suspend () -> Unit,
    cleanupSource: suspend () -> Unit,
    discardUnpublished: suspend () -> Unit,
    cleanupFailed: (Exception) -> Unit,
): Boolean {
    try {
        publish()
    } catch (error: Exception) {
        runCatching { discardUnpublished() }
        if (error is kotlinx.coroutines.CancellationException) throw error
        return false
    }
    try {
        cleanupSource()
    } catch (error: Exception) {
        cleanupFailed(error)
        if (error is kotlinx.coroutines.CancellationException) throw error
    }
    return true
}
