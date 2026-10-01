package com.daykit.feature.home.ui

import com.daykit.core.data.AppPreferences
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
import androidx.compose.material.icons.rounded.SearchOff
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
import com.daykit.core.designsystem.components.AppTopBarCompactHeight
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors

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

    val hiddenTools by AppPreferences.rememberPreference(AppPreferences.KEY_HOME_HIDDEN_TOOLS) {
        AppPreferences.homeHiddenTools
    }
    val toolOrder by AppPreferences.rememberPreference(AppPreferences.KEY_HOME_TOOL_ORDER) {
        AppPreferences.homeToolOrder
    }
    // Search still finds hidden tools, so hiding one never makes it unreachable.
    val q = query.trim()
    fun match(t: ToolTile) = if (q.isBlank()) {
        t.route !in hiddenTools
    } else {
        t.keywords.any { it.contains(q, true) } || t.name.contains(q, true)
    }
    val sections = homeToolCatalog(accents).ordered(toolOrder)
        .map { section -> section.copy(tools = section.tools.filter(::match)) }
    val nothing = sections.all { it.tools.isEmpty() }

    BackHandler(enabled = searchActive) { searchActive = false; query = "" }

    val headerHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppTopBarCompactHeight
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = headerHeight + Spacing.md,
                bottom = bottomBarPadding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (nothing) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = if (q.isBlank()) "All tools are hidden" else "No tools found",
                        description = if (q.isBlank()) {
                            "Show them again in Settings → Home Screen, or search for a tool."
                        } else {
                            "Try a different search."
                        },
                        modifier = Modifier.padding(top = Spacing.xxl),
                    )
                }
            }
            var firstVisible = true
            sections.forEach { section ->
                if (section.tools.isNotEmpty()) {
                    toolSection(section.title, section.tools, onOpenTool, firstSection = firstVisible)
                    firstVisible = false
                }
            }
        }
        SearchAppTopBar(
            title = "DayKit",
            query = query,
            onQueryChange = { query = it },
            searchActive = searchActive,
            onSearchActiveChange = { searchActive = it; if (!it) query = "" },
            searchPlaceholder = "Search tools",
            height = AppTopBarCompactHeight,
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
