package com.daykit.feature.notes.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An image attached to a [SecureNoteEntity]. The image bytes are AES-GCM encrypted
 * at the app layer ([imageCiphertext] + [imageIv]) on top of the SQLCipher-encrypted
 * database, mirroring how note text fields are stored.
 */
@Entity(
    tableName = "secure_note_images",
    indices = [
        Index(value = ["imageId"], unique = true),
        Index(value = ["noteId"]),
    ],
)
data class SecureNoteImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imageId: String,
    val noteId: String,
    val imageCiphertext: ByteArray,
    val imageIv: ByteArray,
    val position: Int,
    val createdAtMillis: Long,
)
