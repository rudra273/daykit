package com.daykit.feature.filelocker.ui

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppExtendedFab
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.StatTile
import com.daykit.core.designsystem.extendedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HIDDEN_FOLDER_NAME = "DayKitFileLocker"
private const val HIDDEN_RELATIVE_PATH = "Documents/$HIDDEN_FOLDER_NAME/"
private const val LOCKED_EXTENSION = ".locked"
private const val GENERIC_MIME_TYPE = "application/octet-stream"

private data class SourceMedia(
    val uri: Uri,
    val name: String,
    val mimeType: String,
)

private data class HiddenMedia(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
)

private data class HideResult(
    val copied: Int,
    val deletedOriginals: Int,
    val failed: Int,
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileLockerScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedHiddenUris = remember { mutableStateListOf<Uri>() }
    var hiddenFiles by remember { mutableStateOf(emptyList<HiddenMedia>()) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<FileLockerPreviewItem?>(null) }

    val selectionMode = selectedHiddenUris.isNotEmpty()
    val gridState = rememberLazyGridState()
    val scrolledUnder by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 4 }
    }

    fun snack(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    previewItem?.let { item ->
        FileLockerPreviewScreen(
            item = item,
            onBack = { previewItem = null },
        )
        return
    }

    BackHandler(enabled = selectionMode) { selectedHiddenUris.clear() }

    fun refreshHiddenFiles() {
        scope.launch {
            loading = true
            hiddenFiles = withContext(Dispatchers.IO) { context.queryHiddenMedia() }
            selectedHiddenUris.removeAll { selected -> hiddenFiles.none { it.uri == selected } }
            loading = false
        }
    }

    val destinationFolderLauncher = rememberLauncherForActivityResult(DestinationFolderPickerContract()) { destinationUri ->
        if (destinationUri == null) {
            snack("Choose a destination folder to unhide selected files.")
            return@rememberLauncherForActivityResult
        }
        val selectedFiles = hiddenFiles.filter { selectedHiddenUris.contains(it.uri) }
        if (selectedFiles.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            val restored = withContext(Dispatchers.IO) {
                context.unhideMedia(selectedFiles, destinationUri)
            }
            hiddenFiles = withContext(Dispatchers.IO) { context.queryHiddenMedia() }
            selectedHiddenUris.clear()
            working = false
            snack("$restored file(s) restored to the selected folder.")
        }
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(HideableMediaPickerContract()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            val result = withContext(Dispatchers.IO) {
                context.hideMedia(uris)
            }
            hiddenFiles = withContext(Dispatchers.IO) { context.queryHiddenMedia() }
            selectedHiddenUris.clear()
            working = false
            snack(
                buildString {
                    append("${result.copied} file(s) hidden.")
                    if (result.failed > 0) append(" ${result.failed} failed.")
                    if (result.copied > result.deletedOriginals) {
                        append(" If an original still appears in Gallery, delete it manually.")
                    }
                },
            )
        }
    }

    LaunchedEffect(Unit) { refreshHiddenFiles() }

    val totalBytes = remember(hiddenFiles) { hiddenFiles.sumOf { it.sizeBytes } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    count = selectedHiddenUris.size,
                    onCancel = { selectedHiddenUris.clear() },
                    onRestore = {
                        snack("Choose where to restore selected files.")
                        destinationFolderLauncher.launch(Unit)
                    },
                    restoreEnabled = !working,
                )
            } else {
                AppTopBar(title = "File Vault", onBack = onBack, scrolledUnder = scrolledUnder)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!selectionMode) {
                AppExtendedFab(
                    icon = Icons.Rounded.Lock,
                    text = if (working) "Working…" else "Hide files",
                    onClick = { if (!working) pickMediaLauncher.launch(Unit) },
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
                                label = "Hidden files",
                                value = "${hiddenFiles.size}",
                                icon = Icons.Rounded.VisibilityOff,
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
                        DisclosureCallout()
                        Spacer(Modifier.height(Spacing.md))
                    }
                }

                if (loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else if (hiddenFiles.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            icon = Icons.Rounded.VisibilityOff,
                            title = "No hidden files",
                            description = "Tap \"Hide files\" to move photos and videos into your vault.",
                            modifier = Modifier.padding(top = Spacing.lg),
                        )
                    }
                } else {
                    items(hiddenFiles, key = { it.uri.toString() }) { file ->
                        HiddenMediaTile(
                            file = file,
                            selected = selectedHiddenUris.contains(file.uri),
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    if (selectedHiddenUris.contains(file.uri)) selectedHiddenUris.remove(file.uri)
                                    else selectedHiddenUris.add(file.uri)
                                } else {
                                    previewItem = file.toPreviewItem()
                                }
                            },
                            onLongClick = {
                                if (!selectedHiddenUris.contains(file.uri)) selectedHiddenUris.add(file.uri)
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
private fun DisclosureCallout() {
    AppCard(contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.VisibilityOff,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "Files are not encrypted, not saved in the app database, and not included in backup or restore.",
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
    onRestore: () -> Unit,
    restoreEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.extendedColors.barTint)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
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
        SecondaryButton(
            text = "Restore",
            enabled = restoreEnabled,
            leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) },
            onClick = onRestore,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HiddenMediaTile(
    file: HiddenMedia,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val isVideo = file.mimeType.startsWith("video/")
    var thumb by remember(file.uri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file.uri) {
        if (!isVideo) {
            thumb = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(file.uri)?.use { input ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        BitmapFactory.decodeStream(input, null, opts)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }

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
                    if (isVideo) Icons.Rounded.PlayCircle else Icons.Rounded.Image,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Video play badge (only when we have a thumbnail underneath)
        if (isVideo && bmp != null) {
            Icon(
                Icons.Rounded.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
            )
        }

        // Selection indicator
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

private fun Context.hideMedia(uris: List<Uri>): HideResult {
    var copied = 0
    var deleted = 0
    var failed = 0

    uris.forEach { uri ->
        runCatching {
            val source = getSourceMedia(uri)
            copyIntoMediaStore(
                source = source.asLockedFile(),
                relativePath = HIDDEN_RELATIVE_PATH,
            )
            copied += 1
            if (deleteOriginal(uri)) {
                deleted += 1
            }
        }.onFailure {
            failed += 1
        }
    }

    return HideResult(
        copied = copied,
        deletedOriginals = deleted,
        failed = failed,
    )
}

private fun Context.unhideMedia(files: List<HiddenMedia>, destinationTreeUri: Uri): Int {
    var restored = 0
    files.forEach { file ->
        runCatching {
            copyIntoPickedFolder(
                source = SourceMedia(
                    uri = file.uri,
                    name = file.name,
                    mimeType = file.name.inferMediaMimeType(),
                ),
                destinationTreeUri = destinationTreeUri,
            )
            contentResolver.delete(file.uri, null, null)
            restored += 1
        }
    }
    return restored
}

private fun Context.queryHiddenMedia(): List<HiddenMedia> {
    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.SIZE,
    )
    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
    val selectionArgs = arrayOf(HIDDEN_RELATIVE_PATH, "%$LOCKED_EXTENSION")

    return contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
        ?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            buildList {
                while (cursor.moveToNext()) {
                    val storedName = cursor.getString(nameIndex).orEmpty()
                    val originalName = storedName.removeLockedExtension()
                    val mimeType = cursor.getString(mimeIndex).orEmpty()
                    add(
                        HiddenMedia(
                            uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex)),
                            name = originalName,
                            mimeType = originalName.inferMediaMimeType().ifBlank { mimeType },
                            sizeBytes = cursor.getLong(sizeIndex),
                        ),
                    )
                }
            }
        }.orEmpty()
}

private fun Context.getSourceMedia(uri: Uri): SourceMedia {
    val mimeType = contentResolver.getType(uri).orEmpty()
    val fallbackName = if (mimeType.startsWith("video/")) "hidden_video" else "hidden_image"
    val name = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else {
            null
        }
    }?.takeIf { it.isNotBlank() } ?: fallbackName

    return SourceMedia(uri = uri, name = name, mimeType = mimeType.ifBlank { GENERIC_MIME_TYPE })
}

