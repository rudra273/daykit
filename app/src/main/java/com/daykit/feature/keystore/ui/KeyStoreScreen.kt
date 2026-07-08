package com.daykit.feature.keystore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import com.daykit.AppContainer
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.security.BiometricAuthenticator
import com.daykit.core.ui.AppBackButton
import com.daykit.core.ui.Cyan
import com.daykit.core.ui.GlassBackground
import com.daykit.core.ui.GlassFilterButton
import com.daykit.core.ui.GlassLoadingIndicator
import com.daykit.core.ui.MutedText
import com.daykit.core.ui.PanelAlt
import com.daykit.core.ui.SoftText
import com.daykit.core.ui.Stroke
import com.daykit.core.ui.Teal
import com.daykit.core.ui.Amber
import com.daykit.core.ui.AmberMuted
import com.daykit.core.ui.PrimaryButton
import com.daykit.core.ui.SecondaryButton
import com.daykit.core.ui.glassSurface
import com.daykit.feature.keystore.data.KeyStoreEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface KeyEditorState {
    data object Add : KeyEditorState
    data class Edit(val entry: KeyStoreEntry) : KeyEditorState
}

private data class KeyEditDraft(
    val name: String,
    val label: String,
    val value: String,
)

@Composable
fun KeyStoreScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
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
    var visibleEntries by remember { mutableStateOf(emptySet<String>()) }
    var query by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var isSearchFocused by remember { mutableStateOf(false) }

    BackHandler {
        if (isSearchFocused) {
            focusManager.clearFocus()
        } else if (editorState != null) {
            editorState = null
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
        GlassBackground {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassLoadingIndicator()
            }
        }
        return
    }

    if (isToolLocked == true && !unlocked) {
        KeyStoreUnlockScreen(
            pin = unlockPin,
            error = unlockError,
            biometricEnabled = biometricEnabled,
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

    if (editorState is KeyEditorState.Add) {
        AddKeyScreen(
            onBack = { editorState = null },
            onSave = { name, label, value ->
                scope.launch {
                    container.keyStoreRepository.addEntry(name, label, value)
                    editorState = null
                }
            },
        )
        return
    }

    GlassBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppBackButton(onClick = onBack)
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Key Store", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${filteredEntries.size} of ${entries?.size ?: 0} saved", color = Amber, style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search name or label", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber,
                        unfocusedBorderColor = Stroke,
                        focusedTextColor = SoftText,
                        unfocusedTextColor = SoftText,
                        cursorColor = Amber,
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { isSearchFocused = it.isFocused }
                )

                val uniqueLabels = remember(entries) {
                    entries.orEmpty().map { it.label }.filter { it.isNotBlank() }.toSet().toList().sorted()
                }

                if (uniqueLabels.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uniqueLabels) { lbl ->
                            val isSelected = selectedLabel == lbl
                            GlassFilterButton(
                                text = lbl,
                                selected = isSelected,
                                onClick = { selectedLabel = if (isSelected) null else lbl },
                            )
                        }
                    }
                }

                when (val currentEntries = entries) {
                    null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlassLoadingIndicator()
                    }

                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (currentEntries.isEmpty()) {
                            item {
                                EmptyState()
                            }
                        }
                        if (currentEntries.isNotEmpty() && filteredEntries.isEmpty()) {
                            item {
                                EmptyState(text = "No matching keys")
                            }
                        }
                        items(filteredEntries, key = { it.entryId }) { entry ->
                            KeyEntryRow(
                                entry = entry,
                                visible = entry.entryId in visibleEntries,
                                onToggleVisible = {
                                    visibleEntries = if (entry.entryId in visibleEntries) {
                                        visibleEntries - entry.entryId
                                    } else {
                                        visibleEntries + entry.entryId
                                    }
                                },
                                onLongPress = { actionEntry = entry },
                            )
                        }
                    }
                }
            }
            PrimaryButton(
                text = "Add Key",
                onClick = { editorState = KeyEditorState.Add },
                leadingIcon = {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 18.dp),
            )
        }
    }

    actionEntry?.let { entry ->
        KeyActionsDialog(
            entry = entry,
            onDismiss = { actionEntry = null },
            onUpdate = {
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
        ConfirmDeleteDialog(
            entry = entry,
            onDismiss = { confirmDeleteEntry = null },
            onConfirm = {
                scope.launch {
                    container.keyStoreRepository.deleteEntry(entry.entryId)
                    visibleEntries = visibleEntries - entry.entryId
                    confirmDeleteEntry = null
                }
            },
        )
    }

    (editorState as? KeyEditorState.Edit)?.let { editState ->
        UpdateKeyDialog(
            entry = editState.entry,
            onDismiss = { editorState = null },
            onSave = { draft ->
                scope.launch {
                    container.keyStoreRepository.updateEntry(
                        entryId = editState.entry.entryId,
                        name = draft.name,
                        label = draft.label,
                        value = draft.value,
                    )
                    editorState = null
                }
            },
        )
    }
}

@Composable
private fun KeyStoreUnlockScreen(
    pin: String,
    error: String?,
    biometricEnabled: Boolean,
    onBack: () -> Unit,
    onPinChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onBiometric: () -> Unit,
) {
    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppBackButton(onClick = onBack)
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Key Store", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Unlock secure storage", color = Amber, style = MaterialTheme.typography.bodySmall)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(RoundedCornerShape(18.dp), selected = false)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AmberMuted),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Master PIN", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                }
                SecureTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    label = "Enter PIN",
                    secure = true,
                )
                error?.let {
                    Text(it, color = Color(0xFFFFA8A8), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (biometricEnabled) {
                        SecondaryButton(
                            text = "Fingerprint",
                            leadingIcon = {
                                Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = Cyan, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f),
                            onClick = onBiometric
                        )
                    }
                    PrimaryButton(
                        text = "Unlock",
                        enabled = pin.length >= 4,
                        modifier = Modifier.weight(if (biometricEnabled) 1f else 1f),
                        onClick = onUnlock
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun AddKeyScreen(
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    BackHandler { onBack() }
    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppBackButton(onClick = onBack)
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Add Key", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Create secure entry", color = Amber, style = MaterialTheme.typography.bodySmall)
                }
            }
            AddKeyCard(onCancel = onBack, onSave = onSave)
        }
    }
}

