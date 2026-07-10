package com.daykit.feature.filelocker.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultFileDao {
    @Query("SELECT * FROM vault_files ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<VaultFileEntity>>

    @Query("SELECT * FROM vault_files WHERE fileId = :fileId LIMIT 1")
    suspend fun getByFileId(fileId: String): VaultFileEntity?

    @Query("SELECT * FROM vault_files ORDER BY createdAtMillis DESC")
    suspend fun observeAllOnce(): List<VaultFileEntity>

    @Upsert
    suspend fun upsert(entity: VaultFileEntity)

    @Query("DELETE FROM vault_files WHERE fileId = :fileId")
    suspend fun deleteByFileId(fileId: String)
}
