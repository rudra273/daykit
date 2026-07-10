package com.daykit.feature.filelocker.ui

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.daykit.feature.filelocker.data.VaultFileRepository
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import kotlin.math.min

/**
 * A media3 [DataSource] that plays a vault file straight from its encrypted
 * blob — decrypting only the byte ranges the player requests, in memory. No
 * plaintext copy is ever written to disk to play a video.
 *
 * The [DataSpec] URI carries the vault file id (see [uriFor]); the source opens
 * a Tink seekable decrypting channel over the ciphertext and serves reads from
 * it, honoring [DataSpec.position] and [DataSpec.length] so the player can seek.
 */
@UnstableApi
class VaultMediaDataSource(
    private val repository: VaultFileRepository,
) : BaseDataSource(false) {

    private var channel: SeekableByteChannel? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val fileId = dataSpec.uri.getQueryParameter(QUERY_FILE_ID)
            ?: error("Vault media URI missing file id")

        val opened = runBlocking { repository.openSeekableChannel(fileId) }
            ?: error("Vault file not found: $fileId")
        channel = opened

        val size = opened.size()
        opened.position(dataSpec.position)

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            size - dataSpec.position
        }
        if (bytesRemaining < 0) throw java.io.EOFException()

        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val channel = channel ?: return C.RESULT_END_OF_INPUT

        val toRead = min(length.toLong(), bytesRemaining).toInt()
        val byteBuffer = ByteBuffer.wrap(buffer, offset, toRead)
        val read = channel.read(byteBuffer)
        if (read <= 0) return C.RESULT_END_OF_INPUT

        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            channel?.close()
        } finally {
            channel = null
            transferEnded()
        }
    }

    class Factory(
        private val repository: VaultFileRepository,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = VaultMediaDataSource(repository)
    }

    companion object {
        private const val SCHEME = "daykitvault"
        private const val QUERY_FILE_ID = "fileId"

        /** Builds the URI a player uses to stream a vault file by id. */
        fun uriFor(fileId: String): Uri =
            Uri.Builder().scheme(SCHEME).authority("file").appendQueryParameter(QUERY_FILE_ID, fileId).build()
    }
}