@Composable
private fun AddKeyCard(
    onCancel: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var confirmValue by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    val canSave = name.isNotBlank() && value.isNotBlank() && value == confirmValue

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(18.dp), selected = false),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Add New Key",
                    fontWeight = FontWeight.SemiBold,
                    color = Amber
                )
            }
            SecureTextField(value = name, onValueChange = { name = it }, label = "Key name")
            SecureTextField(value = value, onValueChange = { value = it }, label = "Value", secure = true)
            SecureTextField(value = confirmValue, onValueChange = { confirmValue = it }, label = "Confirm value", secure = true)
            SecureTextField(value = label, onValueChange = { label = it }, label = "Label (Optional)")
            if (confirmValue.isNotEmpty() && value != confirmValue) {
                Text("Values do not match", color = Color(0xFFFFA8A8), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
                PrimaryButton(text = "Save", enabled = canSave, modifier = Modifier.weight(1f), onClick = { onSave(name, label, value) })
            }
        }
    }
}

@Composable
private fun KeyEntryRow(
    entry: KeyStoreEntry,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onLongPress: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(18.dp), selected = false)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress,
            ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (entry.label.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = entry.label,
                                color = Cyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (visible) entry.value else "••••••••••••••••",
                    color = if (visible) SoftText else MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = onToggleVisible,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "Toggle visibility",
                        tint = if (visible) Cyan else MutedText,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(entry.value)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy value",
                        tint = SoftText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyActionsDialog(
    entry: KeyStoreEntry,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose an action", color = MutedText, style = MaterialTheme.typography.bodyMedium)
                if (entry.label.isNotBlank()) {
                    Text(entry.label, color = Amber, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text("Update", color = Amber, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = Color(0xFFFFA8A8))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MutedText)
                }
            }
        },
        containerColor = PanelAlt,
        titleContentColor = SoftText,
        textContentColor = SoftText,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun ConfirmDeleteDialog(
    entry: KeyStoreEntry,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to remove '${entry.name}'?", color = MutedText, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = Color(0xFFFFA8A8), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        },
        containerColor = PanelAlt,
        titleContentColor = SoftText,
        textContentColor = SoftText,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun UpdateKeyDialog(
    entry: KeyStoreEntry,
    onDismiss: () -> Unit,
    onSave: (KeyEditDraft) -> Unit,
) {
    var name by remember(entry.entryId) { mutableStateOf(entry.name) }
    var value by remember(entry.entryId) { mutableStateOf(entry.value) }
    var confirmValue by remember(entry.entryId) { mutableStateOf(entry.value) }
    var label by remember(entry.entryId) { mutableStateOf(entry.label) }
    val canSave = name.isNotBlank() && value.isNotBlank() && value == confirmValue

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecureTextField(value = name, onValueChange = { name = it }, label = "Key name")
                SecureTextField(value = value, onValueChange = { value = it }, label = "Value", secure = true)
                SecureTextField(value = confirmValue, onValueChange = { confirmValue = it }, label = "Confirm value", secure = true)
                SecureTextField(value = label, onValueChange = { label = it }, label = "Label (Optional)")
                if (confirmValue.isNotEmpty() && value != confirmValue) {
                    Text("Values do not match", color = Color(0xFFFFA8A8), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        KeyEditDraft(
                            name = name,
                            label = label,
                            value = value,
                        ),
                    )
                },
            ) {
                Text("Update", color = if (canSave) Amber else MutedText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        },
        containerColor = PanelAlt,
        titleContentColor = SoftText,
        textContentColor = SoftText,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun SecureTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secure: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        leadingIcon = leadingIcon,
        singleLine = !secure,
        visualTransformation = if (secure && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secure) {
            {
                IconButton(onClick = { visible = !visible }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Hide" else "Show",
                        tint = MutedText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Amber,
            unfocusedBorderColor = Stroke,
            focusedLabelColor = Amber,
            unfocusedLabelColor = MutedText,
            cursorColor = Amber,
            focusedTextColor = SoftText,
            unfocusedTextColor = SoftText,
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyState(text: String = "No keys saved") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .glassSurface(RoundedCornerShape(18.dp), selected = false),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MutedText)
    }
}
