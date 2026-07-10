package com.daykit.feature.notes.data

/** A decrypted image attached to a note. [bytes] are the raw (JPEG) image bytes. */
data class SecureNoteImage(
    val imageId: String,
    val noteId: String,
    val bytes: ByteArray,
    val position: Int,
    val createdAtMillis: Long,
)
