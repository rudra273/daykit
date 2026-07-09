package com.daykit.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppSearchBar
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.navigation.Routes

private data class ToolTile(
    val route: String,
    val name: String,
    val icon: ImageVector,
    val accent: @Composable () -> Color,
    val keywords: List<String>,
)

@Composable
fun HomeScreen(
    container: AppContainer,
    lockedCount: Int,
    bottomBarPadding: PaddingValues,
    onOpenTool: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val habitDashboard by container.habitRepository
        .observeDashboard()
        .collectAsStateWithLifecycle(initialValue = null)

    val gridState = rememberLazyGridState()
    val scrolledUnder by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 4 }
    }

    val accents = MaterialTheme.extendedColors.accents
    val habitsDone = habitDashboard?.let { d ->
        val total = d.buildHabits.size
        val done = d.buildHabits.count { h -> d.logFor(h.habitId)?.completed == true }
        if (total > 0) "$done/$total today" else "Track habits"
    } ?: "Track habits"

    val security = listOf(
        ToolTile(Routes.TOOL_APPLOCK, "App Lock", Icons.Rounded.Lock, { accents.blue },
            listOf("app lock", "lock")),
        ToolTile(Routes.TOOL_KEYSTORE, "Key Store", Icons.Rounded.VpnKey, { accents.indigo },
            listOf("key store", "password", "vault")),
        ToolTile(Routes.TOOL_NOTES, "Notes", Icons.Rounded.Notes, { accents.teal },
            listOf("notes", "secure notes")),
        ToolTile(Routes.TOOL_FILEVAULT, "File Vault", Icons.Rounded.Folder, { accents.purple },
            listOf("file vault", "file locker", "hide files", "images", "videos")),
    )
    val productivity = listOf(
        ToolTile(Routes.TOOL_HABITS, "Habits", Icons.Rounded.TrackChanges, { accents.green },
            listOf("habit", "habits")),
        ToolTile(Routes.TOOL_REMINDERS, "Reminders", Icons.Rounded.NotificationsActive, { accents.orange },
            listOf("reminder", "notification", "alarm")),
        ToolTile(Routes.TOOL_EXPENSES, "Expenses", Icons.Rounded.Payments, { accents.pink },
            listOf("expenses", "budget", "money")),
    )
    val other = listOf(
        ToolTile(Routes.TOOL_EDITOR, "Editor", Icons.Rounded.EditNote, { accents.yellow },
            listOf("editor", "document", "text")),
        ToolTile(Routes.TOOL_DNS, "DNS Manager", Icons.Rounded.Dns, { accents.red },
            listOf("dns", "ad block", "private dns")),
    )

    fun statusFor(route: String): String = when (route) {
        Routes.TOOL_APPLOCK -> if (lockedCount > 0) "$lockedCount apps locked" else "Protect your apps"
        Routes.TOOL_KEYSTORE -> "Encrypted vault"
        Routes.TOOL_NOTES -> "Private notes"
        Routes.TOOL_FILEVAULT -> "Hide photos & videos"
        Routes.TOOL_HABITS -> habitsDone
        Routes.TOOL_REMINDERS -> "Never forget"
        Routes.TOOL_EXPENSES -> "Track spending"
        Routes.TOOL_EDITOR -> "Write & export"
        Routes.TOOL_DNS -> "Private DNS setup"
        else -> ""
    }

    val q = query.trim()
    fun match(t: ToolTile) = q.isBlank() || t.keywords.any { it.contains(q, true) } ||
        t.name.contains(q, true)
    val fSecurity = security.filter(::match)
    val fProductivity = productivity.filter(::match)
    val fOther = other.filter(::match)
    val nothing = fSecurity.isEmpty() && fProductivity.isEmpty() && fOther.isEmpty()

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "DayKit", scrolledUnder = scrolledUnder)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = Spacing.sm,
                bottom = bottomBarPadding.calculateBottomPadding() + Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppSearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search tools")
            }
            if (nothing) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "No tools found",
                        description = "Try a different search.",
                        modifier = Modifier.padding(top = Spacing.xxl),
                    )
                }
            }
            toolSection("Security", fSecurity, ::statusFor, onOpenTool)
            toolSection("Productivity", fProductivity, ::statusFor, onOpenTool)
            toolSection("Other", fOther, ::statusFor, onOpenTool)
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.toolSection(
    title: String,
    tiles: List<ToolTile>,
    statusFor: (String) -> String,
    onOpenTool: (String) -> Unit,
) {
    if (tiles.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(title) }
    items(tiles, key = { it.route }) { tile ->
        ToolCard(tile = tile, status = statusFor(tile.route), onClick = { onOpenTool(tile.route) })
    }
}

@Composable
private fun ToolCard(tile: ToolTile, status: String, onClick: () -> Unit) {
    AppCard(onClick = onClick, contentPadding = PaddingValues(Spacing.lg)) {
        AccentIconTile(icon = tile.icon, accent = tile.accent(), size = 44.dp, iconSize = 24.dp)
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = tile.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