private fun Context.copyIntoMediaStore(
    source: SourceMedia,
    relativePath: String,
): Uri {
    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
        put(MediaStore.MediaColumns.MIME_TYPE, source.mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val outputUri = checkNotNull(contentResolver.insert(collection, values)) {
        "Could not create hidden file"
    }

    try {
        contentResolver.openInputStream(source.uri).use { input ->
            contentResolver.openOutputStream(outputUri).use { output ->
                checkNotNull(input) { "Could not open source file" }
                checkNotNull(output) { "Could not open destination file" }
                input.copyTo(output)
            }
        }
        contentResolver.update(
            outputUri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        return outputUri
    } catch (error: Throwable) {
        contentResolver.delete(outputUri, null, null)
        throw error
    }
}

private fun Context.copyIntoPickedFolder(
    source: SourceMedia,
    destinationTreeUri: Uri,
): Uri {
    val treeDocumentId = DocumentsContract.getTreeDocumentId(destinationTreeUri)
    val destinationDocumentUri = DocumentsContract.buildDocumentUriUsingTree(destinationTreeUri, treeDocumentId)
    val outputUri = checkNotNull(
        DocumentsContract.createDocument(
            contentResolver,
            destinationDocumentUri,
            source.mimeType.ifBlank { source.name.inferMediaMimeType().ifBlank { GENERIC_MIME_TYPE } },
            source.name,
        ),
    ) {
        "Could not create restored file"
    }
    try {
        contentResolver.openInputStream(source.uri).use { input ->
            contentResolver.openOutputStream(outputUri).use { output ->
                checkNotNull(input) { "Could not open hidden file" }
                checkNotNull(output) { "Could not open restore destination" }
                input.copyTo(output)
            }
        }
        return outputUri
    } catch (error: Throwable) {
        DocumentsContract.deleteDocument(contentResolver, outputUri)
        throw error
    }
}

private fun SourceMedia.asLockedFile(): SourceMedia {
    return copy(
        name = if (name.endsWith(LOCKED_EXTENSION, ignoreCase = true)) name else "$name$LOCKED_EXTENSION",
        mimeType = GENERIC_MIME_TYPE,
    )
}

private fun String.removeLockedExtension(): String {
    return if (endsWith(LOCKED_EXTENSION, ignoreCase = true)) {
        dropLast(LOCKED_EXTENSION.length)
    } else {
        this
    }
}

private fun HiddenMedia.toPreviewItem(): FileLockerPreviewItem {
    return FileLockerPreviewItem(
        uri = uri,
        name = name,
        mimeType = mimeType.ifBlank { name.inferMediaMimeType() },
    )
}

private fun String.inferMediaMimeType(): String {
    val extension = substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .takeIf { it.isNotBlank() }
        ?: return ""
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
}

private fun Context.deleteOriginal(uri: Uri): Boolean {
    return runCatching {
        DocumentsContract.deleteDocument(contentResolver, uri)
    }.getOrElse {
        runCatching {
            contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }
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
