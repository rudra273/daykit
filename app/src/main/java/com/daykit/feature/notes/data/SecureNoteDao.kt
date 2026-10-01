package com.daykit.feature.notes.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SecureNoteDao {
    @Query("SELECT * FROM secure_notes WHERE deletedAtMillis IS NULL ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<SecureNoteEntity>>

    @Query("SELECT * FROM secure_notes WHERE noteId = :noteId LIMIT 1")
    suspend fun getByNoteId(noteId: String): SecureNoteEntity?

    @Query("SELECT * FROM secure_notes WHERE deletedAtMillis IS NULL")
    suspend fun getAll(): List<SecureNoteEntity>

    @Upsert
    suspend fun upsert(entity: SecureNoteEntity)

    @Query("DELETE FROM secure_notes WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("SELECT * FROM secure_notes WHERE deletedAtMillis IS NOT NULL ORDER BY deletedAtMillis DESC")
    fun observeTrash(): Flow<List<SecureNoteEntity>>

    @Query("UPDATE secure_notes SET deletedAtMillis = :deletedAtMillis WHERE noteId = :noteId")
    suspend fun setDeletedAt(noteId: String, deletedAtMillis: Long?)

    @Query("SELECT noteId FROM secure_notes WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :cutoffMillis")
    suspend fun trashedBefore(cutoffMillis: Long): List<String>

    @Query("SELECT noteId FROM secure_notes WHERE deletedAtMillis IS NOT NULL")
    suspend fun trashedIds(): List<String>

    // --- Images ---

    @Query("SELECT * FROM secure_note_images ORDER BY position ASC, createdAtMillis ASC")
    fun observeAllImages(): Flow<List<SecureNoteImageEntity>>

    @Query("SELECT * FROM secure_note_images WHERE noteId = :noteId ORDER BY position ASC, createdAtMillis ASC")
    suspend fun getImagesForNote(noteId: String): List<SecureNoteImageEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM secure_note_images WHERE noteId = :noteId")
    suspend fun maxImagePosition(noteId: String): Int

    @Upsert
    suspend fun upsertImage(entity: SecureNoteImageEntity)

    @Query("DELETE FROM secure_note_images WHERE imageId = :imageId")
    suspend fun deleteImageById(imageId: String)

    @Query("DELETE FROM secure_note_images WHERE noteId = :noteId")
    suspend fun deleteImagesForNote(noteId: String)
}
