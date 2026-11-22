@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.home.ui

import android.app.Activity
import com.daykit.core.data.AppPreferences
import com.daykit.core.data.HomeCardSize
import com.daykit.core.data.HomeColumns
import com.daykit.core.session.AppLockSessionManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import com.daykit.AppContainer
import com.daykit.core.designsystem.DayKitMotion
import com.daykit.core.designsystem.LocalReduceMotion
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.BetaBadge
import com.daykit.core.designsystem.components.EmptyState
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.daykit.R
import com.daykit.core.designsystem.components.SegmentedControl
import com.daykit.core.designsystem.components.SearchAppTopBar
import com.daykit.core.designsystem.components.AppTopBarCompactHeight
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.navigation.Routes
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    container: AppContainer,
    lockedCount: Int,
    bottomBarPadding: PaddingValues,
    onOpenTool: (String) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var editLayoutActive by rememberSaveable { mutableStateOf(false) }
    var viewModeSheetOpen by rememberSaveable { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    var draggingRoute by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val accents = MaterialTheme.extendedColors.accents

    val hiddenTools by AppPreferences.rememberPreference(AppPreferences.KEY_HOME_HIDDEN_TOOLS) {
        AppPreferences.homeHiddenTools
    }
    val toolOrder by AppPreferences.rememberPreference(AppPreferences.KEY_HOME_TOOL_ORDER) {
        AppPreferences.homeToolOrder
    }
    val cardSize by AppPreferences.rememberPreference(AppPreferences.KEY_HOME_CARD_SIZE) {
        AppPreferences.homeCardSize
    }
    val columns by AppPreferences.rememberPreference(AppPreferences.KEY_HOME_COLUMNS) {
        AppPreferences.homeColumns
    }

    val allSections = homeToolCatalog(accents).ordered(toolOrder)
    // Search still finds hidden tools, so hiding one never makes it unreachable.
    val q = query.trim()
    fun match(t: ToolTile) = if (q.isBlank()) {
        t.route !in hiddenTools
    } else {
        t.keywords.any { it.contains(q, true) } || t.name.contains(q, true)
    }
    val sections = allSections
        .map { section -> section.copy(tools = section.tools.filter(::match)) }
    val nothing = sections.all { it.tools.isEmpty() }

    val latestAllSections by rememberUpdatedState(allSections)

    BackHandler(enabled = searchActive || editLayoutActive) {
        if (editLayoutActive) {
            editLayoutActive = false
            draggingRoute = null
            dragOffset = Offset.Zero
        } else {
            searchActive = false
            query = ""
        }
    }

    val headerHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppTopBarCompactHeight
    val gridSpacing = if (columns >= 3) Spacing.sm else Spacing.md

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            userScrollEnabled = draggingRoute == null,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = headerHeight + Spacing.md,
                bottom = bottomBarPadding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
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
                    toolSection(
                        title = section.title,
                        tiles = section.tools,
                        cardSize = cardSize,
                        columns = columns,
                        editMode = editLayoutActive,
                        draggingRoute = draggingRoute,
                        dragOffset = dragOffset,
                        onOpenTool = onOpenTool,
                        onDragStart = { route ->
                            draggingRoute = route
                            dragOffset = Offset.Zero
                        },
                        onDrag = { route, delta ->
                            dragOffset += delta
                            val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == route }
                            if (info != null) {
                                val centerX = (info.offset.x + dragOffset.x + info.size.width / 2f).roundToInt()
                                val centerY = (info.offset.y + dragOffset.y + info.size.height / 2f).roundToInt()
                                val sectionRoutes = section.tools.map { it.route }.toSet()
                                val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
                                    candidate.key != route &&
                                        candidate.key in sectionRoutes &&
                                        centerX in candidate.offset.x..(candidate.offset.x + candidate.size.width) &&
                                        centerY in candidate.offset.y..(candidate.offset.y + candidate.size.height)
                                }
                                val targetRoute = target?.key as? String
                                if (targetRoute != null) {
                                    val deltaPos = IntOffset(
                                        info.offset.x - target.offset.x,
                                        info.offset.y - target.offset.y,
                                    )
                                    moveHomeTool(latestAllSections, section.title, route, targetRoute)
                                    dragOffset += Offset(deltaPos.x.toFloat(), deltaPos.y.toFloat())
                                }
                            }
                        },
                        onDragEnd = {
                            draggingRoute = null
                            dragOffset = Offset.Zero
                        },
                        firstSection = firstVisible,
                    )
                    firstVisible = false
                }
            }
        }
        if (editLayoutActive) {
            AppTopBar(
                title = "Edit Layout",
                height = AppTopBarCompactHeight,
                modifier = Modifier.align(Alignment.TopCenter),
                actions = {
                    AppTextButton(
                        text = "Done",
                        onClick = {
                            editLayoutActive = false
                            draggingRoute = null
                            dragOffset = Offset.Zero
                        },
                    )
                },
            )
        } else {
            SearchAppTopBar(
                title = "DayKit",
                query = query,
                onQueryChange = { query = it },
                searchActive = searchActive,
                onSearchActiveChange = { searchActive = it; if (!it) query = "" },
                searchPlaceholder = "Search tools",
                height = AppTopBarCompactHeight,
                modifier = Modifier.align(Alignment.TopCenter),
                titleContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_daykit_mark),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "DayKit",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = MaterialTheme.extendedColors.card,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit layout") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Reorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    editLayoutActive = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("View mode") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.ViewModule,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModeSheetOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Home Screen") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.GridView,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onOpenTool(Routes.SETTINGS_HOME_LAYOUT)
                                },
                            )
                            HorizontalDivider(color = MaterialTheme.extendedColors.divider)
                            DropdownMenuItem(
                                text = { Text("Lock & quit", color = MaterialTheme.extendedColors.danger) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.extendedColors.danger,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    container.sensitiveKeyManager.lock()
                                    container.sensitiveKeyManager.discardPendingUnlockActions()
                                    AppLockSessionManager.clearAll()
                                    (context as? Activity)?.finishAndRemoveTask()
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    if (viewModeSheetOpen) {
        HomeViewModeSheet(
            columns = columns,
            cardSize = cardSize,
            onColumnsChange = { AppPreferences.homeColumns = it },
            onCardSizeChange = { AppPreferences.homeCardSize = it },
            onDismiss = { viewModeSheetOpen = false },
        )
    }
}

@Composable
private fun HomeViewModeSheet(
    columns: Int,
    cardSize: HomeCardSize,
    onColumnsChange: (Int) -> Unit,
    onCardSizeChange: (HomeCardSize) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "View mode",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Columns",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
                val columnOptions = HomeColumns.OPTIONS
                SegmentedControl(
                    items = columnOptions.map { "$it" },
                    selectedIndex = columnOptions.indexOf(columns).coerceAtLeast(0),
                    onSelect = { index -> onColumnsChange(columnOptions[index]) },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Card size",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
                val sizeOptions = HomeCardSize.entries
                SegmentedControl(
                    items = sizeOptions.map { it.label },
                    selectedIndex = sizeOptions.indexOf(cardSize).coerceAtLeast(0),
                    onSelect = { index -> onCardSizeChange(sizeOptions[index]) },
                )
            }
        }
    }
}

