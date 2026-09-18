package com.daykit.feature.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.dp
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.SearchAppTopBar
import com.daykit.core.designsystem.components.AppTopBarHeight
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.navigation.Routes

private data class ToolTile(
    val route: String,
    val name: String,
    val description: String,
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
    var searchActive by rememberSaveable { mutableStateOf(false) }

    val gridState = rememberLazyGridState()

    val accents = MaterialTheme.extendedColors.accents

    val security = listOf(
        ToolTile(Routes.TOOL_APPLOCK, "App Lock", "Protect selected apps", Icons.Rounded.Lock, { accents.blue },
            listOf("app lock", "lock")),
        ToolTile(Routes.TOOL_KEYSTORE, "Key Store", "Store private values", Icons.Rounded.VpnKey, { accents.indigo },
            listOf("key store", "password", "vault")),
        ToolTile(Routes.TOOL_NOTES, "Notes", "Keep private notes", Icons.Rounded.Notes, { accents.teal },
            listOf("notes", "secure notes")),
        ToolTile(Routes.TOOL_FILEVAULT, "File Vault", "Protect photos & videos", Icons.Rounded.Folder, { accents.purple },
            listOf("file vault", "file locker", "hide files", "images", "videos")),
    )
    val productivity = listOf(
        ToolTile(Routes.TOOL_DAYFLOW, "Dayflow", "Pomodoro, journal & mood", Icons.Rounded.AutoAwesome, { accents.indigo },
            listOf("dayflow", "pomodoro", "journal", "mood", "timer")),
        ToolTile(Routes.TOOL_HABITS, "Habits", "Build daily routines", Icons.Rounded.TrackChanges, { accents.green },
            listOf("habit", "habits")),
        ToolTile(Routes.TOOL_FOCUS, "Focus", "Block distractions", Icons.Rounded.Timer, { accents.red },
            listOf("focus", "focus block", "block app", "distraction", "screen time")),
        ToolTile(Routes.TOOL_EXPENSES, "Expenses", "Track monthly spending", Icons.Rounded.Payments, { accents.pink },
            listOf("expenses", "budget", "money")),
    )
    val other = listOf(
        ToolTile(Routes.TOOL_REMINDERS, "Reminders", "Stay on top of tasks", Icons.Rounded.NotificationsActive, { accents.orange },
            listOf("reminder", "notification", "alarm")),
        ToolTile(Routes.TOOL_SCANNER, "Document Scanner", "Scan documents", Icons.Rounded.DocumentScanner, { accents.blue },
            listOf("scanner", "scan document", "document", "camera", "pdf")),
        ToolTile(Routes.TOOL_EDITOR, "Editor", "Write text files", Icons.Rounded.EditNote, { accents.yellow },
            listOf("editor", "document", "text", "pdf")),
        ToolTile(Routes.TOOL_DNS, "DNS Manager", "Set up Private DNS", Icons.Rounded.Dns, { accents.red },
            listOf("dns", "ad block", "private dns")),
        ToolTile(Routes.TOOL_EVENTLIGHT, "Event Light", "Light for video calls", Icons.Rounded.FlashOn, { accents.yellow },
            listOf("event light", "ring light", "video call light", "night light", "border light")),
    )

    val q = query.trim()
    fun match(t: ToolTile) = q.isBlank() || t.keywords.any { it.contains(q, true) } ||
        t.name.contains(q, true)
    val fSecurity = security.filter(::match)
    val fProductivity = productivity.filter(::match)
    val fOther = other.filter(::match)
    val nothing = fSecurity.isEmpty() && fProductivity.isEmpty() && fOther.isEmpty()

    BackHandler(enabled = searchActive) { searchActive = false; query = "" }

    val headerHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppTopBarHeight
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = headerHeight,
                bottom = bottomBarPadding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
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
            toolSection("Security", fSecurity, onOpenTool, firstSection = true)
            toolSection("Productivity", fProductivity, onOpenTool, firstSection = fSecurity.isEmpty())
            toolSection(
                "Utilities",
                fOther,
                onOpenTool,
                firstSection = fSecurity.isEmpty() && fProductivity.isEmpty(),
            )
        }
        SearchAppTopBar(
            title = "DayKit",
            query = query,
            onQueryChange = { query = it },
            searchActive = searchActive,
            onSearchActiveChange = { searchActive = it; if (!it) query = "" },
            searchPlaceholder = "Search tools",
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.toolSection(
    title: String,
    tiles: List<ToolTile>,
    onOpenTool: (String) -> Unit,
    firstSection: Boolean = false,
) {
    if (tiles.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        SectionHeader(title, topPadding = if (firstSection) 0.dp else Spacing.sm)
    }
    items(tiles, key = { it.route }) { tile ->
        ToolCard(tile = tile, onClick = { onOpenTool(tile.route) })
    }
}

@Composable
private fun ToolCard(tile: ToolTile, onClick: () -> Unit) {
    val reservedDescriptionHeight = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }
    val iconTitleGap = Spacing.sm
    val verticalSpace = reservedDescriptionHeight / 2
    AppCard(
        onClick = onClick,
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Spacer(Modifier.height(verticalSpace))
        AccentIconTile(icon = tile.icon, accent = tile.accent(), size = 36.dp, iconSize = 20.dp)
        Spacer(Modifier.height(iconTitleGap))
        Text(
            text = tile.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Keep the card height while centering the slightly wider icon/title pair.
        Spacer(Modifier.height(verticalSpace))
    }
}
