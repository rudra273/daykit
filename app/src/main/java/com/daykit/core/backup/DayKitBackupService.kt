package com.daykit.core.backup

import org.json.JSONObject

class DayKitBackupService(
    private val crypto: BackupCrypto,
    private val contributors: List<BackupContributor>,
) {
    suspend fun exportEncrypted(
        password: CharArray,
        includedToolKeys: Set<String>? = null,
    ): String {
        val tools = JSONObject()
        contributors
            .filter { contributor -> includedToolKeys == null || contributor.toolKey in includedToolKeys }
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

        return crypto.encrypt(payload, password).toString()
    }

    /**
     * Restores a backup, returning a per-tool [ImportReport] so the caller can tell
     * the user exactly what came back and what did not. Sections are never dropped
     * silently: anything skipped or failed is reported.
     *
     * A payload written by an *older* app version is accepted; only a payload from a
     * newer version than this build understands is rejected outright.
     */
    suspend fun importEncrypted(encryptedBackup: String, password: CharArray): ImportReport {
        val payload = crypto.decrypt(JSONObject(encryptedBackup), password)
        val payloadVersion = payload.getInt("payloadVersion")
        if (payloadVersion > PAYLOAD_VERSION) {
            throw NewerBackupException(payloadVersion, PAYLOAD_VERSION)
        }

        val tools = payload.getJSONObject("tools")
        val restored = mutableListOf<String>()
        val skipped = mutableListOf<SkippedSection>()

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
