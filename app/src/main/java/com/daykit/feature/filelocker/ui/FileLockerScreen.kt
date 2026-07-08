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
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daykit.core.ui.AppBackButton
import com.daykit.core.ui.Cyan
import com.daykit.core.ui.GlassBackground
import com.daykit.core.ui.GlassLoadingIndicator
import com.daykit.core.ui.MutedText
import com.daykit.core.ui.PrimaryButton
import com.daykit.core.ui.SecondaryButton
import com.daykit.core.ui.SoftText
import com.daykit.core.ui.glassSurface
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

@Composable
fun FileLockerScreen(
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedHiddenUris = remember { mutableStateListOf<Uri>() }
    var hiddenFiles by remember { mutableStateOf(emptyList<HiddenMedia>()) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<FileLockerPreviewItem?>(null) }
    var message by remember {
        mutableStateOf("Hidden files stay in Documents/$HIDDEN_FOLDER_NAME and are not encrypted.")
    }

    previewItem?.let { item ->
        FileLockerPreviewScreen(
            item = item,
            onBack = { previewItem = null },
        )
        return
    }

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
            message = "Choose a destination folder to unhide selected files."
            return@rememberLauncherForActivityResult
        }
        val selectedFiles = hiddenFiles.filter { selectedHiddenUris.contains(it.uri) }
        if (selectedFiles.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            message = "Unhiding ${selectedFiles.size} file(s)..."
            val restored = withContext(Dispatchers.IO) {
                context.unhideMedia(selectedFiles, destinationUri)
            }
            hiddenFiles = withContext(Dispatchers.IO) { context.queryHiddenMedia() }
            selectedHiddenUris.clear()
            working = false
            message = "$restored file(s) restored to the selected folder."
        }
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(HideableMediaPickerContract()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            working = true
            message = "Hiding ${uris.size} selected file(s)..."
            val result = withContext(Dispatchers.IO) {
                context.hideMedia(uris)
            }
            hiddenFiles = withContext(Dispatchers.IO) { context.queryHiddenMedia() }
            selectedHiddenUris.clear()
            working = false
            message = buildString {
                append("${result.copied} file(s) copied to hidden folder.")
                if (result.deletedOriginals > 0) append(" ${result.deletedOriginals} original(s) removed.")
                if (result.failed > 0) append(" ${result.failed} file(s) could not be hidden.")
                if (result.copied > result.deletedOriginals) {
                    append(" If any original still appears in Gallery, delete that original manually.")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshHiddenFiles()
    }

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppBackButton(onClick = onBack)
                    Text(
                        text = "File Vault",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Files are not encrypted, not saved in the app database, and not included in backup or restore.",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    PrimaryButton(
                        text = if (working) "Working" else "Select Images / Videos",
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        },
                        onClick = { pickMediaLauncher.launch(Unit) },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Hidden files",
                        color = SoftText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { refreshHiddenFiles() },
                        enabled = !working,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = SoftText, modifier = Modifier.size(20.dp))
                    }
                }

                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlassLoadingIndicator(delayMillis = 0)
                    }
                } else if (hiddenFiles.isEmpty()) {
                    GlassPanel(selected = false) {
                        Text(
                            text = "No hidden files found.",
                            color = MutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    hiddenFiles.forEach { file ->
                        HiddenFileRow(
                            file = file,
                            selected = selectedHiddenUris.contains(file.uri),
                            onPreview = {
                                previewItem = file.toPreviewItem()
                            },
                            onSelectedChange = { selected ->
                                if (selected) {
                                    if (!selectedHiddenUris.contains(file.uri)) selectedHiddenUris.add(file.uri)
                                } else {
                                    selectedHiddenUris.remove(file.uri)
                                }
                            },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            text = if (selectedHiddenUris.size == hiddenFiles.size) "Clear" else "Select All",
                            enabled = !working,
                            onClick = {
                                if (selectedHiddenUris.size == hiddenFiles.size) {
                                    selectedHiddenUris.clear()
                                } else {
                                    selectedHiddenUris.clear()
                                    selectedHiddenUris.addAll(hiddenFiles.map { it.uri })
                                }
                            },
                        )
                        PrimaryButton(
                            text = if (working) "Restoring" else "Choose Folder",
                            enabled = selectedHiddenUris.isNotEmpty() && !working,
                            leadingIcon = {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            },
                            onClick = {
                                message = "Choose where to restore selected files."
                                destinationFolderLauncher.launch(Unit)
                            },
                        )
                    }
                }

                Text(
                    text = message,
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HiddenFileRow(
    file: HiddenMedia,
    selected: Boolean,
    onPreview: () -> Unit,
    onSelectedChange: (Boolean) -> Unit,
) {
    FileRowPanel(selected = selected) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable { onSelectedChange(!selected) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                modifier = Modifier.size(36.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = Cyan,
                    uncheckedColor = MutedText,
                    checkmarkColor = Color.Black,
                ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = SoftText,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${file.mimeType.ifBlank { "media file" }} - ${file.sizeBytes.toReadableSize()}",
                    color = MutedText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            SecondaryButton(
                text = "View",
                textStyle = MaterialTheme.typography.labelMedium,
                leadingIcon = {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                },
                onClick = onPreview,
            )
        }
    }
}

@Composable
private fun FileRowPanel(
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(12.dp),
                selected = selected,
                tintStrength = 0.05f,
                shadowElevation = 1f,
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        content()
    }
}

@Composable
private fun GlassPanel(
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(18.dp),
                selected = selected,
                tintStrength = 0.08f,
                shadowElevation = 2f,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        content()
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
