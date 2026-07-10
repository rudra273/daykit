package com.daykit.feature.filelocker.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.daykit.core.security.CipherPayload
import com.daykit.core.security.SensitiveDataLockedException
import com.daykit.core.security.ValueCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID

/**
 * The vault: files are always ciphertext at rest in app-private storage and are
 * only ever decrypted to memory (for viewing) or to a user-chosen location (on
 * explicit export). No plaintext copy is written to disk to view a file.
 *
 * Key hierarchy:
 *  - each file has a random 256-bit data key (DEK)
 *  - the DEK is wrapped by the PIN-derived session key ([cipher]) and stored in
 *    the (SQLCipher-encrypted) DB — never persisted in the clear, and not
 *    recoverable without the user's PIN
 *  - the file bytes are stream-encrypted with the DEK via [VaultStreamingCrypto]
 */
class VaultFileRepository(
    context: Context,
    private val dao: VaultFileDao,
    private val streamingCrypto: VaultStreamingCrypto,
    private val cipher: ValueCipher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val vaultDir: File by lazy {
        File(appContext.filesDir, VAULT_DIR).apply { mkdirs() }
    }

    fun observeFiles(): Flow<List<VaultFile>> =
        dao.observeAll().map { list -> list.map { it.toVaultFile() } }
            .catch { error ->
                // Metadata decryption needs the PIN-derived key. If the DB
                // re-queries between the key being wiped (background) and the
                // unlock gate recomposing, emit nothing instead of crashing.
                if (error is SensitiveDataLockedException) emit(emptyList()) else throw error
            }

    /**
     * Imports [uri] into the vault: streams source -> encrypt -> app-private
     * blob, records metadata, then deletes the original. Crash-safe: the DB row
     * is written only after the ciphertext is fully flushed and fsync'd, and the
     * original is deleted only after the row commits. A crash mid-import leaves
     * at most an orphan blob (cleaned by [pruneOrphans]), never data loss.
     *
     * @return true if the original was also removed from its source location.
     */
    suspend fun importFile(uri: Uri): Boolean {
        val meta = resolveSourceMeta(uri)
        val fileId = UUID.randomUUID().toString()
        val storedName = "$fileId$BLOB_EXTENSION"
        val blob = File(vaultDir, storedName)

        val dek = ByteArray(DEK_BYTES).also(secureRandom::nextBytes)
        try {
            var plaintextSize = 0L
            appContext.contentResolver.openInputStream(uri).use { rawInput ->
                checkNotNull(rawInput) { "Could not open source file" }
                FileOutputStream(blob).use { fileOut ->
                    streamingCrypto.newEncryptingStream(dek, fileOut, fileId.toByteArray()).use { cipherOut ->
                        plaintextSize = rawInput.copyToCounting(cipherOut)
                    }
                    fileOut.fd.sync()
                }
            }

            val wrappedDek = cipher.encryptBytes(dek, DEK_AAD)
            val nameCipher = cipher.encryptString(meta.name, fileId)
            val mimeCipher = cipher.encryptString(meta.mimeType, fileId)
            dao.upsert(
                VaultFileEntity(
                    fileId = fileId,
                    storedFileName = storedName,
                    nameCiphertext = nameCipher.ciphertext,
                    nameIv = nameCipher.iv,
                    mimeCiphertext = mimeCipher.ciphertext,
                    mimeIv = mimeCipher.iv,
                    wrappedDekCiphertext = wrappedDek.ciphertext,
                    wrappedDekIv = wrappedDek.iv,
                    sizeBytes = plaintextSize,
                    createdAtMillis = clock(),
                ),
            )
        } catch (error: Throwable) {
            blob.delete()
            throw error
        } finally {
            dek.fill(0)
        }

        return deleteOriginal(uri)
    }

    /**
     * Opens a decrypting stream over a stored file's plaintext. The caller owns
     * closing it. Plaintext exists only as the bytes it reads — nothing is
     * written to disk. Returns null if the file id is unknown.
     */
    suspend fun openDecryptedStream(fileId: String): InputStream? {
        val entity = dao.getByFileId(fileId) ?: return null
        val blob = File(vaultDir, entity.storedFileName)
        if (!blob.exists()) return null
        val dek = unwrapDek(entity)
        return try {
            streamingCrypto.newDecryptingStream(dek, blob.inputStream(), fileId.toByteArray())
        } finally {
            dek.fill(0)
        }
    }

    /**
     * Opens a random-access decrypting channel over a stored file's plaintext,
     * for on-demand media playback. Seeking decrypts only the touched 1 MiB
     * segment — no full-file decrypt, no plaintext temp file. Caller closes it.
     * Returns null if the file id is unknown or the blob is missing.
     */
    suspend fun openSeekableChannel(fileId: String): java.nio.channels.SeekableByteChannel? {
        val entity = dao.getByFileId(fileId) ?: return null
        val blob = File(vaultDir, entity.storedFileName)
        if (!blob.exists()) return null
        val dek = unwrapDek(entity)
        return try {
            val channel = java.io.RandomAccessFile(blob, "r").channel
            streamingCrypto.newSeekableDecryptingChannel(dek, channel, fileId.toByteArray())
        } finally {
            dek.fill(0)
        }
    }

    /** Streams a file's decrypted bytes into [destination]. */
    suspend fun exportTo(fileId: String, destination: OutputStream): Boolean {
        val input = openDecryptedStream(fileId) ?: return false
        input.use { plaintext ->
            destination.use { out -> plaintext.copyTo(out) }
        }
        return true
    }

    suspend fun delete(fileId: String) {
        val entity = dao.getByFileId(fileId) ?: return
        File(vaultDir, entity.storedFileName).delete()
        dao.deleteByFileId(fileId)
    }

    /**
     * Exports every vault file as decrypted plaintext bytes, for inclusion in an
     * (encrypted) app backup. Called ONLY when the user has explicitly opted in
     * to backing up vault files — see the backup contributor. The bytes are
     * re-protected by the backup's own AES-256-GCM envelope.
     */
    suspend fun exportForBackup(): List<VaultBackupRecord> {
        return dao.observeAllOnce().map { entity ->
            val bytes = openDecryptedStream(entity.fileId)?.use { it.readBytes() } ?: ByteArray(0)
            VaultBackupRecord(
                fileId = entity.fileId,
                name = cipher.decryptString(CipherPayload(entity.nameCiphertext, entity.nameIv), entity.fileId),
                mimeType = cipher.decryptString(CipherPayload(entity.mimeCiphertext, entity.mimeIv), entity.fileId),
                createdAtMillis = entity.createdAtMillis,
                plaintext = bytes,
            )
        }
    }

    /** Re-imports vault files from a backup, re-encrypting each with a fresh DEK. */
    suspend fun importFromBackup(records: List<VaultBackupRecord>) {
        records.forEach { record ->
            if (dao.getByFileId(record.fileId) != null) return@forEach
            val storedName = "${record.fileId}$BLOB_EXTENSION"
            val blob = File(vaultDir, storedName)
            val dek = ByteArray(DEK_BYTES).also(secureRandom::nextBytes)
            try {
                FileOutputStream(blob).use { fileOut ->
                    streamingCrypto.newEncryptingStream(dek, fileOut, record.fileId.toByteArray()).use { cipherOut ->
                        cipherOut.write(record.plaintext)
                    }
                    fileOut.fd.sync()
                }
                val wrappedDek = cipher.encryptBytes(dek, DEK_AAD)
                val nameCipher = cipher.encryptString(record.name, record.fileId)
                val mimeCipher = cipher.encryptString(record.mimeType, record.fileId)
                dao.upsert(
                    VaultFileEntity(
                        fileId = record.fileId,
                        storedFileName = storedName,
                        nameCiphertext = nameCipher.ciphertext,
                        nameIv = nameCipher.iv,
                        mimeCiphertext = mimeCipher.ciphertext,
                        mimeIv = mimeCipher.iv,
                        wrappedDekCiphertext = wrappedDek.ciphertext,
                        wrappedDekIv = wrappedDek.iv,
                        sizeBytes = record.plaintext.size.toLong(),
                        createdAtMillis = record.createdAtMillis,
                    ),
                )
            } catch (error: Throwable) {
                blob.delete()
                throw error
            } finally {
                dek.fill(0)
            }
        }
    }

    private fun unwrapDek(entity: VaultFileEntity): ByteArray {
        return cipher.decryptBytes(
            payload = CipherPayload(entity.wrappedDekCiphertext, entity.wrappedDekIv),
            aad = DEK_AAD,
        )
    }

    private fun VaultFileEntity.toVaultFile(): VaultFile {
        return VaultFile(
            fileId = fileId,
            name = cipher.decryptString(CipherPayload(nameCiphertext, nameIv), fileId),
            mimeType = cipher.decryptString(CipherPayload(mimeCiphertext, mimeIv), fileId),
            sizeBytes = sizeBytes,
            createdAtMillis = createdAtMillis,
        )
    }

    private data class SourceMeta(val name: String, val mimeType: String)

    private fun resolveSourceMeta(uri: Uri): SourceMeta {
        val resolver = appContext.contentResolver
        val mimeType = resolver.getType(uri).orEmpty().ifBlank { GENERIC_MIME_TYPE }
        val fallback = if (mimeType.startsWith("video/")) "video" else "file"
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
            ?.takeIf { it.isNotBlank() } ?: fallback
        return SourceMeta(name = name, mimeType = mimeType)
    }

    private fun deleteOriginal(uri: Uri): Boolean {
        return runCatching {
            appContext.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }

    private fun InputStream.copyToCounting(out: OutputStream): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return total
    }

    companion object {
        private const val VAULT_DIR = "vault"
        private const val BLOB_EXTENSION = ".bin"
        private const val DEK_BYTES = 32
        private const val GENERIC_MIME_TYPE = "application/octet-stream"
        private const val DEK_AAD = "daykit.vault.file.dek"
        private val secureRandom = SecureRandom()
    }
}
