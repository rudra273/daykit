package com.daykit.feature.keystore.data

import org.json.JSONArray
import org.json.JSONObject

class KeyStoreBackupContributor(
    private val repository: KeyStoreRepository,
) : com.daykit.core.backup.BackupContributor {
    override val toolKey: String = "key_store"
    override val schemaVersion: Int = 1

    override suspend fun exportJson(): JSONObject {
        val records = JSONArray()
        repository.exportRecords().forEach { record ->
            records.put(
                JSONObject()
                    .put("entryId", record.entryId)
                    .put("name", record.name)
                    .put("label", record.label)
                    .put("value", record.value)
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
                    KeyStoreBackupRecord(
                        entryId = record.getString("entryId"),
                        name = record.getString("name"),
                        label = record.getString("label"),
                        value = record.getString("value"),
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
