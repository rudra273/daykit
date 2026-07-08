package com.rudra.daykit.feature.notes.data

import com.rudra.daykit.core.security.CipherPayload
import com.rudra.daykit.core.security.SensitiveValueCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class SecureNoteRepository(
    private val dao: SecureNoteDao,
    private val cipher: SensitiveValueCipher,
) {
    fun observeNotes(): Flow<List<SecureNote>> {
        return dao.observeAll().map { entities ->
            entities.mapNotNull { entity -> entity.toDomainOrNull() }
        }
    }

    suspend fun addNote(title: String, content: String, labels: String) {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        require(cleanTitle.isNotBlank() || cleanContent.isNotBlank()) { "Note cannot be empty" }
        val now = System.currentTimeMillis()
        dao.upsert(
            encryptedEntity(
                id = 0,
                noteId = UUID.randomUUID().toString(),
                title = cleanTitle,
                content = cleanContent,
                labels = cleanLabels(labels),
                version = INITIAL_VERSION,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    suspend fun updateNote(noteId: String, title: String, content: String, labels: String) {
        val existing = dao.getByNoteId(noteId) ?: return
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        require(cleanTitle.isNotBlank() || cleanContent.isNotBlank()) { "Note cannot be empty" }
        dao.upsert(
            encryptedEntity(
                id = existing.id,
                noteId = existing.noteId,
                title = cleanTitle,
                content = cleanContent,
                labels = cleanLabels(labels),
                version = existing.version + 1,
                createdAtMillis = existing.createdAtMillis,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteNote(noteId: String) {
        dao.deleteByNoteId(noteId)
    }

    suspend fun exportRecords(): List<SecureNoteBackupRecord> {
        return dao.getAll().mapNotNull { entity -> entity.toDomainOrNull() }
            .map { note ->
                SecureNoteBackupRecord(
                    noteId = note.noteId,
                    title = note.title,
                    content = note.content,
                    labels = note.labels,
                    version = note.version,
                    createdAtMillis = note.createdAtMillis,
                    updatedAtMillis = note.updatedAtMillis,
                )
            }
    }

    suspend fun importRecords(records: List<SecureNoteBackupRecord>) {
        records.forEach { record ->
            val existing = dao.getByNoteId(record.noteId)
            if (existing == null || record.updatedAtMillis > existing.updatedAtMillis) {
                dao.upsert(
                    encryptedEntity(
                        id = existing?.id ?: 0,
                        noteId = record.noteId,
                        title = record.title,
                        content = record.content,
                        labels = cleanLabels(record.labels),
                        version = record.version,
                        createdAtMillis = record.createdAtMillis,
                        updatedAtMillis = record.updatedAtMillis,
                    ),
                )
            }
        }
    }

    private fun encryptedEntity(
        id: Long,
        noteId: String,
        title: String,
        content: String,
        labels: String,
        version: Int,
        createdAtMillis: Long,
        updatedAtMillis: Long,
    ): SecureNoteEntity {
        val titlePayload = cipher.encryptString(title, aad = AAD_TITLE)
        val contentPayload = cipher.encryptString(content, aad = AAD_CONTENT)
        val labelsPayload = cipher.encryptString(labels, aad = AAD_LABELS)
        return SecureNoteEntity(
            id = id,
            noteId = noteId,
            titleCiphertext = titlePayload.ciphertext,
            titleIv = titlePayload.iv,
            contentCiphertext = contentPayload.ciphertext,
            contentIv = contentPayload.iv,
            labelsCiphertext = labelsPayload.ciphertext,
            labelsIv = labelsPayload.iv,
            version = version,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun SecureNoteEntity.toDomainOrNull(): SecureNote? {
        return runCatching {
            SecureNote(
                id = id,
                noteId = noteId,
                title = cipher.decryptString(CipherPayload(titleCiphertext, titleIv), aad = AAD_TITLE),
                content = cipher.decryptString(CipherPayload(contentCiphertext, contentIv), aad = AAD_CONTENT),
                labels = cipher.decryptString(CipherPayload(labelsCiphertext, labelsIv), aad = AAD_LABELS),
                version = version,
                createdAtMillis = createdAtMillis,
                updatedAtMillis = updatedAtMillis,
            )
        }.getOrNull()
    }

    private fun cleanLabels(labels: String): String {
        return labels.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
    }

    private companion object {
        const val INITIAL_VERSION = 1
        const val AAD_TITLE = "secure_notes.note.title"
        const val AAD_CONTENT = "secure_notes.note.content"
        const val AAD_LABELS = "secure_notes.note.labels"
    }
}

data class SecureNoteBackupRecord(
    val noteId: String,
    val title: String,
    val content: String,
    val labels: String,
    val version: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
