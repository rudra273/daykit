package com.daykit.feature.filelocker.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.daykit.core.designsystem.components.AppBackButton
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.feature.filelocker.data.VaultFileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Identifies a vault file to preview. Bytes are fetched via the repository. */
data class FileLockerPreviewItem(
    val fileId: String,
    val name: String,
    val mimeType: String,
)

@Composable
fun FileLockerPreviewScreen(
    item: FileLockerPreviewItem,
    repository: VaultFileRepository,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBackButton(onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Encrypted preview",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                item.mimeType.startsWith("image/") -> EncryptedImagePreview(item = item, repository = repository)
                item.mimeType.startsWith("video/") -> EncryptedVideoPreview(item = item, repository = repository)
                else -> PreviewMessage("Preview is not available for this file type. Export it to open elsewhere.")
            }
        }
    }
}

@Composable
private fun EncryptedImagePreview(
    item: FileLockerPreviewItem,
    repository: VaultFileRepository,
) {
    var imageBitmap by remember(item.fileId) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(item.fileId) { mutableStateOf(false) }

    LaunchedEffect(item.fileId) {
        imageBitmap = null
        failed = false
        // Decrypt to RAM only — no plaintext is written to disk to view.
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                repository.openDecryptedStream(item.fileId)?.use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
        imageBitmap = decoded
        failed = decoded == null
    }

    when {
        imageBitmap != null -> Image(
            bitmap = imageBitmap!!,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        failed -> PreviewMessage("Could not decrypt this image.")

        else -> LoadingIndicator(delayMillis = 0)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun EncryptedVideoPreview(
    item: FileLockerPreviewItem,
    repository: VaultFileRepository,
) {
    val context = LocalContext.current

    // Plays straight from the encrypted blob via a custom decrypting DataSource —
    // only the byte ranges the player seeks to are decrypted, in memory.
    val player = remember(item.fileId) {
        val factory: DataSource.Factory = VaultMediaDataSource.Factory(repository)
        val mediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(VaultMediaDataSource.uriFor(item.fileId))
                    .setMimeType(item.mimeType.ifBlank { MimeTypes.APPLICATION_MP4 })
                    .build(),
            )
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
            }
        },
        update = { view -> view.player = player },
    )
}

@Composable
private fun PreviewMessage(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}
