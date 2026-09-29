package com.daykit.feature.imagetool.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.feature.imagetool.domain.CropRect
import com.daykit.feature.imagetool.domain.ImageMath
import com.daykit.feature.imagetool.domain.ImageOptions
import com.daykit.feature.imagetool.domain.ImageProcessor
import com.daykit.feature.imagetool.domain.ImageSource
import com.daykit.feature.imagetool.domain.OutputFormat
import com.daykit.feature.imagetool.domain.ProcessedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val DimensionPresets = listOf<Int?>(null, 640, 1080, 1600, 2048)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ImageToolScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }

    var uri by remember { mutableStateOf<Uri?>(null) }
    var source by remember { mutableStateOf<ImageSource?>(null) }
    var format by remember { mutableStateOf(OutputFormat.JPEG) }
    var maxDimension by remember { mutableStateOf<Int?>(null) }
    var targetKb by remember { mutableStateOf("") }
    var crop by remember { mutableStateOf<CropRect?>(null) }
    var cropPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ProcessedImage?>(null) }

    val targetBytes = targetKb.trim().toLongOrNull()?.takeIf { it > 0 }?.let { it * 1024 }
    val targetInvalid = targetKb.isNotBlank() && targetBytes == null

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { ImageProcessor.readSource(context, picked) } }
                .onSuccess { uri = picked; source = it; result = null; crop = null }
                .onFailure { message("Couldn't open that image") }
            busy = false
        }
    }

    val outputName = source?.let { "${it.name.substringBeforeLast('.')}-daykit.${format.extension}" } ?: "image.${format.extension}"
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mime)) { dest ->
        val bytes = result?.bytes ?: return@rememberLauncherForActivityResult
        if (dest == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(dest)!!.use { it.write(bytes) }
                }
            }.isSuccess
            message(if (ok) "Image saved" else "Couldn't save image")
        }
    }

    fun process() {
        val src = uri ?: return
        if (targetInvalid) return
        scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.Default) {
                    ImageProcessor.process(context, src, ImageOptions(format, maxDimension, targetBytes, crop))
                }
            }.onSuccess {
                result = it
                if (!it.targetMet) message("Couldn't reach the target size — this is the smallest possible")
            }.onFailure { message("Couldn't process this image") }
            busy = false
        }
    }

    cropPreview?.let { preview ->
        ImageCropDialog(
            image = preview,
            onDismiss = { cropPreview = null },
            onConfirm = { crop = it; result = null; cropPreview = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Image Tool", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                "Resize, compress to a file size, convert format and strip location data — all on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
            )
            SecondaryButton(
                text = if (source == null) "Choose image" else "Choose another image",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Image, null, Modifier.size(18.dp)) },
                onClick = {
                    container.sensitiveKeyManager.expectingActivityResult = true
                    pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )

            source?.let { src ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(src.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        "${src.width} × ${src.height} px · ${if (src.bytes > 0) ImageMath.formatSize(src.bytes) else "unknown size"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textMuted,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    SecondaryButton(
                        text = if (crop == null) "Crop" else "Adjust crop",
                        enabled = !busy,
                        leadingIcon = { Icon(Icons.Rounded.Crop, null, Modifier.size(18.dp)) },
                        onClick = {
                            val src = uri ?: return@SecondaryButton
                            scope.launch {
                                busy = true
                                runCatching {
                                    withContext(Dispatchers.IO) { ImageProcessor.loadPreview(context, src).asImageBitmap() }
                                }.onSuccess { cropPreview = it }.onFailure { message("Couldn't open the crop editor") }
                                busy = false
                            }
                        },
                    )
                    if (crop != null) {
                        val (cw, ch) = ImageMath.croppedSize(src.width, src.height, crop)
                        Text("$cw × $ch px", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textMuted)
                        AppTextButton(text = "Reset", onClick = { crop = null; result = null })
                    }
                }

                SectionLabel("Format")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutputFormat.entries.forEach {
                        FilterChipButton(it.label, selected = format == it) { format = it; result = null }
                    }
                }

                SectionLabel("Longest side")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    DimensionPresets.forEach { d ->
                        FilterChipButton(d?.let { "$it px" } ?: "Original", selected = maxDimension == d) {
                            maxDimension = d; result = null
                        }
                    }
                }

                SectionLabel("Target file size (optional)")
                AppTextField(
                    value = targetKb,
                    onValueChange = { targetKb = it.filter(Char::isDigit).take(6); result = null },
                    placeholder = "e.g. 200",
                    isError = targetInvalid,
                    supportingText = if (format == OutputFormat.PNG) {
                        "PNG is lossless, so the image is scaled down to fit"
                    } else {
                        "Quality is lowered, then the image scaled down, until it fits (KB)"
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                PrimaryButton(
                    text = if (busy) "Processing…" else "Process image",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && !targetInvalid,
                    loading = busy,
                    onClick = ::process,
                )
            }

            result?.let { out ->
                val preview = remember(out) { BitmapFactory.decodeByteArray(out.bytes, 0, out.bytes.size)?.asImageBitmap() }
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    preview?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "Processed image preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                    Text(
                        "${out.width} × ${out.height} px · ${ImageMath.formatSize(out.bytes.size.toLong())}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Location and camera metadata removed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textMuted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PrimaryButton(
                        text = "Save",
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Rounded.Save, null, Modifier.size(18.dp)) },
                        onClick = {
                            container.sensitiveKeyManager.expectingActivityResult = true
                            saveLauncher.launch(outputName)
                        },
                    )
                    SecondaryButton(
                        text = "Share",
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)) },
                        onClick = {
                            scope.launch {
                                val shared = runCatching {
                                    withContext(Dispatchers.IO) { context.shareableImageUri(outputName, out.bytes) }
                                }.getOrNull()
                                if (shared == null) {
                                    message("Couldn't share image")
                                } else {
                                    container.sensitiveKeyManager.expectingActivityResult = true
                                    context.shareImage(shared, out.format.mime)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.extendedColors.textMuted)
}

private fun Context.shareableImageUri(fileName: String, bytes: ByteArray): Uri {
    val dir = File(cacheDir, "image-share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val file = File(dir, fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    file.writeBytes(bytes)
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
}

private fun Context.shareImage(uri: Uri, mime: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, "Share image"))
}
