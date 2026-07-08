package com.daykit.feature.keystore.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "key_store_entries",
    indices = [Index(value = ["entryId"], unique = true)],
)
data class KeyStoreEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: String,
    val nameCiphertext: ByteArray,
    val nameIv: ByteArray,
    val labelCiphertext: ByteArray,
    val labelIv: ByteArray,
    val valueCiphertext: ByteArray,
    val valueIv: ByteArray,
    val version: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
