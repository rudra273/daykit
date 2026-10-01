@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.settings.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daykit.AppContainer
import com.daykit.core.data.AppPreferences
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.security.label
import com.daykit.feature.settings.data.DataExports
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Item counts per tool; null while loading or when a tool can't be read. */
private data class StorageStats(
    val keyStoreEntries: Int?,
    val notes: Int?,
    val noteImages: Int?,
    val vaultFiles: Int?,
    val vaultBytes: Long?,
    val expenses: Int?,
    val habits: Int?,
    val reminders: Int?,
    val databaseBytes: Long,
)

@Composable
fun DataStorageScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accents = MaterialTheme.extendedColors.accents
    var stats by remember { mutableStateOf<StorageStats?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.IO) { loadStats(container, context) }
    }

    fun writeExport(uri: Uri, what: String, produce: suspend () -> Pair<String, Int>) {
        scope.launch {
            busy = true
            runCatching {
                val (text, count) = produce()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                        ?: error("Could not open the file")
                }
                count
            }.onSuccess { count ->
                message = "Exported $count $what."
            }.onFailure { error ->
                message = "Export failed: ${error.message ?: "unknown error"}"
            }
            busy = false
        }
    }

    val expensesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            writeExport(uri, "expenses") {
                val entries = container.expenseRepository.observeAllEntries().first()
                DataExports.expensesCsv(entries, AppPreferences.currencyCode) to entries.size
            }
        }
    }
    val notesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        // Notes are decrypted with the MSK, which may have been wiped while the
        // picker was open; the write resumes after the user unlocks again.
        if (uri != null) {
            container.sensitiveKeyManager.runWhenUnlocked {
                writeExport(uri, "notes") {
                    val notes = container.secureNoteRepository.observeNotes().first()
                    DataExports.notesMarkdown(notes) to notes.size
                }
            }
        }
    }

    // Notes leave the encrypted store as readable text, so confirm with the PIN first.
    val notesExport = rememberPinGatedChange<Unit>(
        container = container,
        title = "Export notes",
        message = {
            "Your notes will be saved as a readable, unencrypted file. " +
                "Enter your master ${container.credentialRepository.credentialKind().label} to continue."
        },
        apply = {
            container.sensitiveKeyManager.expectingActivityResult = true
            notesLauncher.launch("daykit-notes-${LocalDate.now()}.md")
        },
    )

    SettingsSubPage(title = "Data & Storage", onBack = onBack) {
        item { SectionHeader("Storage", topPadding = 0.dp) }
        item {
            val s = stats
            AppCard(contentPadding = PaddingValues(0.dp)) {
                StorageRow("Key Store", Icons.Rounded.VpnKey, accents.indigo, s?.keyStoreEntries?.let { count(it, "entry", "entries") })
                RowDivider(startIndent = Spacing.lg)
                StorageRow(
                    "Notes", Icons.Rounded.Notes, accents.teal,
                    s?.notes?.let { notes ->
                        count(notes, "note", "notes") + (s.noteImages?.takeIf { it > 0 }?.let { " · ${count(it, "image", "images")}" } ?: "")
                    },
                )
                RowDivider(startIndent = Spacing.lg)
                StorageRow(
                    "File Vault", Icons.Rounded.Folder, accents.purple,
                    s?.vaultFiles?.let { files ->
                        count(files, "file", "files") + (s.vaultBytes?.let { " · ${Formatter.formatShortFileSize(context, it)}" } ?: "")
                    },
                )
                RowDivider(startIndent = Spacing.lg)
                StorageRow("Expenses", Icons.Rounded.Payments, accents.pink, s?.expenses?.let { count(it, "entry", "entries") })
                RowDivider(startIndent = Spacing.lg)
                StorageRow("Habits", Icons.Rounded.TrackChanges, accents.green, s?.habits?.let { count(it, "habit", "habits") })
                RowDivider(startIndent = Spacing.lg)
                StorageRow("Reminders", Icons.Rounded.NotificationsActive, accents.orange, s?.reminders?.let { count(it, "reminder", "reminders") })
                RowDivider(startIndent = Spacing.lg)
                StorageRow(
                    "Encrypted Database", Icons.Rounded.Storage, accents.blue,
                    s?.let { Formatter.formatShortFileSize(context, it.databaseBytes) },
                )
            }
        }

        item { SectionHeader("Export") }
        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                AppListRow(
                    headline = "Export Expenses",
                    supporting = "CSV for spreadsheets",
                    leadingIcon = Icons.Rounded.TableChart,
                    leadingAccent = accents.pink,
                    enabled = !busy,
                    trailing = { NavChevron() },
                    onClick = {
                        message = null
                        container.sensitiveKeyManager.expectingActivityResult = true
                        expensesLauncher.launch("daykit-expenses-${LocalDate.now()}.csv")
                    },
                )
                RowDivider(startIndent = Spacing.lg)
                AppListRow(
                    headline = "Export Notes",
                    supporting = "Markdown text, without images",
                    leadingIcon = Icons.Rounded.Notes,
                    leadingAccent = accents.teal,
                    enabled = !busy,
                    trailing = { NavChevron() },
                    onClick = {
                        message = null
                        notesExport.request(Unit, true)
                    },
                )
            }
        }
        item {
            SettingsFootnote(
                message ?: "Exports are plain files anyone can read. For a full, encrypted copy use Backup & Restore.",
            )
        }
    }
}

@Composable
private fun StorageRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    value: String?,
) {
    AppListRow(
        headline = title,
        supporting = value ?: "…",
        leadingIcon = icon,
        leadingAccent = accent,
    )
}

private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** Each tool is read independently so one locked or failing store doesn't blank the page. */
private suspend fun loadStats(container: AppContainer, context: android.content.Context): StorageStats {
    suspend fun <T> safe(block: suspend () -> T): T? = runCatching { block() }.getOrNull()
    val vault = safe { container.vaultFileRepository.observeFiles().first() }
    return StorageStats(
        keyStoreEntries = safe { container.keyStoreRepository.observeEntries().first().size },
        notes = safe { container.secureNoteRepository.observeNotes().first().size },
        noteImages = safe { container.secureNoteRepository.observeImagesByNote().first().values.sumOf { it.size } },
        vaultFiles = vault?.size,
        vaultBytes = vault?.sumOf { it.sizeBytes },
        expenses = safe { container.expenseRepository.observeAllEntries().first().size },
        habits = safe { container.habitRepository.observeDashboard().first().habits.size },
        reminders = safe { container.reminderRepository.observeReminders().first().size },
        databaseBytes = context.getDatabasePath("daykit_secure.db").length(),
    )
}
