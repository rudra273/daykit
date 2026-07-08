package com.rudra.daykit.feature.notes.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "secure_notes",
    indices = [Index(value = ["noteId"], unique = true)],
)
data class SecureNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: String,
    val titleCiphertext: ByteArray,
    val titleIv: ByteArray,
    val contentCiphertext: ByteArray,
    val contentIv: ByteArray,
    val labelsCiphertext: ByteArray,
    val labelsIv: ByteArray,
    val version: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
