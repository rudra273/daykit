package com.daykit.feature.notes.data

import com.daykit.core.backup.BackupContributor
import org.json.JSONArray
import org.json.JSONObject

class SecureNoteBackupContributor(
    private val repository: SecureNoteRepository,
) : BackupContributor {
    override val toolKey: String = "secure_notes"
    override val schemaVersion: Int = 1

    override suspend fun exportJson(): JSONObject {
        val records = JSONArray()
        repository.exportRecords().forEach { record ->
            records.put(
                JSONObject()
                    .put("noteId", record.noteId)
                    .put("title", record.title)
                    .put("content", record.content)
                    .put("labels", record.labels)
                    .put("version", record.version)
                    .put("createdAtMillis", record.createdAtMillis)
                    .put("updatedAtMillis", record.updatedAtMillis),
            )
        }
        return JSONObject().put("records", records)
    }

    override suspend fun importJson(payload: JSONObject) {
        val records = payload.getJSONArray("records")
        val parsed = buildList {
            for (index in 0 until records.length()) {
                val record = records.getJSONObject(index)
                add(
                    SecureNoteBackupRecord(
                        noteId = record.getString("noteId"),
                        title = record.getString("title"),
                        content = record.getString("content"),
                        labels = record.getString("labels"),
                        version = record.getInt("version"),
                        createdAtMillis = record.getLong("createdAtMillis"),
                        updatedAtMillis = record.getLong("updatedAtMillis"),
                    ),
                )
            }
        }
        repository.importRecords(parsed)
    }
}
