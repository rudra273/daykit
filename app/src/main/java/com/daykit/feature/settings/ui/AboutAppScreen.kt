package com.rudra.daykit.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rudra.daykit.core.ui.AppBackButton
import com.rudra.daykit.core.ui.Cyan
import com.rudra.daykit.core.ui.GlassBackground
import com.rudra.daykit.core.ui.MutedText
import com.rudra.daykit.core.ui.SoftText

@Composable
fun AboutAppScreen(
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

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
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "About DayKit",
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        "Key Store and Secure Notes are always included in app backups. Expenses and Habits are optional and can be switched on or off in Backup & Restore settings.",
                        "App Lock selections, theme preferences, dashboard widget settings, DNS choices, reminder schedules, editor cache, and File Vault media are not included in app backups.",
                        "Google Drive backup uses the user's Google account permission to create and manage DayKit backup files. Google Drive stores the encrypted backup file, not plain app data.",
                    ),
                )
                AboutSection(
                    title = "App Lock",
                    body = listOf(
                        "App Lock stores the selected locked package list locally on the device so the app can keep working after restart or reboot.",
                        "Accessibility access is optional and improves locked-app detection. When enabled, DayKit detects window changes for the limited purpose of showing the lock screen for apps selected by the user.",
                        "Usage access and overlay permission are used as the fallback app-lock path when Accessibility is not enabled.",
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
                        "File Vault copies selected images or videos into a Documents/DayKitFileLocker folder and uses a locked file extension.",
                        "File Vault media is not encrypted, not saved in the app database, and not included in backup or restore.",
                        "Users should treat File Vault as a local hiding tool, not as a cryptographic vault.",
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
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AboutHero() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("DayKit", color = SoftText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "A local-first privacy utility with locked tools, encrypted notes and keys, optional encrypted backups, and practical daily trackers.",
            color = MutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Version 1.0", color = Cyan, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AboutSection(
    title: String,
    body: List<String>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = SoftText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        body.forEach { paragraph ->
            Text(paragraph, color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
