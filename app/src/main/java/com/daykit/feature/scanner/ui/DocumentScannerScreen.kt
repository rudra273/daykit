@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.scanner.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.PictureAsPdf
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.extendedColors
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DocumentScannerScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingPdf by remember { mutableStateOf<Uri?>(null) }
    var scannerStarting by remember { mutableStateOf(false) }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { destination ->
        val source = pendingPdf
        pendingPdf = null
        if (destination == null || source == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(source)?.use { input ->
                        context.contentResolver.openOutputStream(destination, "w")?.use { output -> input.copyTo(output) }
                            ?: error("Cannot create destination")
                    } ?: error("Cannot read scan")
                }
            }
            message(if (saved.isSuccess) "Scanned PDF saved" else "Couldn't save scanned PDF")
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        scannerStarting = false
        if (activityResult.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        val uri = result?.pdf?.uri
        if (uri == null) {
            message("No scanned document returned")
        } else {
            pendingPdf = uri
            container.sensitiveKeyManager.expectingActivityResult = true
            savePdfLauncher.launch("scan-${System.currentTimeMillis()}.pdf")
        }
    }

    fun startScan() {
        val activity = context.findActivity()
        if (activity == null || scannerStarting) return
        scannerStarting = true
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerStarting = false
                container.sensitiveKeyManager.expectingActivityResult = true
                scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                scannerStarting = false
                message("Document scanner is unavailable")
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Document Scanner", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AccentIconTile(
                        icon = Icons.Rounded.DocumentScanner,
                        accent = MaterialTheme.extendedColors.accents.blue,
                        size = 64.dp,
                        iconSize = 34.dp,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    Text("Scan documents", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Capture up to 20 pages or import them from your gallery. Crop, rotate, and enhance pages before saving one PDF.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    PrimaryButton(
                        text = if (scannerStarting) "Opening scanner…" else "Scan document",
                        enabled = !scannerStarting,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp)) },
                        onClick = ::startScan,
                    )
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
