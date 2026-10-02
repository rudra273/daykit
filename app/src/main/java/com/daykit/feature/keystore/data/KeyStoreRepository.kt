package com.daykit.feature.keystore.data

import com.daykit.core.data.RecentlyDeleted
import com.daykit.core.security.CipherPayload
import com.daykit.core.security.SensitiveDataLockedException
import com.daykit.core.security.ValueCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * [onSaved] runs after an entry is written or restored (decrypted, so only while unlocked);
 * [onRemoved] after it leaves the live set (trashed or deleted; needs no key). The container
 * uses them to keep expiry reminders in step.
 */
class KeyStoreRepository(
    private val dao: KeyStoreEntryDao,
    private val cipher: ValueCipher,
    private val onSaved: (KeyStoreEntry) -> Unit = {},
    private val onRemoved: (entryId: String) -> Unit = {},
) {
    fun observeEntries(): Flow<List<KeyStoreEntry>> {
        return dao.observeAll().map { entities ->
            entities.mapNotNull { entity -> entity.toDomainOrNull() }
        }.catch { error ->
            // If the DB re-queries in the instant between the key being wiped
            // (background) and the unlock gate recomposing, decryption throws.
            // Emit nothing rather than crash — the gate is about to take over.
            if (error is SensitiveDataLockedException) emit(emptyList()) else throw error
        }
    }

    suspend fun addEntry(draft: KeyDraft) {
        val clean = draft.cleaned()
        val now = System.currentTimeMillis()
        val entryId = UUID.randomUUID().toString()
        dao.upsert(
            entity = encryptedEntity(
                entryId = entryId,
                id = 0,
                name = clean.name,
                label = clean.label,
                document = clean.document,
                version = INITIAL_VERSION,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
        notifySaved(entryId)
    }

    suspend fun updateEntry(entryId: String, draft: KeyDraft) {
        val existing = dao.getByEntryId(entryId) ?: return
        val clean = draft.cleaned()
        dao.upsert(
            encryptedEntity(
                entryId = existing.entryId,
                id = existing.id,
                name = clean.name,
                label = clean.label,
                document = clean.document,
                version = existing.version + 1,
                createdAtMillis = existing.createdAtMillis,
                updatedAtMillis = System.currentTimeMillis(),
            ).copy(deletedAtMillis = existing.deletedAtMillis),
        )
        notifySaved(entryId)
    }

    /** Ticks a recovery code off (or back on) without touching anything else on the entry. */
    suspend fun setRecoveryCodeUsed(entryId: String, code: String, used: Boolean) {
        val entry = dao.getByEntryId(entryId)?.toDomainOrNull() ?: return
        val usedCodes = if (used) entry.usedCodes + code else entry.usedCodes - code
        updateEntry(
            entryId,
            KeyDraft(entry.name, entry.label, entry.type, entry.fields, usedCodes),
        )
    }

    /**
     * Every live entry, or null if the key was wiped mid-read. Callers that act on absence
     * (cancelling reminders) must not mistake a half-decrypted list for deletions.
     */
    suspend fun liveEntriesOrNull(): List<KeyStoreEntry>? {
        val results = dao.getAll().map { it.toDomainResult() }
        if (results.any { it.exceptionOrNull() is SensitiveDataLockedException }) return null
        return results.mapNotNull { it.getOrNull() }
    }

    /** Moves the entry to Recently deleted; it stays encrypted and can be restored. */
    suspend fun deleteEntry(entryId: String) {
        dao.setDeletedAt(entryId, System.currentTimeMillis())
        onRemoved(entryId)
    }

    suspend fun restoreEntry(entryId: String) {
        dao.setDeletedAt(entryId, null)
        notifySaved(entryId)
    }

    suspend fun deleteEntryForever(entryId: String) {
        dao.deleteByEntryId(entryId)
        onRemoved(entryId)
    }

    fun observeTrash(): Flow<List<KeyStoreEntry>> {
        return dao.observeTrash().map { entities ->
            entities.mapNotNull { entity -> entity.toDomainOrNull() }
        }.catch { error ->
            if (error is SensitiveDataLockedException) emit(emptyList()) else throw error
        }
    }

    suspend fun emptyTrash() {
        dao.trashedIds().forEach { deleteEntryForever(it) }
    }

    /** Purges entries past the retention window. Needs no key, so it can run before unlock. */
    suspend fun purgeExpiredTrash(nowMillis: Long = System.currentTimeMillis()) {
        dao.trashedBefore(RecentlyDeleted.purgeCutoff(nowMillis)).forEach { deleteEntryForever(it) }
    }

    suspend fun exportRecords(): List<KeyStoreBackupRecord> {
        return dao.getAll().mapNotNull { entity -> entity.toDomainOrNull() }
            .map { entry ->
                KeyStoreBackupRecord(
                    entryId = entry.entryId,
                    name = entry.name,
                    label = entry.label,
                    document = KeyEntryDocument(entry.type, entry.fields, entry.usedCodes),
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
                        document = record.document,
                        version = record.version,
                        createdAtMillis = record.createdAtMillis,
                        updatedAtMillis = record.updatedAtMillis,
                    ),
                )
                notifySaved(record.entryId)
            }
        }
    }

    private suspend fun notifySaved(entryId: String) {
        dao.getByEntryId(entryId)?.toDomainOrNull()?.let(onSaved)
    }

    private data class CleanDraft(val name: String, val label: String, val document: KeyEntryDocument)

    private fun KeyDraft.cleaned(): CleanDraft {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Key name cannot be empty" }
        // Passwords keep their exact characters, leading/trailing spaces included.
        val cleanFields = fields
            .map { if (it.kind == KeyFieldKind.Secret) it else it.copy(value = it.value.trim()) }
            .filter { it.value.isNotBlank() }
        require(cleanFields.isNotEmpty()) { "Key value cannot be empty" }
        // Forget ticks for codes that are no longer in the list.
        val codes = RecoveryCodes.parse(cleanFields.firstOrNull { it.key == KeyType.CODES }?.value.orEmpty())
        return CleanDraft(
            name = cleanName,
            label = label.trim(),
            document = KeyEntryDocument(type, cleanFields, usedCodes.filter { it in codes }.toSet()),
        )
    }

    private fun encryptedEntity(
        entryId: String,
        id: Long,
        name: String,
        label: String,
        document: KeyEntryDocument,
        version: Int,
        createdAtMillis: Long,
        updatedAtMillis: Long,
    ): KeyStoreEntryEntity {
        val namePayload = cipher.encryptString(name, aad = AAD_NAME)
        val labelPayload = cipher.encryptString(label, aad = AAD_LABEL)
        // A plain one-value Key keeps the original format, so older backups and app versions
        // read it unchanged. Documents use their own AAD, binding the format to the ciphertext.
        val single = document.isSingleValue
        val valuePayload = if (single) {
            cipher.encryptString(document.fields.first { it.key == KeyType.VALUE }.value, aad = AAD_VALUE)
        } else {
            cipher.encryptString(document.toJson(), aad = AAD_DOCUMENT)
        }
        return KeyStoreEntryEntity(
            id = id,
            entryId = entryId,
            nameCiphertext = namePayload.ciphertext,
            nameIv = namePayload.iv,
            labelCiphertext = labelPayload.ciphertext,
            labelIv = labelPayload.iv,
            valueCiphertext = valuePayload.ciphertext,
            valueIv = valuePayload.iv,
            format = if (single) KeyEntryDocument.FORMAT_SINGLE else KeyEntryDocument.FORMAT_DOCUMENT,
            version = version,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun KeyStoreEntryEntity.toDomainOrNull(): KeyStoreEntry? = toDomainResult().getOrNull()

    private fun KeyStoreEntryEntity.toDomainResult(): Result<KeyStoreEntry> {
        return runCatching {
            val valuePayload = CipherPayload(valueCiphertext, valueIv)
            val document = when (format) {
                KeyEntryDocument.FORMAT_DOCUMENT ->
                    KeyEntryDocument.fromJson(cipher.decryptString(valuePayload, aad = AAD_DOCUMENT))
                else -> KeyEntryDocument.single(cipher.decryptString(valuePayload, aad = AAD_VALUE))
            }
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
                type = document.type,
                fields = document.fields,
                usedCodes = document.usedCodes,
                version = version,
                createdAtMillis = createdAtMillis,
                updatedAtMillis = updatedAtMillis,
                deletedAtMillis = deletedAtMillis,
            )
        }
    }

    private companion object {
        const val INITIAL_VERSION = 1
        const val AAD_NAME = "key_store.entry.name"
        const val AAD_LABEL = "key_store.entry.label"
        const val AAD_VALUE = "key_store.entry.value"
        const val AAD_DOCUMENT = "key_store.entry.document"
    }
}

data class KeyStoreBackupRecord(
    val entryId: String,
    val name: String,
    val label: String,
    val document: KeyEntryDocument,
    val version: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
