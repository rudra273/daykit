package com.daykit.feature.keystore.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Typed entries were added as optional extras (`type`, `fields`, `usedCodes`) rather than a
 * schemaVersion bump: the backup service skips a section whose version doesn't match, so a
 * bump would stop older backups restoring their keys. `value` is still written (the quick-copy
 * value for typed entries), so an older app restoring a newer backup keeps at least that.
 */
class KeyStoreBackupContributor(
    private val repository: KeyStoreRepository,
) : com.daykit.core.backup.BackupContributor {
    override val toolKey: String = "key_store"
    override val schemaVersion: Int = 1

    override suspend fun exportJson(): JSONObject {
        val records = JSONArray()
        repository.exportRecords().forEach { record ->
            val document = record.document
            val json = JSONObject()
                .put("entryId", record.entryId)
                .put("name", record.name)
                .put("label", record.label)
                .put("value", legacyValue(document))
                .put("version", record.version)
                .put("createdAtMillis", record.createdAtMillis)
                .put("updatedAtMillis", record.updatedAtMillis)
            if (!document.isSingleValue) {
                json.put("type", document.type.storageKey)
                    .put("fields", KeyEntryDocument.fieldsToJson(document.fields))
                    .put("usedCodes", JSONArray(document.usedCodes.toList()))
            }
            records.put(json)
        }
        return JSONObject().put("records", records)
    }

    override suspend fun importJson(payload: JSONObject) {
        val records = payload.getJSONArray("records")
        val parsed = buildList {
            for (index in 0 until records.length()) {
                val record = records.getJSONObject(index)
                val document = if (record.has("fields")) {
                    val type = KeyType.fromStorageKey(record.optString("type"))
                    KeyEntryDocument(
                        type = type,
                        fields = KeyEntryDocument.normalize(
                            type,
                            KeyEntryDocument.fieldsFromJson(record.getJSONArray("fields")),
                        ),
                        usedCodes = KeyEntryDocument.stringSet(record.optJSONArray("usedCodes")),
                    )
                } else {
                    KeyEntryDocument.single(record.getString("value"))
                }
                add(
                    KeyStoreBackupRecord(
                        entryId = record.getString("entryId"),
                        name = record.getString("name"),
                        label = record.getString("label"),
                        document = document,
                        version = record.getInt("version"),
                        createdAtMillis = record.getLong("createdAtMillis"),
                        updatedAtMillis = record.getLong("updatedAtMillis"),
                    ),
                )
            }
        }
        repository.importRecords(parsed)
    }

    private fun legacyValue(document: KeyEntryDocument): String {
        val fields = document.fields
        val preferred = document.type.quickCopyKeys
            .mapNotNull { key -> fields.firstOrNull { it.key == key }?.value }
            .firstOrNull { it.isNotBlank() }
        return preferred ?: fields.firstOrNull { it.value.isNotBlank() }?.value.orEmpty()
    }
}
