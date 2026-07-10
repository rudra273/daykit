package com.daykit.feature.filelocker.data

/** A decrypted view of a vault file's metadata (bytes stay encrypted on disk). */
data class VaultFile(
    val fileId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAtMillis: Long,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

/** A vault file's decrypted bytes + metadata, used only for backup export/import. */
data class VaultBackupRecord(
    val fileId: String,
    val name: String,
    val mimeType: String,
    val createdAtMillis: Long,
    val plaintext: ByteArray,
)
