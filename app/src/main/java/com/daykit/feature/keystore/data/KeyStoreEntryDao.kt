package com.daykit.feature.keystore.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyStoreEntryDao {
    @Query("SELECT * FROM key_store_entries WHERE deletedAtMillis IS NULL ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<KeyStoreEntryEntity>>

    @Query("SELECT * FROM key_store_entries WHERE entryId = :entryId LIMIT 1")
    suspend fun getByEntryId(entryId: String): KeyStoreEntryEntity?

    @Query("SELECT * FROM key_store_entries WHERE deletedAtMillis IS NULL")
    suspend fun getAll(): List<KeyStoreEntryEntity>

    @Upsert
    suspend fun upsert(entity: KeyStoreEntryEntity)

    @Query("DELETE FROM key_store_entries WHERE entryId = :entryId")
    suspend fun deleteByEntryId(entryId: String)

    @Query("SELECT * FROM key_store_entries WHERE deletedAtMillis IS NOT NULL ORDER BY deletedAtMillis DESC")
    fun observeTrash(): Flow<List<KeyStoreEntryEntity>>

    @Query("UPDATE key_store_entries SET deletedAtMillis = :deletedAtMillis WHERE entryId = :entryId")
    suspend fun setDeletedAt(entryId: String, deletedAtMillis: Long?)

    @Query("SELECT entryId FROM key_store_entries WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :cutoffMillis")
    suspend fun trashedBefore(cutoffMillis: Long): List<String>

    @Query("SELECT entryId FROM key_store_entries WHERE deletedAtMillis IS NOT NULL")
    suspend fun trashedIds(): List<String>
}
