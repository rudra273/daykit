package com.daykit.feature.filelocker.data

import android.util.Base64
import com.daykit.core.backup.BackupContributor
import org.json.JSONArray
import org.json.JSONObject

/**
 * Includes vault files in an app backup — but ONLY when the user has opted in.
 *
 * The gating is external: this contributor's [toolKey] is added to the backup's
 * included-tool set only when the "back up vault files" toggle is on (default
 * off). If the key is absent, [DayKitBackupService] never calls [exportJson],
 * so no vault bytes can leak into a backup the user did not ask for.
 *
 * Vault files can be large; when opted in, each file's decrypted bytes are
 * base64-encoded into the payload and then sealed by the backup's own
 * AES-256-GCM envelope alongside all other data.
 */
class VaultBackupContributor(
    private val repository: VaultFileRepository,
) : BackupContributor {
    override val toolKey: String = TOOL_KEY
    override val schemaVersion: Int = 1

    override suspend fun exportJson(): JSONObject {
        val files = JSONArray()
        repository.exportForBackup().forEach { record ->
            files.put(
                JSONObject()
                    .put("fileId", record.fileId)
                    .put("name", record.name)
                    .put("mimeType", record.mimeType)
                    .put("createdAtMillis", record.createdAtMillis)
                    .put("bytes", Base64.encodeToString(record.plaintext, Base64.NO_WRAP)),
            )
        }
        return JSONObject().put("files", files)
    }

    override suspend fun importJson(payload: JSONObject) {
        val files = payload.optJSONArray("files") ?: JSONArray()
        val records = buildList {
            for (index in 0 until files.length()) {
                val file = files.getJSONObject(index)
                add(
                    VaultBackupRecord(
                        fileId = file.getString("fileId"),
                        name = file.getString("name"),
                        mimeType = file.getString("mimeType"),
                        createdAtMillis = file.getLong("createdAtMillis"),
                        plaintext = Base64.decode(file.getString("bytes"), Base64.NO_WRAP),
                    ),
                )
            }
        }
        repository.importFromBackup(records)
    }

    companion object {
        const val TOOL_KEY = "vault_files"
    }
}
