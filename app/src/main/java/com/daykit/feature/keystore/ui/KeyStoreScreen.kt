@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.keystore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import com.daykit.AppContainer
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AppAlertDialog
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppFab
import com.daykit.core.designsystem.components.AppSearchBar
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.DestructiveButton
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.security.BiometricAuthenticator
import com.daykit.feature.keystore.data.KeyStoreEntry
import com.daykit.feature.lock.ui.ToolUnlockScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val biometricAuthenticator = remember(activity) { BiometricAuthenticator(activity) }
    var unlocked by remember { mutableStateOf(false) }
    var unlockPin by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var biometricEnabled by remember { mutableStateOf(false) }
    var toolLocked by remember { mutableStateOf<Boolean?>(null) }
    val isToolLocked = toolLocked
    val entries by container.keyStoreRepository
        .observeEntries()
        .collectAsStateWithLifecycle(initialValue = null)
    var editorState by remember { mutableStateOf<KeyEditorState?>(null) }
    var actionEntry by remember { mutableStateOf<KeyStoreEntry?>(null) }
    var confirmDeleteEntry by remember { mutableStateOf<KeyStoreEntry?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler {
        if (query.isNotEmpty() || selectedLabel != null) {
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

    fun tryBiometricUnlock(enabled: Boolean = biometricEnabled) {
        if (!enabled || !biometricAuthenticator.canAuthenticate()) return
        biometricAuthenticator.authenticate(
            title = "Unlock Key Store",
            subtitle = "DayKit secure storage",
            onSuccess = {
                unlocked = true
                unlockPin = ""
                unlockError = null
            },
            onError = { unlockError = it },
        )
    }

    LaunchedEffect(Unit) {
        val storedToolLocked = container.secureSettingRepository
            .getBoolean(SecureSettingRepository.KEY_TOOL_LOCK_KEY_STORE) != false
        val storedBiometricEnabled = container.secureSettingRepository
            .getBoolean(SecureSettingRepository.KEY_BIOMETRIC_ENABLED) == true
        biometricEnabled = storedBiometricEnabled
        if (storedToolLocked && storedBiometricEnabled) {
            tryBiometricUnlock(storedBiometricEnabled)
        }
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_TOOL_LOCK_KEY_STORE)
            .collect { locked ->
                toolLocked = locked ?: true
            }
    }

    LaunchedEffect(isToolLocked) {
        if (isToolLocked == false) {
            unlocked = true
            unlockPin = ""
            unlockError = null
        }
    }

    if (isToolLocked == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator()
        }
        return
    }

    if (isToolLocked == true && !unlocked) {
        ToolUnlockScreen(
            title = "Key Store",
            subtitle = "Unlock secure storage",
            pin = unlockPin,
            error = unlockError,
            biometricEnabled = biometricEnabled,
            icon = Icons.Rounded.VpnKey,
            onBack = onBack,
            onPinChange = {
                unlockPin = it.filter(Char::isDigit).take(12)
                unlockError = null
            },
            onUnlock = {
                scope.launch {
                    val pin = unlockPin
                    val valid = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (valid) {
                        unlocked = true
                        unlockPin = ""
                    } else {
                        unlockError = "Wrong PIN"
                        unlockPin = ""
                    }
                }
            },
            onBiometric = { tryBiometricUnlock() },
        )
        return
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
            AppTopBar(
                title = "Key Store",
                onBack = onBack,
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
        Box(modifier = Modifier.fillMaxSize()) {
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
                item(key = "header") {
                    Text(
                        text = "${filteredEntries.size} of ${entries?.size ?: 0} saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textMuted,
                    )
                }
                item(key = "search") {
                    AppSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search name or label",
                    )
                }
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
                            SectionHeader(text = group, modifier = Modifier.padding(top = Spacing.xs))
                        }
                        items(groupEntries, key = { it.entryId }) { entry ->
                            KeyEntryCard(
                                entry = entry,
                                onClick = { actionEntry = entry },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(entry.value))
                                    scope.launch { snackbarHostState.showSnackbar("Copied") }
                                },
                            )
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
                scope.launch {
                    container.keyStoreRepository.deleteEntry(entry.entryId)
                    confirmDeleteEntry = null
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
                scope.launch {
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

@Composable
private fun KeyEntryCard(
    entry: KeyStoreEntry,
    onClick: () -> Unit,
    onCopy: () -> Unit,
) {
    val accent = accentFor(entry.label.ifBlank { entry.name })
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.label.isNotBlank()) {
                        Spacer(Modifier.width(Spacing.sm))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.asAccentContainer())
                                .padding(horizontal = Spacing.sm, vertical = 2.dp),
                        ) {
                            Text(
                                text = entry.label,
                                color = accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "••••••••",
                    color = MaterialTheme.extendedColors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Copy value",
                    tint = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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
