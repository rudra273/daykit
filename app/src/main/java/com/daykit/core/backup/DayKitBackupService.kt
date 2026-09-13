package com.daykit.core.backup

import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DayKitBackupService(
    private val crypto: BackupCrypto,
    private val contributors: List<BackupContributor>,
) {
    private val operationMutex = Mutex()

    suspend fun exportEncrypted(password: CharArray, includedToolKeys: Set<String> = includedBackupToolKeys(false, false, false)): String =
        try { operationMutex.withLock { exportInternal(password, includedToolKeys) } }
        finally { password.fill('\u0000') }

    private suspend fun exportInternal(password: CharArray, includedToolKeys: Set<String>): String {
        val tools = JSONObject()
        contributors
            .filter { contributor -> contributor.toolKey in includedToolKeys }
            .forEach { contributor ->
            tools.put(
                contributor.toolKey,
                JSONObject()
                    .put("schemaVersion", contributor.schemaVersion)
                    .put("payload", contributor.exportJson()),
            )
        }

        val payload = JSONObject()
            .put("app", "DayKit")
            .put("payloadVersion", PAYLOAD_VERSION)
            .put("exportedAtMillis", System.currentTimeMillis())
            .put("tools", tools)

        val encrypted = crypto.encrypt(payload, password).toString()
        if (encrypted.length > BackupLimits.MAX_ENVELOPE_BYTES) {
            throw BackupSizeException("This backup exceeds the supported 16 MiB file size. Turn off optional utilities to reduce its size.")
        }
        return encrypted
    }

    /**
     * Restores a backup, returning a per-tool [ImportReport] so the caller can tell
     * the user exactly what came back and what did not. Sections are never dropped
     * silently: anything skipped or failed is reported.
     *
     * A payload written by an *older* app version is accepted; only a payload from a
     * newer version than this build understands is rejected outright.
     */
    suspend fun importEncrypted(encryptedBackup: String, password: CharArray): ImportReport =
        try { operationMutex.withLock { importInternal(encryptedBackup, password) } }
        finally { password.fill('\u0000') }

    private suspend fun importInternal(encryptedBackup: String, password: CharArray): ImportReport {
        if (encryptedBackup.length > BackupLimits.MAX_ENVELOPE_BYTES) {
            throw BackupSizeException("This backup exceeds the supported 16 MiB file size.")
        }
        val payload = crypto.decrypt(JSONObject(encryptedBackup), password)
        val payloadVersion = payload.getInt("payloadVersion")
        if (payloadVersion > PAYLOAD_VERSION) {
            throw NewerBackupException(payloadVersion, PAYLOAD_VERSION)
        }

        val tools = payload.getJSONObject("tools")
        val restored = mutableListOf<String>()
        val skipped = mutableListOf<SkippedSection>()

        tools.keys().forEach { key ->
            if (contributors.none { it.toolKey == key }) skipped += SkippedSection(key, "This utility is not supported by this app version")
        }
        contributors.forEach { contributor ->
            if (!tools.has(contributor.toolKey)) return@forEach
            // One malformed or future-shaped section must not abort the whole
            // restore and leave the DB half-populated.
            runCatching {
                val section = tools.getJSONObject(contributor.toolKey)
                val sectionVersion = section.getInt("schemaVersion")
                if (sectionVersion != contributor.schemaVersion) {
                    skipped += SkippedSection(
                        toolKey = contributor.toolKey,
                        reason = "Saved in format v$sectionVersion, this version reads " +
                            "v${contributor.schemaVersion}",
                    )
                } else {
                    contributor.importJson(section.getJSONObject("payload"))
                    restored += contributor.toolKey
                }
            }.onFailure { error ->
                skipped += SkippedSection(
                    toolKey = contributor.toolKey,
                    reason = error.message ?: "Could not be read",
                )
            }
        }

        return ImportReport(restored = restored, skipped = skipped)
    }

    /** Outcome of a restore, so the UI never reports success over missing data. */
    data class ImportReport(
        val restored: List<String>,
        val skipped: List<SkippedSection>,
    ) {
        val isCompleteRestore: Boolean get() = skipped.isEmpty()
    }

    data class SkippedSection(
        val toolKey: String,
        val reason: String,
    )

    /** A backup written by a newer app version than this build can read. */
    class NewerBackupException(
        val backupVersion: Int,
        val supportedVersion: Int,
    ) : Exception(
        "This backup was made by a newer version of DayKit " +
            "(format v$backupVersion, this version reads v$supportedVersion). Update the app to restore it.",
    )

    companion object {
        const val PAYLOAD_VERSION = 1
    }
}
