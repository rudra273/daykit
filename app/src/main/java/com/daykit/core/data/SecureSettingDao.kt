package com.rudra.daykit.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SecureSettingDao {
    @Query("SELECT * FROM secure_settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SecureSettingEntity?

    @Query("SELECT * FROM secure_settings WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<SecureSettingEntity?>

    @Query("DELETE FROM secure_settings WHERE `key` = :key")
    suspend fun delete(key: String)

    @Upsert
    suspend fun upsert(entity: SecureSettingEntity)
}
