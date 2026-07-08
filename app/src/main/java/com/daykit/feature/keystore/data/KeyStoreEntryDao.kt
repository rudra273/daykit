package com.daykit.feature.keystore.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyStoreEntryDao {
    @Query("SELECT * FROM key_store_entries ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<KeyStoreEntryEntity>>

    @Query("SELECT * FROM key_store_entries WHERE entryId = :entryId LIMIT 1")
    suspend fun getByEntryId(entryId: String): KeyStoreEntryEntity?

    @Query("SELECT * FROM key_store_entries")
    suspend fun getAll(): List<KeyStoreEntryEntity>

    @Upsert
    suspend fun upsert(entity: KeyStoreEntryEntity)

    @Query("DELETE FROM key_store_entries WHERE entryId = :entryId")
    suspend fun deleteByEntryId(entryId: String)
}
