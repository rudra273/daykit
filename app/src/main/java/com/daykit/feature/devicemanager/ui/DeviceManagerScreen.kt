package com.daykit.feature.devicemanager.ui

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.feature.devicemanager.data.DeviceReader
import com.daykit.feature.devicemanager.data.DeviceSnapshot
import com.daykit.feature.devicemanager.data.FileInsight
import com.daykit.feature.devicemanager.data.AppInsight
import kotlinx.coroutines.launch

@Composable
fun DeviceManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<DeviceSnapshot?>(null) }
    var files by remember { mutableStateOf<List<FileInsight>?>(null) }
    var loadingFiles by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var leastCount by rememberSaveable { mutableStateOf("10") }
    var largestCount by rememberSaveable { mutableStateOf("10") }
    LaunchedEffect(refresh) {
        runCatching { DeviceReader.load(context) }
            .onSuccess { snapshot = it; error = null }
            .onFailure { error = "Could not load device information." }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            loadingFiles = true
            // A selected folder grants access to its children for this scan.
            scope.launch {
                runCatching { DeviceReader.largestFiles(context, uri) }
                    .onSuccess { files = it; error = null }
                    .onFailure { error = "Could not scan that folder." }
                loadingFiles = false
            }
        }
    }
    Scaffold(topBar = {
        AppTopBar(title = "Device Manager", onBack = onBack, actions = {
            IconButton(onClick = { refresh++ }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            val data = snapshot
            if (data == null) LoadingIndicator() else {
                Section("Battery") {
                    Detail("Charge", data.batteryLevel?.let { "$it%" } ?: "Unavailable")
                    LinearProgressIndicator(
                        progress = { (data.batteryLevel ?: 0) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Detail("Status", if (data.charging) "Charging" else "Not charging")
                    Detail("Health", data.batteryHealth)
                    Text("Health is the status reported by Android, not an estimated battery capacity.", style = MaterialTheme.typography.bodySmall)
                }
                Section("Battery use by app") {
                    Text("Android does not share reliable per-app battery percentages with regular apps. Check your device's Battery usage screen for measured results.", style = MaterialTheme.typography.bodySmall)
                    val batteryIntent = Intent("android.intent.action.POWER_USAGE_SUMMARY")
                    if (batteryIntent.resolveActivity(context.packageManager) != null) {
                        Button(onClick = { context.startActivity(batteryIntent) }) { Text("Open battery usage") }
                    } else {
                        Text("Open Settings → Battery → Battery usage.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Section("Internal storage") {
                    Detail("Used", Formatter.formatFileSize(context, data.storageTotal - data.storageFree))
                    Detail("Free", Formatter.formatFileSize(context, data.storageFree))
                    Detail("Total", Formatter.formatFileSize(context, data.storageTotal))
                    LinearProgressIndicator(
                        progress = { if (data.storageTotal > 0) (data.storageTotal - data.storageFree).toFloat() / data.storageTotal else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!data.usageAccess) {
                    Section("App insights") {
                        Text("Allow Usage Access to see least recently used apps and app sizes.")
                        Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                            Text("Grant Usage Access")
                        }
                    }
                } else {
                    Section("Most used apps · last 7 days") {
                        if (data.mostUsed.isEmpty()) Text("No usage time is available yet.")
                        val maxTime = data.mostUsed.firstOrNull()?.foregroundMillis7d ?: 1L
                        data.mostUsed.forEachIndexed { index, app ->
                            AppBarRow(index + 1, app, formatDuration(app.foregroundMillis7d), app.foregroundMillis7d.toFloat() / maxTime)
                        }
                    }
                    Section("Least recently used apps") {
                        Text("Based on up to 90 days of usage history. “No recent use” may also mean Android has no retained history.", style = MaterialTheme.typography.bodySmall)
                        CountField(leastCount, { leastCount = it }, data.leastUsed.size)
                        data.leastUsed.take(leastCount.toIntOrNull()?.coerceAtLeast(1) ?: 10).forEachIndexed { index, app ->
                            val whenUsed = if (app.lastUsed == 0L) "No recent use" else DateUtils.getRelativeTimeSpanString(app.lastUsed).toString()
                            AppRow(index + 1, app, whenUsed)
                        }
                    }
                    Section("Largest apps") {
                        if (data.largestApps.isEmpty()) Text("App sizes are unavailable on this device.")
                        CountField(largestCount, { largestCount = it }, data.largestApps.size)
                        val maxSize = data.largestApps.firstOrNull()?.bytes ?: 1L
                        data.largestApps.take(largestCount.toIntOrNull()?.coerceAtLeast(1) ?: 10).forEachIndexed { index, app ->
                            AppBarRow(index + 1, app, Formatter.formatFileSize(context, app.bytes ?: 0), (app.bytes ?: 0).toFloat() / maxSize)
                        }
                    }
                }
                Section("Largest files") {
                    Text("Choose a folder to scan its files. Android does not allow an automatic search of every folder.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { folderPicker.launch(null) }) { Text("Choose folder") }
                    if (loadingFiles) LoadingIndicator()
                    if (files?.isEmpty() == true) Text("No files found in that folder.")
                    files?.forEachIndexed { index, file ->
                        Detail("${index + 1}. ${file.path}", Formatter.formatFileSize(context, file.bytes))
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        content()
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(Spacing.xs))
}

@Composable
private fun CountField(value: String, onChange: (String) -> Unit, available: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.length <= 4 && input.all(Char::isDigit)) onChange(input) },
        label = { Text("Show how many") },
        supportingText = { Text("$available apps available") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AppRow(rank: Int, app: AppInsight, detail: String) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(48, 48).asImageBitmap() }.getOrNull()
    }
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank.", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp))
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppBarRow(rank: Int, app: AppInsight, detail: String, fraction: Float) {
    AppRow(rank, app, detail)
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().padding(start = 72.dp, bottom = Spacing.sm),
    )
}

private fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    val hours = minutes / 60
    return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
}
