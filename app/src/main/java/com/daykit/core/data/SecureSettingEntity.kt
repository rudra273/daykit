package com.daykit.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secure_settings")
data class SecureSettingEntity(
    @PrimaryKey val key: String,
    val valueCiphertext: ByteArray,
    val valueIv: ByteArray,
    val updatedAtMillis: Long,
)
