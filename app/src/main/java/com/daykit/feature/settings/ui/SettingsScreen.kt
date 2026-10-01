package com.daykit.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.SearchAppTopBar
import com.daykit.core.designsystem.components.AppTopBarCompactHeight
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors

/** [keywords] are the settings inside the page; search matches them as well as the title. */
private data class SettingsLink(
    val headline: String,
    val keywords: List<String>,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit,
)

private data class SettingsSearchHit(val label: String, val link: SettingsLink)

/** The Settings tab: a hub of grouped links into the settings sub-pages. */
@Composable
fun SettingsScreen(
    bottomBarPadding: PaddingValues,
    onOpenSecurity: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenHomeLayout: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenDataStorage: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAboutApp: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val accents = MaterialTheme.extendedColors.accents
    val sections = listOf(
        "Security" to listOf(
            SettingsLink(
                "Security & Privacy",
                listOf(
                    "Change Master PIN or Password", "Fingerprint Unlock", "Auto-Lock", "Screenshot Protection",
                    "Hide in Recents", "Clear Copied Secrets", "Clipboard", "Re-lock Locked Apps",
                    "Uninstall Protection", "Anti-theft Protection", "Failed Unlock Attempts",
                ),
                Icons.Rounded.Shield, accents.indigo, onOpenSecurity,
            ),
        ),
        "Preferences" to listOf(
            SettingsLink(
                "General",
                listOf("Currency", "First Day of Week", "Time Format", "24-hour clock", "Open On"),
                Icons.Rounded.Tune, accents.blue, onOpenGeneral,
            ),
            SettingsLink("Appearance", listOf("Theme", "Dark mode", "Haptic feedback"), Icons.Rounded.Palette, accents.orange, onOpenAppearance),
            SettingsLink("Home Screen", listOf("Show tools", "Hide tools", "Reorder tools"), Icons.Rounded.GridView, accents.green, onOpenHomeLayout),
            SettingsLink(
                "Notifications & Permissions",
                listOf("Full-Screen Alarm", "Snooze Length", "Sound & Vibration", "Permissions", "Usage Access", "Overlay", "Exact alarms"),
                Icons.Rounded.NotificationsActive, accents.pink, onOpenNotifications,
            ),
        ),
        "Data" to listOf(
            SettingsLink(
                "Backup & Restore",
                listOf("Backup password", "Google Drive", "Automatic backups", "What's included", "Restore", "Local file"),
                Icons.Rounded.CloudUpload, accents.blue, onOpenBackupRestore,
            ),
            SettingsLink("Data & Storage", listOf("Storage usage", "Export Expenses", "Export Notes"), Icons.Rounded.Storage, accents.teal, onOpenDataStorage),
        ),
        "About" to listOf(
            SettingsLink("About DayKit", listOf("Version"), Icons.Rounded.Info, accents.blue, onOpenAboutApp),
            SettingsLink("Privacy Policy", emptyList(), Icons.Rounded.Policy, accents.green, onOpenPrivacyPolicy),
        ),
    )

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = searchActive) { searchActive = false; query = "" }
    val needle = query.trim()
    val hits = if (needle.isEmpty()) emptyList() else sections.flatMap { it.second }.flatMap { link ->
        (listOf(link.headline) + link.keywords)
            .filter { it.contains(needle, ignoreCase = true) }
            .map { SettingsSearchHit(it, link) }
    }

    val headerHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppTopBarCompactHeight
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = headerHeight + Spacing.md,
                bottom = bottomBarPadding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (needle.isNotEmpty()) {
                if (hits.isEmpty()) {
                    item(key = "no-results") {
                        EmptyState(
                            modifier = Modifier.padding(top = Spacing.xxl),
                            icon = Icons.Rounded.Search,
                            title = "No settings match",
                            description = "Try a different word, like \"lock\" or \"backup\".",
                        )
                    }
                } else {
                    item(key = "results") {
                        AppCard(contentPadding = PaddingValues(0.dp)) {
                            hits.forEachIndexed { index, hit ->
                                AppListRow(
                                    headline = hit.label,
                                    supporting = if (hit.label == hit.link.headline) null else hit.link.headline,
                                    leadingIcon = hit.link.icon,
                                    leadingAccent = hit.link.accent,
                                    trailing = { NavChevron() },
                                    onClick = hit.link.onClick,
                                )
                                if (index < hits.lastIndex) RowDivider(startIndent = Spacing.lg)
                            }
                        }
                    }
                }
            } else {
                sections.forEachIndexed { sectionIndex, (title, links) ->
                    item(key = "header-$title") {
                        SectionHeader(title, topPadding = if (sectionIndex == 0) 0.dp else Spacing.sm)
                    }
                    item(key = "card-$title") {
                        AppCard(contentPadding = PaddingValues(0.dp)) {
                            links.forEachIndexed { index, link ->
                                AppListRow(
                                    headline = link.headline,
                                    leadingIcon = link.icon,
                                    leadingAccent = link.accent,
                                    trailing = { NavChevron() },
                                    onClick = link.onClick,
                                )
                                if (index < links.lastIndex) RowDivider(startIndent = Spacing.lg)
                            }
                        }
                    }
                }
            }
        }
        SearchAppTopBar(
            title = "Settings",
            query = query,
            onQueryChange = { query = it },
            searchActive = searchActive,
            onSearchActiveChange = { searchActive = it; if (!it) query = "" },
            searchPlaceholder = "Search settings",
            height = AppTopBarCompactHeight,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
