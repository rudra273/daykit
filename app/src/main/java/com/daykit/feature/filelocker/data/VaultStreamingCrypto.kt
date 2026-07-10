package com.daykit.feature.filelocker.data

import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel
import java.nio.channels.FileChannel

/**
 * Chunked, streaming authenticated encryption for vault files.
 *
 * Backed by Tink's [AesGcmHkdfStreaming] (the AES256_GCM_HKDF_1MB scheme): the
 * plaintext is split into 1 MiB segments, each encrypted AES-256-GCM with its
 * own derived key + nonce, and the segment index is bound into the derivation
 * so segments cannot be reordered, dropped, or truncated undetected.
 *
 * Why streaming rather than a single doFinal():
 *  - Constant memory — a multi-GB video encrypts/decrypts with ~1 MiB of RAM.
 *  - Seekable — [newDecryptingStream] returns a [java.nio.channels.SeekableByteChannel]
 *    wrapper capability via Tink, which is what makes on-demand video playback
 *    possible in a later phase.
 *
 * The [dataKey] here is a per-file 256-bit DEK. It is never persisted in the
 * clear — the caller wraps it with the Android Keystore KEK before storing it.
 */
class VaultStreamingCrypto {

    private fun streamingAead(dataKey: ByteArray): StreamingAead {
        require(dataKey.size == KEY_SIZE_BYTES) {
            "Vault data key must be $KEY_SIZE_BYTES bytes, was ${dataKey.size}"
        }
        return AesGcmHkdfStreaming(
            dataKey,
            HKDF_ALGORITHM,
            KEY_SIZE_BYTES,
            SEGMENT_SIZE_BYTES,
            FIRST_SEGMENT_OFFSET,
        )
    }

    /**
     * Wraps [ciphertextOut] so that bytes written to the returned stream are
     * encrypted before hitting [ciphertextOut]. [associatedData] is
     * authenticated but not encrypted — pass a stable per-file value (e.g. the
     * file id) so a ciphertext cannot be swapped between records.
     *
     * The returned stream MUST be closed to flush the final segment + auth tag.
     */
    fun newEncryptingStream(
        dataKey: ByteArray,
        ciphertextOut: OutputStream,
        associatedData: ByteArray,
    ): OutputStream {
        return streamingAead(dataKey).newEncryptingStream(ciphertextOut, associatedData)
    }

    /**
     * Wraps [ciphertextIn] so that reads from the returned stream yield decrypted
     * plaintext. Throws if authentication fails (tampering, wrong key, wrong AAD).
     */
    fun newDecryptingStream(
        dataKey: ByteArray,
        ciphertextIn: InputStream,
        associatedData: ByteArray,
    ): InputStream {
        return streamingAead(dataKey).newDecryptingStream(ciphertextIn, associatedData)
    }

    /**
     * A random-access decrypting view over the plaintext, backed by [ciphertextChannel].
     * Seeking maps to the enclosing 1 MiB segment, so a player can jump anywhere in a
     * video and only that segment is decrypted — no full-file decrypt, no temp file.
     * The returned channel owns [ciphertextChannel] and closes it when closed.
     */
    fun newSeekableDecryptingChannel(
        dataKey: ByteArray,
        ciphertextChannel: FileChannel,
        associatedData: ByteArray,
    ): SeekableByteChannel {
        return streamingAead(dataKey).newSeekableDecryptingChannel(ciphertextChannel, associatedData)
    }

    companion object {
        private const val KEY_SIZE_BYTES = 32
        private const val HKDF_ALGORITHM = "HmacSha256"
        private const val SEGMENT_SIZE_BYTES = 1 shl 20 // 1 MiB
        private const val FIRST_SEGMENT_OFFSET = 0
    }
}
