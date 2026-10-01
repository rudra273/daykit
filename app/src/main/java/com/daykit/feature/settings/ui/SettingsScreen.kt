package com.daykit.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.AppTopBarCompactHeight
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors

private data class SettingsLink(
    val headline: String,
    val supporting: String?,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit,
)

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
            SettingsLink("Security & Privacy", "PIN, auto-lock, screenshots, clipboard", Icons.Rounded.Shield, accents.indigo, onOpenSecurity),
        ),
        "Preferences" to listOf(
            SettingsLink("General", "Currency, week start, time format", Icons.Rounded.Tune, accents.blue, onOpenGeneral),
            SettingsLink("Appearance", "Theme and haptics", Icons.Rounded.Palette, accents.orange, onOpenAppearance),
            SettingsLink("Home Screen", "Show, hide and reorder tools", Icons.Rounded.GridView, accents.green, onOpenHomeLayout),
            SettingsLink("Notifications & Permissions", "Reminders and what DayKit can access", Icons.Rounded.NotificationsActive, accents.pink, onOpenNotifications),
        ),
        "Data" to listOf(
            SettingsLink("Backup & Restore", null, Icons.Rounded.CloudUpload, accents.blue, onOpenBackupRestore),
            SettingsLink("Data & Storage", "Usage per tool and exports", Icons.Rounded.Storage, accents.teal, onOpenDataStorage),
        ),
        "About" to listOf(
            SettingsLink("About DayKit", null, Icons.Rounded.Info, accents.blue, onOpenAboutApp),
            SettingsLink("Privacy Policy", null, Icons.Rounded.Policy, accents.green, onOpenPrivacyPolicy),
        ),
    )

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
            sections.forEachIndexed { sectionIndex, (title, links) ->
                item(key = "header-$title") {
                    SectionHeader(title, topPadding = if (sectionIndex == 0) 0.dp else Spacing.sm)
                }
                item(key = "card-$title") {
                    AppCard(contentPadding = PaddingValues(0.dp)) {
                        links.forEachIndexed { index, link ->
                            AppListRow(
                                headline = link.headline,
                                supporting = link.supporting,
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
        AppTopBar(
            title = "Settings",
            height = AppTopBarCompactHeight,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
