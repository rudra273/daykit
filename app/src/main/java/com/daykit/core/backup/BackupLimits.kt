package com.daykit.core.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream

class BackupSizeException(message: String) : IllegalArgumentException(message)

/** The v1 JSON envelope is in-memory. Bound it until a streaming format replaces it. */
object BackupLimits {
    const val MAX_VAULT_BYTES = 4 * 1024 * 1024
    const val MAX_ENVELOPE_BYTES = 16 * 1024 * 1024
    const val VAULT_LIMIT_MESSAGE = "Vault backup supports up to 4 MiB of files in total. Turn off Vault files to back up your other data; export large vault files separately."

    fun decodedBase64Size(encoded: String): Long {
        require(encoded.length % 4 == 0) { "Invalid vault file encoding" }
        val padding = encoded.takeLast(2).count { it == '=' }
        require(encoded.dropLast(padding).all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' }) {
            "Invalid vault file encoding"
        }
        return encoded.length.toLong() / 4 * 3 - padding
    }

    fun checkVaultSizes(sizes: Iterable<Long>) {
        var remaining = MAX_VAULT_BYTES.toLong()
        for (size in sizes) {
            if (size < 0 || size > remaining) throw BackupSizeException(VAULT_LIMIT_MESSAGE)
            remaining -= size
        }
    }

    /** Check actual bytes too: neither file metadata nor HTTP content length is trusted. */
    fun readBounded(input: InputStream, limit: Int, message: String): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var remaining = limit
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, remaining + 1))
            if (count < 0) break
            if (count > remaining) throw BackupSizeException(message)
            output.write(buffer, 0, count)
            remaining -= count
        }
        return output.toByteArray()
    }

    fun readEnvelope(input: InputStream): String = readBounded(input, MAX_ENVELOPE_BYTES,
        "This backup exceeds the supported 16 MiB file size.").toString(Charsets.UTF_8)
}
