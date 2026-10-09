@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.keystore.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.MinTouchTarget
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppFab
import com.daykit.core.designsystem.components.DestructiveButton
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.RecentlyDeletedAction
import com.daykit.core.designsystem.components.RecentlyDeletedSheet
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SearchAppTopBar
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.components.TrashItem
import com.daykit.core.designsystem.components.rememberErrorReporter
import com.daykit.core.designsystem.components.showUndo
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.util.SensitiveClipboard
import com.daykit.feature.keystore.data.KeyDraft
import com.daykit.feature.keystore.data.KeyField
import com.daykit.feature.keystore.data.KeyFieldFormat
import com.daykit.feature.keystore.data.KeyFieldKind
import com.daykit.feature.keystore.data.KeyStoreEntry
import com.daykit.feature.keystore.data.KeyType
import kotlinx.coroutines.launch

private sealed interface KeyEditorState {
    data class Add(val type: KeyType) : KeyEditorState
    data class Edit(val entry: KeyStoreEntry) : KeyEditorState
}

@Composable
fun KeyStoreScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by container.keyStoreRepository
        .observeEntries()
        .collectAsStateWithLifecycle(initialValue = null)
    var editorState by remember { mutableStateOf<KeyEditorState?>(null) }
    // By id, so the open sheet follows edits (e.g. a recovery code ticked off).
    var actionEntryId by remember { mutableStateOf<String?>(null) }
    val actionEntry = entries?.firstOrNull { it.entryId == actionEntryId }
    var showTrash by remember { mutableStateOf(false) }
    val trashedEntries by container.keyStoreRepository
        .observeTrash()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val types = remember { listOf<KeyType?>(null) + KeyType.entries }
    val pagerState = rememberPagerState(pageCount = { types.size })

    val snackbarHostState = remember { SnackbarHostState() }
    val errors = rememberErrorReporter(snackbarHostState, scope)

    fun copy(label: String, value: String) {
        SensitiveClipboard.copy(context, label, value)
        scope.launch { snackbarHostState.showSnackbar("$label copied") }
    }

    BackHandler {
        if (searchActive) {
            searchActive = false
            query = ""
        } else if (query.isNotEmpty() || pagerState.currentPage != 0) {
            query = ""
            scope.launch { pagerState.animateScrollToPage(0) }
        } else {
            onBack()
        }
    }

    val matchingEntries = remember(entries, query) {
        entries.orEmpty().filter { entry ->
            // Secret fields never match search.
            query.isBlank() ||
                entry.name.contains(query, ignoreCase = true) ||
                entry.label.contains(query, ignoreCase = true) ||
                entry.type.title.contains(query, ignoreCase = true) ||
                entry.fields.any { !it.kind.isSecret && it.value.contains(query, ignoreCase = true) }
        }
    }

    val uniqueLabels = remember(entries) {
        entries.orEmpty().map { it.label }.filter { it.isNotBlank() }.toSet().toList().sorted()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                SearchAppTopBar(
                    title = "Key Store",
                    query = query,
                    onQueryChange = { query = it },
                    searchActive = searchActive,
                    onSearchActiveChange = { searchActive = it; if (!it) query = "" },
                    onBack = onBack,
                    searchPlaceholder = "Search keys",
                    actions = { RecentlyDeletedAction(onClick = { showTrash = true }) },
                )
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = Spacing.md,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.extendedColors.accents.blue,
                ) {
                    types.forEachIndexed { index, type ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(type.tabTitle(), maxLines = 1) },
                            unselectedContentColor = MaterialTheme.extendedColors.textMuted,
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AppFab(
                icon = Icons.Rounded.Add,
                contentDescription = "Add key",
                onClick = { editorState = KeyEditorState.Add(types[pagerState.currentPage] ?: KeyType.Simple) },
            )
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            key = { types[it]?.storageKey ?: "all" },
        ) { page ->
            val type = types[page]
            val filteredEntries = remember(matchingEntries, type) {
                matchingEntries.filter { type == null || it.type == type }
            }
            val grouped = remember(filteredEntries) {
                filteredEntries.groupBy { it.label.ifBlank { "Unlabeled" } }.toSortedMap()
            }
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.md,
                    bottom = Spacing.xxl + 72.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (entries == null) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                } else if (filteredEntries.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = if (query.isNotBlank()) Icons.Rounded.SearchOff else type?.icon ?: Icons.Rounded.VpnKey,
                            title = if (query.isNotBlank()) "No matching keys" else "No ${type.emptyTitle()} saved",
                            description = if (query.isBlank()) "Tap + to add your first entry." else null,
                        )
                    }
                } else {
                    grouped.forEach { (group, groupEntries) ->
                        item(key = "section-$group") {
                            SectionHeader(text = group, topPadding = if (group == grouped.keys.firstOrNull()) 0.dp else Spacing.sm)
                        }
                        item(key = "group-$group") {
                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                groupEntries.forEachIndexed { index, entry ->
                                    KeyEntryRow(
                                        entry = entry,
                                        onClick = { actionEntryId = entry.entryId },
                                        onCopy = entry.quickCopyValue?.let { value -> { copy(entry.name, value) } },
                                    )
                                    if (index < groupEntries.lastIndex) RowDivider(startIndent = RowDividerIndent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actionEntry?.let { entry ->
        KeyDetailSheet(
            entry = entry,
            onDismiss = { actionEntryId = null },
            onCopy = ::copy,
            onToggleCode = { code, used ->
                errors.launchGuarded("Couldn't update that code.") {
                    container.keyStoreRepository.setRecoveryCodeUsed(entry.entryId, code, used)
                }
            },
            onEdit = {
                actionEntryId = null
                editorState = KeyEditorState.Edit(entry)
            },
            onDelete = {
                actionEntryId = null
                errors.launchGuarded("Couldn't delete that key.") {
                    container.keyStoreRepository.deleteEntry(entry.entryId)
                    scope.launch {
                        snackbarHostState.showUndo("\"${entry.name}\" moved to Recently deleted") {
                            container.keyStoreRepository.restoreEntry(entry.entryId)
                        }
                    }
                }
            },
        )
    }

    if (showTrash) {
        RecentlyDeletedSheet(
            items = trashedEntries.map { entry ->
                TrashItem(
                    id = entry.entryId,
                    title = entry.name,
                    deletedAtMillis = entry.deletedAtMillis ?: 0L,
                    icon = entry.type.icon,
                )
            },
            onDismiss = { showTrash = false },
            onRestore = { item ->
                errors.launchGuarded("Couldn't restore that key.") {
                    container.keyStoreRepository.restoreEntry(item.id)
                }
            },
            onDeleteForever = { item ->
                errors.launchGuarded("Couldn't delete that key.") {
                    container.keyStoreRepository.deleteEntryForever(item.id)
                }
            },
            onEmpty = {
                errors.launchGuarded("Couldn't empty Recently deleted.") {
                    container.keyStoreRepository.emptyTrash()
                }
            },
        )
    }

    editorState?.let { state ->
        val editing = state as? KeyEditorState.Edit
        KeyFormSheet(
            entry = editing?.entry,
            existingLabels = uniqueLabels,
            initialType = (state as? KeyEditorState.Add)?.type ?: KeyType.Simple,
            onDismiss = { editorState = null },
            onSave = { draft ->
                errors.launchGuarded(
                    failureMessage = "Couldn't save that key.",
                    onFailure = { editorState = null },
                ) {
                    if (editing != null) {
                        container.keyStoreRepository.updateEntry(editing.entry.entryId, draft)
                    } else {
                        container.keyStoreRepository.addEntry(draft)
                    }
                    editorState = null
                    if (draft.hasExpiry()) requestNotificationPermissionIfNeeded(context as? Activity)
                }
            },
        )
    }
}

private val LeadingTileSize = 30.dp

/** Indent that lands the divider flush with the row's text column, past the leading tile. */
private val RowDividerIndent = Spacing.lg + LeadingTileSize + Spacing.md

/** A card or ID with an expiry date set, i.e. something [KeyExpiry] may remind about. */
private fun KeyDraft.hasExpiry(): Boolean = when (type) {
    KeyType.Card -> fields.any { it.key == "expiry" && KeyFieldFormat.isValidExpiry(it.value) }
    KeyType.Identity -> fields.any { it.key == "expires" && it.value.isNotBlank() }
    else -> false
}

/** Expiry reminders are notifications; ask once there's actually something to remind about. */
private fun requestNotificationPermissionIfNeeded(activity: Activity?) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 52)
}

/**
 * A key row for the grouped Key Store card. Deliberately local rather than
 * [com.daykit.core.designsystem.components.AppListRow], whose 60dp minHeight floor exists
 * for the settings switch-row rhythm — here the floor is the 48dp copy-button touch target
 * and nothing more. The label isn't repeated in the row: it's already the section header
 * above. [onCopy] is null when the entry has nothing to quick-copy.
 */
@Composable
private fun KeyEntryRow(
    entry: KeyStoreEntry,
    onClick: () -> Unit,
    onCopy: (() -> Unit)?,
) {
    val summary = remember(entry) { entry.summary() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.type == KeyType.Simple) {
            KeyMonogramTile(
                letter = entry.name.trim().firstOrNull()?.uppercase() ?: "#",
                accent = accentFor(entry.label.ifBlank { entry.name }),
            )
        } else {
            AccentIconTile(icon = entry.type.icon, accent = entry.type.accent(), size = LeadingTileSize)
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(MinTouchTarget)) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    // Named per row so TalkBack doesn't read N identical "Copy" buttons.
                    contentDescription = "Copy ${entry.name}",
                    tint = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.size(MinTouchTarget))
        }
    }
}

