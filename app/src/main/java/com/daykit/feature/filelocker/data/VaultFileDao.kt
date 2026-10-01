package com.daykit.feature.filelocker.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultFileDao {
    @Query("SELECT * FROM vault_files WHERE deletedAtMillis IS NULL ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<VaultFileEntity>>

    @Query("SELECT * FROM vault_files WHERE fileId = :fileId LIMIT 1")
    suspend fun getByFileId(fileId: String): VaultFileEntity?

    @Query("SELECT * FROM vault_files WHERE deletedAtMillis IS NULL ORDER BY createdAtMillis DESC")
    suspend fun observeAllOnce(): List<VaultFileEntity>

    @Upsert
    suspend fun upsert(entity: VaultFileEntity)

    @Query("DELETE FROM vault_files WHERE fileId = :fileId")
    suspend fun deleteByFileId(fileId: String)

    @Query("SELECT * FROM vault_files WHERE deletedAtMillis IS NOT NULL ORDER BY deletedAtMillis DESC")
    fun observeTrash(): Flow<List<VaultFileEntity>>

    @Query("UPDATE vault_files SET deletedAtMillis = :deletedAtMillis WHERE fileId = :fileId")
    suspend fun setDeletedAt(fileId: String, deletedAtMillis: Long?)

    @Query("SELECT fileId FROM vault_files WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :cutoffMillis")
    suspend fun trashedBefore(cutoffMillis: Long): List<String>

    @Query("SELECT fileId FROM vault_files WHERE deletedAtMillis IS NOT NULL")
    suspend fun trashedIds(): List<String>
}
