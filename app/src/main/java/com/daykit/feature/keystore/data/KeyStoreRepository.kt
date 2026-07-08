package com.daykit.feature.keystore.data

import com.daykit.core.security.CipherPayload
import com.daykit.core.security.SensitiveValueCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class KeyStoreRepository(
    private val dao: KeyStoreEntryDao,
    private val cipher: SensitiveValueCipher,
) {
    fun observeEntries(): Flow<List<KeyStoreEntry>> {
        return dao.observeAll().map { entities ->
            entities.mapNotNull { entity -> entity.toDomainOrNull() }
        }
    }

    suspend fun addEntry(name: String, label: String, value: String) {
        val cleanName = name.trim()
        val cleanLabel = label.trim()
        require(cleanName.isNotBlank()) { "Key name cannot be empty" }
        require(value.isNotBlank()) { "Key value cannot be empty" }

        val now = System.currentTimeMillis()
        dao.upsert(
            entity = encryptedEntity(
                entryId = UUID.randomUUID().toString(),
                id = 0,
                name = cleanName,
                label = cleanLabel,
                value = value,
                version = INITIAL_VERSION,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    suspend fun updateEntry(entryId: String, name: String, label: String, value: String) {
        val existing = dao.getByEntryId(entryId) ?: return
        val cleanName = name.trim()
        val cleanLabel = label.trim()
        require(cleanName.isNotBlank()) { "Key name cannot be empty" }
        require(value.isNotBlank()) { "Key value cannot be empty" }

        dao.upsert(
            encryptedEntity(
                entryId = existing.entryId,
                id = existing.id,
                name = cleanName,
                label = cleanLabel,
                value = value,
                version = existing.version + 1,
                createdAtMillis = existing.createdAtMillis,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteEntry(entryId: String) {
        dao.deleteByEntryId(entryId)
    }

    suspend fun exportRecords(): List<KeyStoreBackupRecord> {
        return dao.getAll().mapNotNull { entity -> entity.toDomainOrNull() }
            .map { entry ->
                KeyStoreBackupRecord(
                    entryId = entry.entryId,
                    name = entry.name,
                    label = entry.label,
                    value = entry.value,
                    version = entry.version,
                    createdAtMillis = entry.createdAtMillis,
                    updatedAtMillis = entry.updatedAtMillis,
                )
            }
    }

    suspend fun importRecords(records: List<KeyStoreBackupRecord>) {
        records.forEach { record ->
            val existing = dao.getByEntryId(record.entryId)
            if (existing == null || record.updatedAtMillis > existing.updatedAtMillis) {
                dao.upsert(
                    encryptedEntity(
                        id = existing?.id ?: 0,
                        entryId = record.entryId,
                        name = record.name,
                        label = record.label,
                        value = record.value,
                        version = record.version,
                        createdAtMillis = record.createdAtMillis,
                        updatedAtMillis = record.updatedAtMillis,
                    ),
                )
            }
        }
    }

    private fun encryptedEntity(
        entryId: String,
        id: Long,
        name: String,
        label: String,
        value: String,
        version: Int,
        createdAtMillis: Long,
        updatedAtMillis: Long,
    ): KeyStoreEntryEntity {
        val namePayload = cipher.encryptString(name, aad = AAD_NAME)
        val labelPayload = cipher.encryptString(label, aad = AAD_LABEL)
        val valuePayload = cipher.encryptString(value, aad = AAD_VALUE)
        return KeyStoreEntryEntity(
            id = id,
            entryId = entryId,
            nameCiphertext = namePayload.ciphertext,
            nameIv = namePayload.iv,
            labelCiphertext = labelPayload.ciphertext,
            labelIv = labelPayload.iv,
            valueCiphertext = valuePayload.ciphertext,
            valueIv = valuePayload.iv,
            version = version,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun KeyStoreEntryEntity.toDomainOrNull(): KeyStoreEntry? {
        return runCatching {
            KeyStoreEntry(
                id = id,
                entryId = entryId,
                name = cipher.decryptString(
                    payload = CipherPayload(nameCiphertext, nameIv),
                    aad = AAD_NAME,
                ),
                label = cipher.decryptString(
                    payload = CipherPayload(labelCiphertext, labelIv),
                    aad = AAD_LABEL,
                ),
                value = cipher.decryptString(
                    payload = CipherPayload(valueCiphertext, valueIv),
                    aad = AAD_VALUE,
                ),
                version = version,
                createdAtMillis = createdAtMillis,
                updatedAtMillis = updatedAtMillis,
            )
        }.getOrNull()
    }

    private companion object {
        const val INITIAL_VERSION = 1
        const val AAD_NAME = "key_store.entry.name"
        const val AAD_LABEL = "key_store.entry.label"
        const val AAD_VALUE = "key_store.entry.value"
    }
}

data class KeyStoreBackupRecord(
    val entryId: String,
    val name: String,
    val label: String,
    val value: String,
    val version: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
