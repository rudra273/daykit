package com.daykit.feature.applock.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedAppDao {
    @Query("SELECT * FROM locked_apps")
    fun observeAll(): Flow<List<LockedAppEntity>>

    @Query("SELECT * FROM locked_apps WHERE enabled = 1")
    fun observeEnabled(): Flow<List<LockedAppEntity>>

    @Query("SELECT * FROM locked_apps")
    suspend fun getAll(): List<LockedAppEntity>

    @Upsert
    suspend fun upsert(entity: LockedAppEntity)

    @Query("DELETE FROM locked_apps WHERE id = :id")
    suspend fun deleteById(id: Long)
}
