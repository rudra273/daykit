package com.rudra.daykit.feature.editor.ui

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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rudra.daykit.core.ui.AppBackButton
import com.rudra.daykit.core.ui.Cyan
import com.rudra.daykit.core.ui.DeepBackground
import com.rudra.daykit.core.ui.GlassBackground
import com.rudra.daykit.core.ui.MutedText
import com.rudra.daykit.core.ui.PanelAlt
import com.rudra.daykit.core.ui.SoftText
import com.rudra.daykit.core.ui.Stroke
import com.rudra.daykit.core.ui.glassSurface
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

    BackHandler { onBack() }

    GlassBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppBackButton(onClick = onBack)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Editor",
                            color = SoftText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (isDirty) "Unsaved changes" else "User-owned files",
                            color = if (isDirty) Cyan else MutedText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    EditorActionButton(Icons.Rounded.Add, "New") {
                        fileName = "untitled.txt"
                        fileUri = null
                        content = ""
                        isDirty = false
                    }
                    EditorActionButton(Icons.Rounded.FileOpen, "Open") {
                        openDocumentLauncher.launch(arrayOf("text/*", "application/json", "text/csv", "application/xml", "application/javascript"))
                    }
                }

                OutlinedTextField(
                    value = fileName,
                    onValueChange = {
                        fileName = it
                        isDirty = true
                    },
                    label = { Text("File name", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = Stroke,
                        focusedLabelColor = Cyan,
                        unfocusedLabelColor = MutedText,
                        cursorColor = Cyan,
                        focusedTextColor = SoftText,
                        unfocusedTextColor = SoftText,
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EditorCommandButton(
                        icon = Icons.Rounded.Save,
                        text = "Save",
                        modifier = Modifier.weight(1f),
                    ) {
                        val uri = fileUri
                        if (uri != null) {
                            writeTextTo(uri)
                        } else {
                            showMessage("Use Save as to choose a file first")
                        }
                    }
                    EditorCommandButton(
                        icon = Icons.Rounded.SaveAs,
                        text = "Save as",
                        modifier = Modifier.weight(1f),
                    ) {
                        pendingSaveTarget = SaveTarget.TextFile
                        createDocumentLauncher.launch(fileName.cleanFileName("untitled.txt"))
                    }
                    EditorCommandButton(
                        icon = Icons.Rounded.PictureAsPdf,
                        text = "PDF",
                        modifier = Modifier.weight(1f),
                    ) {
                        pendingSaveTarget = SaveTarget.Pdf
                        createDocumentLauncher.launch(fileName.withExtension("pdf"))
                    }
                    EditorCommandButton(
                        icon = Icons.Rounded.Share,
                        text = "Share",
                        modifier = Modifier.weight(1f),
                    ) {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { context.shareableTextUri(fileName.cleanFileName("untitled.txt"), content) }
                            }
                            result.onSuccess { context.shareFile(it, fileName.cleanFileName("untitled.txt")) }
                                .onFailure { showMessage("Share failed") }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EditorCommandButton(
                        icon = Icons.Rounded.Image,
                        text = "Images to PDF",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        pickImagesLauncher.launch("image/*")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .glassSurface(RoundedCornerShape(18.dp), selected = false)
                        .clip(RoundedCornerShape(18.dp))
                        .background(DeepBackground.copy(alpha = 0.72f))
                        .padding(14.dp),
                ) {
                    if (content.isEmpty()) {
                        Text(
                            text = "Write anything here...",
                            color = MutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    BasicTextField(
                        value = content,
                        onValueChange = {
                            content = it
                            isDirty = true
                        },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = TextStyle(
                            color = SoftText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        cursorBrush = SolidColor(Cyan),
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun EditorActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = Cyan, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EditorCommandButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .glassSurface(RoundedCornerShape(12.dp), selected = false)
            .clip(RoundedCornerShape(12.dp))
            .background(PanelAlt.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            color = SoftText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
