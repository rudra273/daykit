package com.daykit.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daykit.R
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors

private data class AboutFeatureItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
)

@Composable
fun AboutAppScreen(
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.1.1"
    }
    val accents = MaterialTheme.extendedColors.accents

    val vaultFeatures = listOf(
        AboutFeatureItem("App Lock", "PIN & biometric protection for installed apps", Icons.Rounded.Lock, accents.blue),
        AboutFeatureItem("Key Store", "Hardware-bound Argon2id + AES-GCM secret vault", Icons.Rounded.Key, accents.indigo),
        AboutFeatureItem("Secure Notes", "Encrypted local notes with PIN-gated access", Icons.Rounded.Description, accents.mint),
        AboutFeatureItem("File Vault", "Streamed AES-256 photo & video encryption", Icons.Rounded.FolderSpecial, accents.purple),
    )

    val dailyFeatures = listOf(
        AboutFeatureItem("Focus & Limits", "Strict locks, daily app budgets & scheduled routines", Icons.Rounded.HourglassTop, accents.red),
        AboutFeatureItem("Habits & Reminders", "Daily streaks, check-ins & exact alarm reminders", Icons.Rounded.TaskAlt, accents.green),
        AboutFeatureItem("Dayflow & Expenses", "Pomodoro timer, mood journal & monthly spend tracker", Icons.Rounded.AutoAwesome, accents.pink),
    )

    val utilityFeatures = listOf(
        AboutFeatureItem("Encrypted Backups", "Password-encrypted local & Google Drive backups", Icons.Rounded.CloudUpload, accents.blue),
        AboutFeatureItem("Scanner & Image Tool", "On-device PDF scanning, compression & EXIF stripping", Icons.Rounded.DocumentScanner, accents.mint),
        AboutFeatureItem("DNS & Device Manager", "Private DNS shortcuts & local storage diagnostics", Icons.Rounded.PhoneAndroid, accents.cyan),
    )

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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AboutBrandHero(versionName = versionName)

            SectionHeader("Security & Vault")
            AboutFeatureGroupCard(items = vaultFeatures)

            SectionHeader("Focus & Daily Kit")
            AboutFeatureGroupCard(items = dailyFeatures)

            SectionHeader("Utilities & Backup")
            AboutFeatureGroupCard(items = utilityFeatures)

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutBrandHero(versionName: String) {
    val ext = MaterialTheme.extendedColors
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Spacing.xl, horizontal = Spacing.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF8F3EB))
                    .border(0.5.dp, ext.divider, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_daykit_mark),
                    contentDescription = "DayKit",
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "DayKit",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Version $versionName",
                style = MaterialTheme.typography.labelMedium,
                color = ext.textMuted,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Local-first privacy & productivity kit. Your data stays encrypted on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = ext.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.md))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TrustBadge(icon = Icons.Rounded.WifiOff, label = "Local-First", accent = ext.accents.mint)
                TrustBadge(icon = Icons.Rounded.Shield, label = "AES-256-GCM", accent = ext.accents.blue)
                TrustBadge(icon = Icons.Rounded.VerifiedUser, label = "SQLCipher DB", accent = ext.accents.indigo)
            }
        }
    }
}

@Composable
private fun TrustBadge(icon: ImageVector, label: String, accent: Color) {
    val isDark = MaterialTheme.extendedColors.isDark
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = if (isDark) 0.18f else 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
    }
}

@Composable
private fun AboutFeatureGroupCard(items: List<AboutFeatureItem>) {
    AppCard(contentPadding = PaddingValues(0.dp)) {
        items.forEachIndexed { index, item ->
            AppListRow(
                headline = item.title,
                supporting = item.subtitle,
                leadingIcon = item.icon,
                leadingAccent = item.accent,
            )
            if (index < items.lastIndex) RowDivider()
        }
    }
}