private fun moveHomeTool(
    allSections: List<ToolSection>,
    sectionTitle: String,
    fromRoute: String,
    toRoute: String,
) {
    val section = allSections.firstOrNull { it.title == sectionTitle } ?: return
    val list = section.tools.toMutableList()
    val fromIdx = list.indexOfFirst { it.route == fromRoute }
    val toIdx = list.indexOfFirst { it.route == toRoute }
    if (fromIdx < 0 || toIdx < 0 || fromIdx == toIdx) return
    val moved = list.removeAt(fromIdx)
    list.add(toIdx, moved)
    AppPreferences.homeToolOrder = allSections.flatMap { current ->
        if (current.title == sectionTitle) list else current.tools
    }.map { it.route }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.toolSection(
    title: String,
    tiles: List<ToolTile>,
    cardSize: HomeCardSize,
    columns: Int,
    editMode: Boolean,
    draggingRoute: String?,
    dragOffset: Offset,
    onOpenTool: (String) -> Unit,
    onDragStart: (String) -> Unit,
    onDrag: (String, Offset) -> Unit,
    onDragEnd: () -> Unit,
    firstSection: Boolean = false,
) {
    if (tiles.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        SectionHeader(title, topPadding = if (firstSection) 0.dp else Spacing.sm)
    }
    items(tiles, key = { it.route }) { tile ->
        val isDragging = draggingRoute == tile.route
        ToolCard(
            tile = tile,
            cardSize = cardSize,
            columns = columns,
            editMode = editMode,
            isDragging = isDragging,
            dragOffset = if (isDragging) dragOffset else Offset.Zero,
            onClick = { if (!editMode) onOpenTool(tile.route) },
            onDragStart = { onDragStart(tile.route) },
            onDrag = { delta -> onDrag(tile.route, delta) },
            onDragEnd = onDragEnd,
        )
    }
}

@Composable
private fun ToolCard(
    tile: ToolTile,
    cardSize: HomeCardSize,
    columns: Int,
    editMode: Boolean,
    isDragging: Boolean,
    dragOffset: Offset,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDrag by rememberUpdatedState(onDrag)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)

    val (basePadding, baseTileSize, baseIconSize) = when (cardSize) {
        HomeCardSize.Compact -> Triple(11.dp, 32.dp, 18.dp)
        HomeCardSize.Default -> Triple(14.dp, 38.dp, 21.dp)
        HomeCardSize.Large -> Triple(16.dp, 44.dp, 24.dp)
    }
    val (iconTitleGap, verticalSpace) = when (cardSize) {
        HomeCardSize.Compact -> 8.dp to 0.dp
        HomeCardSize.Default -> 10.dp to 0.dp
        HomeCardSize.Large -> 12.dp to 4.dp
    }

    val cardPadding = when (columns) {
        4 -> 8.dp
        3 -> 10.dp
        else -> basePadding
    }
    val tileSize = when (columns) {
        4 -> (baseTileSize - 4.dp).coerceAtLeast(24.dp)
        else -> baseTileSize
    }
    val iconSize = when (columns) {
        4 -> (baseIconSize - 2.dp).coerceAtLeast(14.dp)
        else -> baseIconSize
    }
    val titleStyle = when (columns) {
        4 -> MaterialTheme.typography.labelMedium
        3 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.titleMedium
    }

    val reduceMotion = LocalReduceMotion.current
    val dragScale by animateFloatAsState(
        targetValue = if (editMode && isDragging) 1.06f else 1f,
        animationSpec = if (reduceMotion) snap() else DayKitMotion.controlSpring(),
        label = "dragScale",
    )
    val dragModifier = if (editMode) {
        Modifier
            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = if (isDragging) 18f else 0f
            }
            .pointerInput(tile.route) {
                detectDragGestures(
                    onDragStart = { latestOnDragStart() },
                    onDragEnd = { latestOnDragEnd() },
                    onDragCancel = { latestOnDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        latestOnDrag(dragAmount)
                    },
                )
            }
    } else {
        Modifier
    }

    AppCard(
        modifier = dragModifier,
        onClick = if (editMode) null else onClick,
        contentPadding = PaddingValues(cardPadding),
    ) {
        when (columns) {
            1 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccentIconTile(icon = tile.icon, accent = tile.accent(), size = tileSize, iconSize = iconSize)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tile.name,
                                style = titleStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (tile.isBeta) {
                                Spacer(Modifier.width(6.dp))
                                BetaBadge()
                            }
                        }
                        if (cardSize != HomeCardSize.Compact) {
                            Text(
                                text = tile.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (editMode) {
                        Icon(
                            imageVector = Icons.Rounded.DragIndicator,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.extendedColors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            4 -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (verticalSpace > 0.dp) Spacer(Modifier.height(verticalSpace / 2))
                    Box(Modifier.fillMaxWidth()) {
                        AccentIconTile(
                            icon = tile.icon,
                            accent = tile.accent(),
                            size = tileSize,
                            iconSize = iconSize,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        if (editMode) {
                            Icon(
                                imageVector = Icons.Rounded.DragIndicator,
                                contentDescription = "Drag to reorder",
                                tint = MaterialTheme.extendedColors.textMuted,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tile.name,
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (verticalSpace > 0.dp) Spacer(Modifier.height(verticalSpace / 2))
                }
            }
            else -> {
                if (verticalSpace > 0.dp) Spacer(Modifier.height(verticalSpace))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AccentIconTile(icon = tile.icon, accent = tile.accent(), size = tileSize, iconSize = iconSize)
                    if (editMode) {
                        Icon(
                            imageVector = Icons.Rounded.DragIndicator,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.extendedColors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    } else if (tile.isBeta) {
                        BetaBadge()
                    }
                }
                Spacer(Modifier.height(iconTitleGap))
                Text(
                    text = tile.name,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (verticalSpace > 0.dp) Spacer(Modifier.height(verticalSpace))
            }
        }
    }
}
