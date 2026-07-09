@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.editor.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBackButton
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.extendedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private enum class SaveTarget {
    TextFile,
    Pdf,
    ImagesPdf,
}

@Composable
fun EditorScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var fileName by remember { mutableStateOf("untitled.txt") }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var content by remember { mutableStateOf("") }
    var isDirty by remember { mutableStateOf(false) }
    var pendingSaveTarget by remember { mutableStateOf(SaveTarget.TextFile) }
    var pendingImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun writeTextTo(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open file")
                }
            }
            result.onSuccess {
                fileName = context.displayName(uri) ?: fileName
                fileUri = uri
                isDirty = false
                showMessage("Saved")
            }.onFailure {
                showMessage("Save failed")
            }
        }
    }

    fun writePdfTo(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        val pdf = createPdfBytes(content)
                        try {
                            pdf.writeTo(output)
                        } finally {
                            pdf.close()
                        }
                    } ?: error("Cannot create PDF")
                }
            }
            result.onSuccess { showMessage("PDF exported") }
                .onFailure { showMessage("PDF export failed") }
        }
    }

    fun writeImagesPdfTo(uri: Uri, imageUris: List<Uri>) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        val pdf = createPdfFromImages(context, imageUris)
                        try {
                            pdf.writeTo(output)
                        } finally {
                            pdf.close()
                        }
                    } ?: error("Cannot create PDF")
                }
            }
            result.onSuccess { showMessage("Image PDF exported") }
                .onFailure { showMessage("Image PDF failed") }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        takePersistableWritePermission(context, uri)
        when (pendingSaveTarget) {
            SaveTarget.TextFile -> writeTextTo(uri)
            SaveTarget.Pdf -> writePdfTo(uri)
            SaveTarget.ImagesPdf -> writeImagesPdfTo(uri, pendingImageUris)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    takePersistableReadWritePermission(context, uri)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Cannot open file")
                    val name = context.displayName(uri) ?: "opened.txt"
                    name to bytes.toString(Charsets.UTF_8)
                }
            }
            result.onSuccess { (name, text) ->
                fileName = name
                fileUri = uri
                content = text
                isDirty = false
                showMessage("Opened")
            }.onFailure {
                showMessage("Open failed")
            }
        }
    }

    val pickImagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        pendingImageUris = uris
        pendingSaveTarget = SaveTarget.ImagesPdf
        createDocumentLauncher.launch("images.pdf")
    }

    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }

    val wordCount = remember(content) {
        if (content.isBlank()) 0 else content.trim().split(Regex("\\s+")).size
    }

    fun newDocument() {
        fileName = "untitled.txt"
        fileUri = null
        content = ""
        isDirty = false
    }

    fun saveCurrent() {
        val uri = fileUri
        if (uri != null) writeTextTo(uri)
        else {
            pendingSaveTarget = SaveTarget.TextFile
            createDocumentLauncher.launch(fileName.cleanFileName("untitled.txt"))
        }
    }

    fun share() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { context.shareableTextUri(fileName.cleanFileName("untitled.txt"), content) }
            }
            result.onSuccess { context.shareFile(it, fileName.cleanFileName("untitled.txt")) }
                .onFailure { showMessage("Share failed") }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Document top bar: back, filename + unsaved dot, save, share, overflow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
                    .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBackButton(onClick = onBack)
                Spacer(Modifier.width(Spacing.sm))
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { renameOpen = true },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isDirty) {
                            Spacer(Modifier.width(Spacing.sm))
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                        }
                    }
                    Text(
                        text = if (isDirty) "Unsaved changes" else "Saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.extendedColors.textMuted,
                    )
                }
                IconButton(onClick = { saveCurrent() }) {
                    Icon(Icons.Rounded.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { share() }) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Borderless full-bleed body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Spacing.lg)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (content.isEmpty()) {
                    Text(
                        text = "Start writing…",
                        color = MaterialTheme.extendedColors.textMuted,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                    )
                }
                BasicTextField(
                    value = content,
                    onValueChange = {
                        content = it
                        isDirty = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }

            // Word/char footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$wordCount words · ${content.length} chars",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.xl),
        )
    }

    if (menuOpen) {
        AppBottomSheet(onDismissRequest = { menuOpen = false }) {
            Text(
                text = "Document",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            AppListRow(
                headline = "New document",
                leadingIcon = Icons.Rounded.Add,
                onClick = { menuOpen = false; newDocument() },
            )
            AppListRow(
                headline = "Open…",
                leadingIcon = Icons.Rounded.FileOpen,
                onClick = {
                    menuOpen = false
                    openDocumentLauncher.launch(arrayOf("text/*", "application/json", "text/csv", "application/xml", "application/javascript"))
                },
            )
            AppListRow(
                headline = "Save as…",
                leadingIcon = Icons.Rounded.SaveAs,
                onClick = {
                    menuOpen = false
                    pendingSaveTarget = SaveTarget.TextFile
                    createDocumentLauncher.launch(fileName.cleanFileName("untitled.txt"))
                },
            )
            AppListRow(
                headline = "Export as PDF",
                leadingIcon = Icons.Rounded.PictureAsPdf,
                onClick = {
                    menuOpen = false
                    pendingSaveTarget = SaveTarget.Pdf
                    createDocumentLauncher.launch(fileName.withExtension("pdf"))
                },
            )
            AppListRow(
                headline = "Rename",
                leadingIcon = Icons.Rounded.DriveFileRenameOutline,
                onClick = { menuOpen = false; renameOpen = true },
            )
            AppListRow(
                headline = "Images to PDF",
                leadingIcon = Icons.Rounded.Image,
                onClick = {
                    menuOpen = false
                    pickImagesLauncher.launch("image/*")
                },
            )
            Spacer(Modifier.height(Spacing.sm))
        }
    }

    if (renameOpen) {
        var draft by remember { mutableStateOf(fileName) }
        AppBottomSheet(onDismissRequest = { renameOpen = false }) {
            Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg)) {
                Text(
                    text = "File name",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.lg))
                com.daykit.core.designsystem.components.AppTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "File name",
                    placeholder = "untitled.txt",
                )
                Spacer(Modifier.height(Spacing.lg))
                com.daykit.core.designsystem.components.PrimaryButton(
                    text = "Done",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = draft.isNotBlank(),
                    onClick = {
                        fileName = draft
                        isDirty = true
                        renameOpen = false
                    },
                )
            }
        }
    }
}

