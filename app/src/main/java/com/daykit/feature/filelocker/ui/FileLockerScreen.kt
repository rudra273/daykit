package com.daykit.feature.filelocker.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppExtendedFab
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.AppTopBarHeight
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.StatTile
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.filelocker.data.VaultFile
import com.daykit.feature.filelocker.data.VaultFileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileLockerScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = container.vaultFileRepository
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedIds = remember { mutableStateListOf<String>() }
    var working by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<FileLockerPreviewItem?>(null) }

    val files by repository.observeFiles().collectAsStateWithLifecycle(initialValue = emptyList())
    val selectionMode = selectedIds.isNotEmpty()
    val gridState = rememberLazyGridState()

    fun snack(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    previewItem?.let { item ->
        FileLockerPreviewScreen(
            item = item,
            repository = repository,
            onBack = { previewItem = null },
        )
        return
    }

    BackHandler(enabled = selectionMode) { selectedIds.clear() }

    // Drop selections that no longer exist.
    selectedIds.removeAll { id -> files.none { it.fileId == id } }

    val exportFolderLauncher = rememberLauncherForActivityResult(DestinationFolderPickerContract()) { destinationUri ->
        if (destinationUri == null) {
            snack("Choose a destination folder to export the selected files.")
            return@rememberLauncherForActivityResult
        }
        val selected = files.filter { selectedIds.contains(it.fileId) }
        if (selected.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            val exported = withContext(Dispatchers.IO) {
                exportFiles(context, repository, selected, destinationUri)
            }
            selectedIds.clear()
            working = false
            snack("$exported file(s) exported. The originals remain protected in the vault.")
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            working = true
            var imported = 0
            var failed = 0
            var removedOriginals = 0
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    runCatching {
                        if (repository.importFile(uri)) removedOriginals += 1
                        imported += 1
                    }.onFailure { error ->
                        android.util.Log.e("FileVault", "Import failed for $uri", error)
                        failed += 1
                    }
                }
            }
            working = false
            snack(
                buildString {
                    append("$imported file(s) encrypted into the vault.")
                    if (failed > 0) append(" $failed failed.")
                    if (imported > removedOriginals) {
                        append(" If an original still appears in Gallery, delete it manually.")
                    }
                },
            )
        }
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(HideableMediaPickerContract()) { uris ->
        importUris(uris)
    }

    // Media shared into the app ("share to DayKit"): consume and import once.
    LaunchedEffect(Unit) {
        container.pendingVaultShares.collect { shared ->
            if (shared.isNotEmpty()) {
                container.pendingVaultShares.value = emptyList()
                importUris(shared)
            }
        }
    }

    val totalBytes = remember(files) { files.sumOf { it.sizeBytes } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = selectedIds.size,
                    onCancel = { selectedIds.clear() },
                    onExport = {
                        snack("Choose where to export the selected files.")
                        container.sensitiveKeyManager.expectingActivityResult = true
                        exportFolderLauncher.launch(Unit)
                    },
                    onRestore = {
                        val toRestore = selectedIds.toList()
                        scope.launch {
                            working = true
                            var restored = 0
                            withContext(Dispatchers.IO) {
                                toRestore.forEach { fileId ->
                                    if (repository.restoreToGallery(fileId)) restored += 1
                                }
                            }
                            selectedIds.clear()
                            working = false
                            snack(
                                if (restored == toRestore.size) {
                                    "$restored file(s) unlocked back to Gallery."
                                } else {
                                    "$restored of ${toRestore.size} file(s) unlocked back to Gallery."
                                },
                            )
                        }
                    },
                    onDelete = {
                        val toDelete = selectedIds.toList()
                        scope.launch {
                            working = true
                            withContext(Dispatchers.IO) {
                                toDelete.forEach { repository.delete(it) }
                            }
                            selectedIds.clear()
                            working = false
                            snack("${toDelete.size} file(s) deleted from the vault.")
                        }
                    },
                    actionsEnabled = !working,
                )
            } else {
                AppTopBar(title = "File Vault", onBack = onBack)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!selectionMode) {
                AppExtendedFab(
                    icon = Icons.Rounded.Lock,
                    text = if (working) "Working…" else "Add files",
                    onClick = {
                        if (!working) {
                            container.sensitiveKeyManager.expectingActivityResult = true
                            pickMediaLauncher.launch(Unit)
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.md, end = Spacing.md,
                    top = innerPadding.calculateTopPadding() + Spacing.sm, bottom = Spacing.xxl + 72.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            StatTile(
                                label = "Protected files",
                                value = "${files.size}",
                                icon = Icons.Rounded.Shield,
                                accent = MaterialTheme.extendedColors.accents.purple,
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Total size",
                                value = totalBytes.toReadableSize(),
                                icon = Icons.Rounded.FolderOpen,
                                accent = MaterialTheme.extendedColors.accents.blue,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                        SecurityCallout()
                        Spacer(Modifier.height(Spacing.md))
                    }
                }

                if (files.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            icon = Icons.Rounded.Shield,
                            title = "No protected files",
                            description = "Tap \"Add files\" to encrypt photos and videos into your vault.",
                            modifier = Modifier.padding(top = Spacing.lg),
                        )
                    }
                } else {
                    items(files, key = { it.fileId }) { file ->
                        VaultFileTile(
                            file = file,
                            repository = repository,
                            selected = selectedIds.contains(file.fileId),
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    if (selectedIds.contains(file.fileId)) selectedIds.remove(file.fileId)
                                    else selectedIds.add(file.fileId)
                                } else {
                                    previewItem = FileLockerPreviewItem(
                                        fileId = file.fileId,
                                        name = file.name,
                                        mimeType = file.mimeType,
                                    )
                                }
                            },
                            onLongClick = {
                                if (!selectedIds.contains(file.fileId)) selectedIds.add(file.fileId)
                            },
                        )
                    }
                }
            }

            if (working) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SecurityCallout() {
    AppCard(contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "Files are encrypted (AES-256) and stored only inside this app. " +
                    "Other apps, file managers, and USB cannot read them. Export a file to use it elsewhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
            )
        }
    }
}

