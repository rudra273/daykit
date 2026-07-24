package com.daykit.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors

@Composable
fun AboutAppScreen(
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "About DayKit",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AboutHero()
            AboutSection(
                title = "What DayKit Does",
                body = listOf(
                    "DayKit is a private utility app for securing app access, storing sensitive notes and keys, tracking expenses and habits, managing reminders, choosing DNS shortcuts, editing text files, and hiding selected media files from normal gallery views.",
                    "The app is designed around local-first storage. Most data stays on the device unless the user creates an encrypted backup or connects Google Drive backup.",
                ),
            )
            AboutSection(
                title = "Key Store Locking",
                body = listOf(
                    "Key Store entries are stored in the encrypted local database. Entry names, labels, and values are encrypted before they are saved.",
                    "The Key Store tool is locked by default. A user must unlock it with the master PIN or enabled biometric unlock before saved key values are shown.",
                    "The local database passphrase is randomly generated and protected with Android Keystore AES-GCM. Android Keystore protects the app's encryption key from normal file access by other apps.",
                ),
            )
            AboutSection(
                title = "Backup And Restore",
                body = listOf(
                    "Backups are encrypted before they leave the device. The backup password is used to derive an encryption key, and the backup payload is encrypted with AES-GCM.",
                    "Key Store and Secure Notes are always included in app backups. Expenses, Habits, and File Vault files are optional and can be switched on or off in Backup & Restore settings. File Vault backup is off by default.",
                    "App Lock selections, theme preferences, dashboard widget settings, DNS choices, reminder schedules, and editor cache are not included in app backups.",
                    "Google Drive backup uses the user's Google account permission to create and manage DayKit backup files. Google Drive stores the encrypted backup file, not plain app data.",
                ),
            )
            AboutSection(
                title = "App Lock",
                body = listOf(
                    "App Lock stores the selected locked package list locally on the device so the app can keep working after restart or reboot.",
                    "Usage access is used to detect when a locked app opens, and overlay permission is used to show the lock challenge over it.",
                    "App Lock package selections are not backed up to cloud or local backup files.",
                ),
            )
            AboutSection(
                title = "Secure Notes",
                body = listOf(
                    "Secure Notes stores private note titles, content, and labels encrypted in the local database.",
                    "The Notes tool can require master PIN or biometric unlock before opening, depending on the user's tool-lock settings.",
                    "Secure Notes are included in encrypted backup files so they can be restored on a new install with the backup password.",
                ),
            )
            AboutSection(
                title = "File Vault",
                body = listOf(
                    "File Vault encrypts selected images and videos with AES-256 and stores the ciphertext in the app's private storage, where other apps and file managers cannot read it.",
                    "Each file is encrypted with its own key, and that key is protected by the Android Keystore. Files are streamed through encryption, so even large videos can be added and played back without leaving a decrypted copy on disk.",
                    "File Vault files can optionally be included in encrypted backups. This is off by default and only takes effect when the user turns it on in Backup & Restore settings.",
                ),
            )
            AboutSection(
                title = "Expenses, Habits, Reminders, DNS, And Editor",
                body = listOf(
                    "Expenses and Habits are stored locally and can optionally be included in encrypted backups.",
                    "Reminders are stored locally for app scheduling and notification behavior, but they are not currently included in backup files.",
                    "DNS Manager opens Android private DNS settings and does not operate a DNS server or collect DNS traffic.",
                    "Editor creates user-directed text files and temporary share files only when the user chooses to save or share.",
                ),
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun AboutHero() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "DayKit",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "A local-first privacy utility with locked tools, encrypted notes and keys, optional encrypted backups, and practical daily trackers.",
            color = MaterialTheme.extendedColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Version 1.0",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AboutSection(
    title: String,
    body: List<String>,
) {
    SectionHeader(text = title)
    Column(modifier = Modifier.fillMaxWidth()) {
        body.forEachIndexed { index, paragraph ->
            if (index > 0) Spacer(Modifier.height(Spacing.sm))
            Text(
                paragraph,
                color = MaterialTheme.extendedColors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
