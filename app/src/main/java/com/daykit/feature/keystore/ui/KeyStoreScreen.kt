@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.keystore.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import com.daykit.AppContainer
import com.daykit.core.designsystem.MinTouchTarget
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AppAlertDialog
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppFab
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.DestructiveButton
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SearchAppTopBar
import com.daykit.core.designsystem.components.rememberErrorReporter
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.keystore.data.KeyStoreEntry
import kotlinx.coroutines.launch

private sealed interface KeyEditorState {
    data object Add : KeyEditorState
    data class Edit(val entry: KeyStoreEntry) : KeyEditorState
}

@Composable
fun KeyStoreScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val entries by container.keyStoreRepository
        .observeEntries()
        .collectAsStateWithLifecycle(initialValue = null)
    var editorState by remember { mutableStateOf<KeyEditorState?>(null) }
    var actionEntry by remember { mutableStateOf<KeyStoreEntry?>(null) }
    var confirmDeleteEntry by remember { mutableStateOf<KeyStoreEntry?>(null) }
    var query by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val errors = rememberErrorReporter(snackbarHostState, scope)

    BackHandler {
        if (searchActive) {
            searchActive = false
            query = ""
        } else if (query.isNotEmpty() || selectedLabel != null) {
            query = ""
            selectedLabel = null
        } else {
            onBack()
        }
    }

    val filteredEntries = remember(entries, query, selectedLabel) {
        entries.orEmpty().filter { entry ->
            val matchesQuery = query.isBlank() ||
                entry.name.contains(query, ignoreCase = true) ||
                entry.label.contains(query, ignoreCase = true)
            val matchesLabel = selectedLabel == null || entry.label == selectedLabel
            matchesQuery && matchesLabel
        }
    }

    val listState = rememberLazyListState()

    val uniqueLabels = remember(entries) {
        entries.orEmpty().map { it.label }.filter { it.isNotBlank() }.toSet().toList().sorted()
    }

    val grouped = remember(filteredEntries) {
        filteredEntries.groupBy { it.label.ifBlank { "Other" } }
            .toSortedMap()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SearchAppTopBar(
                title = "Key Store",
                query = query,
                onQueryChange = { query = it },
                searchActive = searchActive,
                onSearchActiveChange = { searchActive = it; if (!it) query = "" },
                onBack = onBack,
                searchPlaceholder = "Search name or label",
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AppFab(
                icon = Icons.Rounded.Add,
                contentDescription = "Add key",
                onClick = { editorState = KeyEditorState.Add },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = innerPadding.calculateTopPadding() + Spacing.sm,
                bottom = Spacing.xxl + 72.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (uniqueLabels.isNotEmpty()) {
                item(key = "filters") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(uniqueLabels) { lbl ->
                            val isSelected = selectedLabel == lbl
                            FilterChipButton(
                                text = lbl,
                                selected = isSelected,
                                onClick = { selectedLabel = if (isSelected) null else lbl },
                            )
                        }
                    }
                }
            }

            val currentEntries = entries
            if (currentEntries != null && currentEntries.isEmpty()) {
                item(key = "empty-all") {
                    EmptyState(
                        icon = Icons.Rounded.VpnKey,
                        title = "No keys saved",
                        description = "Tap + to add your first key.",
                    )
                }
            } else if (currentEntries != null && filteredEntries.isEmpty()) {
                item(key = "empty-filter") {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "No matching keys",
                    )
                }
            } else {
                grouped.forEach { (group, groupEntries) ->
                    item(key = "section-$group") {
                        SectionHeader(text = group)
                    }
                    // One card per label group rather than per entry: the per-row card
                    // border + 8dp gap was most of each row's height.
                    item(key = "group-$group") {
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            groupEntries.forEachIndexed { index, entry ->
                                KeyEntryRow(
                                    entry = entry,
                                    onClick = { actionEntry = entry },
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(entry.value))
                                        scope.launch { snackbarHostState.showSnackbar("Copied") }
                                    },
                                )
                                if (index < groupEntries.lastIndex) {
                                    RowDivider(startIndent = RowDividerIndent)
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
            onDismiss = { actionEntry = null },
            onCopy = {
                clipboard.setText(AnnotatedString(entry.value))
                scope.launch { snackbarHostState.showSnackbar("Copied") }
            },
            onEdit = {
                actionEntry = null
                editorState = KeyEditorState.Edit(entry)
            },
            onDelete = {
                actionEntry = null
                confirmDeleteEntry = entry
            },
        )
    }

    confirmDeleteEntry?.let { entry ->
        AppAlertDialog(
            onDismissRequest = { confirmDeleteEntry = null },
            title = "Delete key",
            text = "Remove \"${entry.name}\"?",
            confirmText = "Delete",
            destructiveConfirm = true,
            onConfirm = {
                confirmDeleteEntry = null
                errors.launchGuarded("Couldn't delete that key.") {
                    container.keyStoreRepository.deleteEntry(entry.entryId)
                }
            },
        )
    }

    editorState?.let { state ->
        val editing = state as? KeyEditorState.Edit
        KeyFormSheet(
            entry = editing?.entry,
            onDismiss = { editorState = null },
            onSave = { name, label, value ->
                errors.launchGuarded(
                    failureMessage = "Couldn't save that key.",
                    onFailure = { editorState = null },
                ) {
                    if (editing != null) {
                        container.keyStoreRepository.updateEntry(
                            entryId = editing.entry.entryId,
                            name = name,
                            label = label,
                            value = value,
                        )
                    } else {
                        container.keyStoreRepository.addEntry(name, label, value)
                    }
                    editorState = null
                }
            },
        )
    }
}

