package com.daykit.feature.filelocker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata for one file stored in the encrypted vault.
 *
 * The file bytes themselves live in app-private storage at
 * `filesDir/vault/<storedFileName>`, encrypted with a per-file data key (DEK).
 * The DEK is wrapped by the Android Keystore master key and stored here as
 * [wrappedDekCiphertext] + [wrappedDekIv] — it is never persisted in the clear.
 *
 * Display name and mime type are AES-GCM encrypted at the app layer (on top of
 * SQLCipher), mirroring how other sensitive columns in this DB are stored.
 */
@Entity(
    tableName = "vault_files",
    indices = [Index(value = ["fileId"], unique = true)],
)
data class VaultFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileId: String,
    /** Random on-disk name of the ciphertext blob under filesDir/vault/. */
    val storedFileName: String,
    val nameCiphertext: ByteArray,
    val nameIv: ByteArray,
    val mimeCiphertext: ByteArray,
    val mimeIv: ByteArray,
    /** Per-file DEK, wrapped (encrypted) by the Keystore KEK. */
    val wrappedDekCiphertext: ByteArray,
    val wrappedDekIv: ByteArray,
    /** Plaintext byte size of the original file (for display only). */
    val sizeBytes: Long,
    val createdAtMillis: Long,
)