@Composable
private fun SelectionTopBar(
    count: Int,
    onCancel: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    actionsEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.extendedColors.card)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(AppTopBarHeight)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "$count selected",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete, enabled = actionsEnabled) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.width(Spacing.xs))
        SecondaryButton(
            text = "Unlock",
            enabled = actionsEnabled,
            leadingIcon = { Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp)) },
            onClick = onRestore,
        )
        Spacer(Modifier.width(Spacing.xs))
        SecondaryButton(
            text = "Export",
            enabled = actionsEnabled,
            leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) },
            onClick = onExport,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultFileTile(
    file: VaultFile,
    repository: VaultFileRepository,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var thumb by remember(file.fileId) { mutableStateOf<ImageBitmap?>(null) }

    // Decrypt a downsampled thumbnail to RAM only. Videos show a placeholder.
    LaunchedEffectThumb(file, repository) { thumb = it }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.extendedColors.inputField)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                else Modifier,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val bmp = thumb
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    if (file.isVideo) Icons.Rounded.PlayCircle else Icons.Rounded.Image,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        if (file.isVideo && bmp != null) {
            Icon(
                Icons.Rounded.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
        }

        if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .size(22.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LaunchedEffectThumb(
    file: VaultFile,
    repository: VaultFileRepository,
    onLoaded: (ImageBitmap?) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(file.fileId) {
        if (!file.isImage) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                repository.openDecryptedStream(file.fileId)?.use { input ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(input, null, opts)?.asImageBitmap()
                }
            }.getOrNull()
        }
        onLoaded(bitmap)
    }
}

private class HideableMediaPickerContract : ActivityResultContract<Unit, List<Uri>>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK) return emptyList()
        val data = intent ?: return emptyList()
        val clipData = data.clipData
        if (clipData != null) {
            return List(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
        }
        return data.data?.let(::listOf).orEmpty()
    }
}

private class DestinationFolderPickerContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}

private suspend fun exportFiles(
    context: Context,
    repository: VaultFileRepository,
    files: List<VaultFile>,
    destinationTreeUri: Uri,
): Int {
    val resolver = context.contentResolver
    val treeDocumentId = DocumentsContract.getTreeDocumentId(destinationTreeUri)
    val destinationDocumentUri = DocumentsContract.buildDocumentUriUsingTree(destinationTreeUri, treeDocumentId)
    var exported = 0
    files.forEach { file ->
        runCatching {
            val outputUri = DocumentsContract.createDocument(
                resolver,
                destinationDocumentUri,
                file.mimeType.ifBlank { "application/octet-stream" },
                file.name,
            ) ?: return@runCatching
            val out = resolver.openOutputStream(outputUri) ?: return@runCatching
            if (repository.exportTo(file.fileId, out)) exported += 1
        }
    }
    return exported
}

private fun Long.toReadableSize(): String {
    if (this <= 0L) return "Unknown size"
    val kb = this / 1024.0
    if (kb < 1024) return "${kb.toInt()} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${String.format("%.1f", mb)} MB"
    val gb = mb / 1024.0
    return "${String.format("%.1f", gb)} GB"
}
