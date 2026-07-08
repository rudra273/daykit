package com.daykit.feature.applock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locked_apps")
data class LockedAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageNameCiphertext: ByteArray,
    val packageNameIv: ByteArray,
    val labelCiphertext: ByteArray,
    val labelIv: ByteArray,
    val enabled: Boolean,
    val updatedAtMillis: Long,
)
