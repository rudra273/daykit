package com.daykit.feature.settings.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.daykit.core.ui.AppBackButton
import com.daykit.core.ui.Cyan
import com.daykit.core.ui.GlassBackground
import com.daykit.core.ui.MutedText
import com.daykit.core.ui.SoftText

private const val PRIVACY_POLICY_URL = "https://www.rosmox.com/projects/daykit/privacy-policy"

@Composable
fun PrivacyPolicyScreen(
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
                        text = "Privacy Policy",
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
                PolicyHeader()
                PolicySection(
                    title = "Overview",
                    body = listOf(
                        "DayKit is a local-first privacy utility. The app is designed to keep user-created data on the device unless the user intentionally creates an encrypted local backup or connects Google Drive backup.",
                        "This policy explains what data the app accesses, how the data is used, what is stored locally, what may be placed into encrypted backup files, and how optional permissions such as Accessibility are used.",
                    ),
                )
                PolicySection(
                    title = "Data Stored On The Device",
                    body = listOf(
                        "Master PIN credential: the app stores a salted password hash for verifying the master PIN. The original PIN is not stored.",
                        "Key Store: saved key names, labels, and values are stored in the encrypted local database.",
                        "Secure Notes: note titles, content, labels, and timestamps are stored in the encrypted local database.",
                        "Expenses and Habits: tracker entries, bills, habit definitions, progress logs, and related timestamps are stored locally.",
                        "App Lock: selected locked package names and app labels are stored locally so App Lock can continue working after app restart or device reboot.",
                        "Settings: tool-lock preferences, backup preferences, selected Google account email for Drive backup, theme preferences, widget preferences, and local app configuration may be stored on the device.",
                        "File Vault: selected media files are copied to a local Documents/DayKitFileLocker folder. File Vault media is not encrypted by this app and is not stored in the app database.",
                    ),
                )
                PolicySection(
                    title = "Backup And Google Drive",
                    body = listOf(
                        "Backups are encrypted on the device before they are saved or uploaded. The backup password is used to derive the backup encryption key, and backup content is encrypted before it leaves the device.",
                        "Key Store and Secure Notes are always included in encrypted backup files. Expenses and Habits are optional and can be enabled or disabled in Backup & Restore settings.",
                        "App Lock package selections, theme preferences, widget settings, DNS choices, reminder schedules, editor cache, and File Vault media are not included in app backup files.",
                        "When Google Drive backup is enabled, DayKit requests access to create and manage its own backup files in the user's Google Drive. The app uploads encrypted backup files; it does not upload plain Key Store values, notes, expenses, or habits.",
                        "Google account authorization is used only for the backup and restore actions chosen by the user or for automatic backup when the user enables it.",
                    ),
                )
                PolicySection(
                    title = "Accessibility Permission",
                    body = listOf(
                        "Accessibility access is optional. The app can function without it, but App Lock detection may be less reliable.",
                        "If the user enables Accessibility for DayKit, the app uses Accessibility window events to detect when a user opens an app selected for locking, then shows the DayKit lock screen.",
                        "DayKit does not use Accessibility to read screen text, collect passwords, collect typed content, make purchases, send messages, perform clicks on behalf of the user, or transmit screen content.",
                        "Accessibility data is used only on the device for App Lock behavior and is not included in cloud backup.",
                    ),
                )
                PolicySection(
                    title = "Usage Access, Overlay, Notifications, And Device Admin",
                    body = listOf(
                        "Usage Access may be used to identify the foreground app for App Lock behavior when Accessibility is not enabled.",
                        "Overlay permission may be used to display a lock challenge over selected locked apps in the fallback App Lock flow.",
                        "Notification permission is used for reminders, habits, and app alerts when the user enables those features.",
                        "Device Admin is optional and is used only for uninstall protection. It does not give DayKit access to personal files or messages.",
                    ),
                )
                PolicySection(
                    title = "Network And Third Parties",
                    body = listOf(
                        "DayKit uses internet access for Google Drive backup and restore when the user connects a Google account.",
                        "The app uses Google sign-in/authorization components and Google Drive APIs for Drive backup operations.",
                        "The app does not sell user data. The app does not use advertising identifiers for ad targeting. The app does not intentionally share user-created vault data with third parties except when the user stores an encrypted backup in Google Drive.",
                    ),
                )
                PolicySection(
                    title = "Security Measures",
                    body = listOf(
                        "The local database is encrypted with SQLCipher. The database passphrase is randomly generated and protected using Android Keystore.",
                        "Sensitive Key Store and Secure Notes fields are encrypted before storage.",
                        "Backup files are encrypted with a password chosen by the user. If the backup password is forgotten, the backup cannot be restored.",
                        "The app sets secure window flags on the main activity to reduce screenshots of sensitive screens where supported by Android.",
                    ),
                )
                PolicySection(
                    title = "Retention And Deletion",
                    body = listOf(
                        "Data remains on the device until the user deletes it inside the app, clears app data, or uninstalls the app.",
                        "Encrypted Google Drive backup files remain in the user's Google Drive until deleted by the user or by backup retention behavior. DayKit keeps only recent backup files when it creates new Drive backups.",
                        "Uninstalling the app or clearing app data removes local app data from the device. It does not automatically delete backup files already stored in Google Drive.",
                    ),
                )
                PolicySection(
                    title = "User Choices",
                    body = listOf(
                        "Users can choose whether to enable Accessibility, Usage Access, Overlay permission, notifications, Device Admin, Google Drive backup, automatic backup, and optional backup of Expenses and Habits.",
                        "Users can use local encrypted backup without Google Drive by creating a local backup file.",
                        "Users can stop future Drive backups by turning automatic backup off or removing the backup password.",
                    ),
                )
                PolicySection(
                    title = "Children",
                    body = listOf(
                        "DayKit is a general utility app and is not directed to children. The app does not knowingly collect children's personal information.",
                    ),
                )
                PolicySection(
                    title = "Changes And Contact",
                    body = listOf(
                        "This privacy policy may be updated as the app changes. The current policy should be available inside the app and through the Google Play listing.",
                        "For privacy questions, use the developer contact information provided on the Google Play Store listing for DayKit.",
                    ),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PolicyHeader() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("DayKit Privacy Policy", color = SoftText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Effective date: May 9, 2026", color = Cyan, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(
            "This policy is written for the DayKit Android app.",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Public policy URL: $PRIVACY_POLICY_URL",
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                }
            },
            color = Cyan,
            style = MaterialTheme.typography.bodySmall,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun PolicySection(
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