private fun Context.displayName(uri: Uri): String? {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else {
            null
        }
    }
}

private fun takePersistableReadWritePermission(context: Context, uri: Uri) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        .recoverCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}

private fun takePersistableWritePermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
}

private fun String.cleanFileName(fallback: String): String {
    val clean = trim().replace(Regex("""[/\\:*?"<>|]"""), "_")
    return clean.ifBlank { fallback }
}

private fun String.withExtension(extension: String): String {
    val clean = cleanFileName("untitled.txt")
    val base = clean.substringBeforeLast('.', clean)
    return "$base.$extension"
}

private fun createPdfBytes(text: String): PdfDocument {
    val pdf = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 42f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 12f
        typeface = Typeface.MONOSPACE
    }
    val lineHeight = (paint.fontSpacing * 1.25f).roundToInt().coerceAtLeast(18)
    val maxLineWidth = pageWidth - margin * 2
    val lines = text.ifEmpty { " " }.lineSequence().flatMap { it.wrapForPdf(paint, maxLineWidth).asSequence() }.toList()
    var lineIndex = 0
    var pageNumber = 1
    while (lineIndex < lines.size) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var y = margin + lineHeight
        while (lineIndex < lines.size && y < pageHeight - margin) {
            page.canvas.drawText(lines[lineIndex], margin, y, paint)
            y += lineHeight
            lineIndex++
        }
        pdf.finishPage(page)
        pageNumber++
    }
    return pdf
}

private fun createPdfFromImages(context: Context, imageUris: List<Uri>): PdfDocument {
    val pdf = PdfDocument()
    imageUris.forEachIndexed { index, uri ->
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return@forEachIndexed
        try {
            val portrait = bitmap.height >= bitmap.width
            val pageWidth = if (portrait) 595 else 842
            val pageHeight = if (portrait) 842 else 595
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create())
            val target = bitmap.fitRect(pageWidth.toFloat(), pageHeight.toFloat(), 28f)
            page.canvas.drawColor(android.graphics.Color.WHITE)
            page.canvas.drawBitmap(bitmap, null, target, Paint(Paint.ANTI_ALIAS_FLAG))
            pdf.finishPage(page)
        } finally {
            bitmap.recycle()
        }
    }
    if (imageUris.isEmpty()) {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        page.canvas.drawColor(android.graphics.Color.WHITE)
        pdf.finishPage(page)
    }
    return pdf
}

private fun Bitmap.fitRect(pageWidth: Float, pageHeight: Float, margin: Float): RectF {
    val maxWidth = pageWidth - margin * 2
    val maxHeight = pageHeight - margin * 2
    val scale = minOf(maxWidth / width, maxHeight / height)
    val drawWidth = width * scale
    val drawHeight = height * scale
    val left = (pageWidth - drawWidth) / 2f
    val top = (pageHeight - drawHeight) / 2f
    return RectF(left, top, left + drawWidth, top + drawHeight)
}

private fun String.wrapForPdf(paint: Paint, maxWidth: Float): List<String> {
    if (isBlank()) return listOf("")
    val output = mutableListOf<String>()
    var remaining = this
    while (remaining.isNotEmpty()) {
        val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
        output += remaining.take(count)
        remaining = remaining.drop(count)
    }
    return output
}

private fun Context.shareableTextUri(fileName: String, content: String): Uri {
    val dir = File(cacheDir, "editor-share").apply { mkdirs() }
    val file = File(dir, fileName.cleanFileName("shared.txt"))
    file.writeText(content)
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
}

private fun Context.shareFile(uri: Uri, fileName: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeTypeForFileName(fileName)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, "Share file"))
}

private fun mimeTypeForFileName(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "txt" -> "text/plain"
    "md", "markdown" -> "text/markdown"
    "csv" -> "text/csv"
    "json" -> "application/json"
    "xml" -> "application/xml"
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js" -> "application/javascript"
    else -> "text/plain"
}