/** 30dp accent monogram, matching `AccentIconTile`'s geometry for a text leading slot. */
@Composable
private fun KeyMonogramTile(letter: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(LeadingTileSize)
            .clip(MaterialTheme.shapes.medium)
            .background(accent.asAccentContainer()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = accent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun KeyDetailSheet(
    entry: KeyStoreEntry,
    onDismiss: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onToggleCode: (code: String, used: Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentIconTile(
                    icon = entry.type.icon,
                    accent = entry.type.accent(),
                    size = 40.dp,
                    iconSize = 22.dp,
                )
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(entry.type.title, entry.label).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.textMuted,
                    )
                }
            }
            if (entry.hasHero()) {
                KeyHero(entry = entry, onCopy = onCopy, onToggleCode = onToggleCode)
            }
            val rows = entry.fields.filter { it.value.isNotBlank() && it.kind !in HeroOwnedKinds }
            if (rows.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.extendedColors.inputField),
                ) {
                    rows.forEachIndexed { index, field ->
                        KeyFieldRow(field = field, onCopy = onCopy)
                        if (index < rows.lastIndex) RowDivider()
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SecondaryButton(
                    text = "Edit",
                    modifier = Modifier.weight(1f),
                    leadingIcon = {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = onEdit,
                )
                DestructiveButton(
                    text = "Delete",
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                )
            }
        }
    }
}