private val MonogramTileSize = 30.dp

/** Indent that lands the divider flush with the row's text column, past the monogram tile. */
private val RowDividerIndent = Spacing.lg + MonogramTileSize + Spacing.md

private val AccentPalette: @Composable () -> List<Color>
    get() = {
        val a = MaterialTheme.extendedColors.accents
        listOf(a.blue, a.teal, a.green, a.red, a.orange, a.yellow, a.purple, a.pink, a.indigo)
    }

@Composable
private fun accentFor(key: String): Color {
    val palette = AccentPalette()
    val idx = (kotlin.math.abs(key.hashCode())) % palette.size
    return palette[idx]
}

/**
 * A single-line key row for the grouped Key Store card. Deliberately local rather than
 * [com.daykit.core.designsystem.components.AppListRow], whose 60dp minHeight floor exists
 * for the settings switch-row rhythm — here the floor is the 48dp copy-button touch target
 * and nothing more. The label isn't repeated in the row: it's already the section header
 * above, and the monogram accent is derived from it.
 */
@Composable
private fun KeyEntryRow(
    entry: KeyStoreEntry,
    onClick: () -> Unit,
    onCopy: () -> Unit,
) {
    val accent = accentFor(entry.label.ifBlank { entry.name })
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(start = Spacing.lg, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyMonogramTile(
            letter = entry.name.trim().firstOrNull()?.uppercase() ?: "#",
            accent = accent,
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(MinTouchTarget)) {
            Icon(
                Icons.Rounded.ContentCopy,
                // Named per row so TalkBack doesn't read N identical "Copy value" buttons.
                contentDescription = "Copy value for ${entry.name}",
                tint = MaterialTheme.extendedColors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 30dp accent monogram, matching `AccentIconTile`'s geometry for a text leading slot. */
@Composable
private fun KeyMonogramTile(letter: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(MonogramTileSize)
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
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var revealed by remember(entry.entryId) { mutableStateOf(false) }
    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (entry.label.isNotBlank()) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.extendedColors.inputField)
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (revealed) entry.value else "••••••••••••",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (revealed) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (revealed) "Hide" else "Show",
                        tint = MaterialTheme.extendedColors.textMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            PrimaryButton(
                text = "Copy",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = onCopy,
            )
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

@Composable
private fun KeyFormSheet(
    entry: KeyStoreEntry?,
    onDismiss: () -> Unit,
    onSave: (name: String, label: String, value: String) -> Unit,
) {
    val editKey = entry?.entryId ?: "new"
    var name by remember(editKey) { mutableStateOf(entry?.name ?: "") }
    var value by remember(editKey) { mutableStateOf(entry?.value ?: "") }
    var confirmValue by remember(editKey) { mutableStateOf(entry?.value ?: "") }
    var label by remember(editKey) { mutableStateOf(entry?.label ?: "") }
    var valueVisible by remember(editKey) { mutableStateOf(false) }
    var confirmVisible by remember(editKey) { mutableStateOf(false) }

    val mismatch = confirmValue.isNotEmpty() && value != confirmValue
    val canSave = name.isNotBlank() && value.isNotBlank() && value == confirmValue
    val noCaps = KeyboardOptions(capitalization = KeyboardCapitalization.None)

    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = if (entry != null) "Edit key" else "Add key",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "Key name",
                keyboardOptions = noCaps,
            )
            AppTextField(
                value = value,
                onValueChange = { value = it },
                label = "Value",
                keyboardOptions = noCaps,
                visualTransformation = if (valueVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { valueVisible = !valueVisible }) {
                        Icon(
                            if (valueVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (valueVisible) "Hide" else "Show",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
            AppTextField(
                value = confirmValue,
                onValueChange = { confirmValue = it },
                label = "Confirm value",
                isError = mismatch,
                supportingText = if (mismatch) "Values do not match" else null,
                keyboardOptions = noCaps,
                visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { confirmVisible = !confirmVisible }) {
                        Icon(
                            if (confirmVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (confirmVisible) "Hide" else "Show",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
            AppTextField(
                value = label,
                onValueChange = { label = it },
                label = "Label (optional)",
                keyboardOptions = noCaps,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SecondaryButton(
                    text = "Cancel",
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                PrimaryButton(
                    text = "Save",
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    onClick = { onSave(name, label, value) },
                )
            }
        }
    }
}
