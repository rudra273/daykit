package com.daykit.feature.notes.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SecureNoteDao {
    @Query("SELECT * FROM secure_notes ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<SecureNoteEntity>>

    @Query("SELECT * FROM secure_notes WHERE noteId = :noteId LIMIT 1")
    suspend fun getByNoteId(noteId: String): SecureNoteEntity?

    @Query("SELECT * FROM secure_notes")
    suspend fun getAll(): List<SecureNoteEntity>

    @Upsert
    suspend fun upsert(entity: SecureNoteEntity)

    @Query("DELETE FROM secure_notes WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)
}