/** One stored value: its name, the value (masked until the eye is tapped if secret), copy. */
@Composable
private fun KeyFieldRow(
    field: KeyField,
    onCopy: (label: String, value: String) -> Unit,
) {
    val secret = field.kind.isSecret
    var revealed by rememberSaveable(field.key) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extendedColors.textMuted,
            )
            Text(
                text = KeyFieldFormat.display(field.kind, field.value, revealed),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (secret || field.kind == KeyFieldKind.Expiry) FontFamily.Monospace else null,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (revealed || !secret) 6 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (secret) {
            IconButton(onClick = { revealed = !revealed }, modifier = Modifier.size(MinTouchTarget)) {
                Icon(
                    if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (revealed) "Hide ${field.label}" else "Show ${field.label}",
                    tint = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(
            onClick = { onCopy(field.label, KeyFieldFormat.copyText(field.kind, field.value)) },
            modifier = Modifier.size(MinTouchTarget),
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = "Copy ${field.label}",
                tint = MaterialTheme.extendedColors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}


private fun KeyType?.tabTitle(): String = when (this) {
    null -> "All"
    KeyType.Simple -> "Keys"
    KeyType.Login -> "Logins"
    KeyType.Card -> "Cards"
    KeyType.Bank -> "Banks"
    KeyType.Identity -> "IDs"
    KeyType.Recovery -> "Recovery"
    KeyType.Crypto -> "Wallets"
}

private fun KeyType?.emptyTitle(): String = when (this) {
    null -> "keys"
    KeyType.Identity -> "IDs"
    KeyType.Recovery -> "recovery codes"
    else -> tabTitle().lowercase()
}
