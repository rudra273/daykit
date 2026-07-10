@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.daykit.feature.notes.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBackButton
import com.daykit.core.designsystem.components.AppAlertDialog
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppFab
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.SearchAppTopBar
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.security.BiometricAuthenticator
import com.daykit.core.security.errorMessageOrNull
import com.daykit.feature.lock.ui.ToolUnlockScreen
import com.daykit.feature.notes.data.SecureNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface NoteEditorState {
    data object Add : NoteEditorState
    data class Edit(val note: SecureNote) : NoteEditorState
}

@Composable
fun SecureNotesScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val biometricAuthenticator = remember(activity) { BiometricAuthenticator(activity) }
    var unlocked by remember { mutableStateOf(false) }
    var unlockPin by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var biometricEnabled by remember { mutableStateOf(false) }
    var toolLocked by remember { mutableStateOf<Boolean?>(null) }
    val isToolLocked = toolLocked
    var editorState by remember { mutableStateOf<NoteEditorState?>(null) }
    var actionNote by remember { mutableStateOf<SecureNote?>(null) }
    var confirmDeleteNote by remember { mutableStateOf<SecureNote?>(null) }
    var query by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    val notes by container.secureNoteRepository
        .observeNotes()
        .collectAsStateWithLifecycle(initialValue = null)
    val imagesByNote by container.secureNoteRepository
        .observeImagesByNote()
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    fun tryBiometricUnlock(enabled: Boolean = biometricEnabled) {
        if (!enabled || !biometricAuthenticator.canAuthenticate()) return
        biometricAuthenticator.authenticate(
            title = "Unlock Notes",
            subtitle = "DayKit secure notes",
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
            .getBoolean(SecureSettingRepository.KEY_TOOL_LOCK_NOTES) != false
        val storedBiometricEnabled = container.secureSettingRepository
            .getBoolean(SecureSettingRepository.KEY_BIOMETRIC_ENABLED) == true
        biometricEnabled = storedBiometricEnabled
        if (storedToolLocked && storedBiometricEnabled) {
            tryBiometricUnlock(storedBiometricEnabled)
        }
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_TOOL_LOCK_NOTES)
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

    BackHandler {
        when {
            searchActive -> {
                searchActive = false
                query = ""
                selectedLabel = null
            }
            query.isNotBlank() || selectedLabel != null -> {
                query = ""
                selectedLabel = null
            }
            else -> onBack()
        }
    }

    if (isToolLocked == null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }
        return
    }

    if (isToolLocked == true && !unlocked) {
        ToolUnlockScreen(
            title = "Notes",
            subtitle = "Unlock private notes",
            pin = unlockPin,
            error = unlockError,
            biometricEnabled = biometricEnabled,
            icon = Icons.Rounded.Notes,
            onBack = onBack,
            onPinChange = {
                unlockPin = it.filter(Char::isDigit).take(12)
                unlockError = null
            },
            onUnlock = {
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(unlockPin.toCharArray())
                    }
                    if (result is com.daykit.core.security.PinVerifyResult.Success) {
                        unlocked = true
                        unlockPin = ""
                    } else {
                        unlockError = result.errorMessageOrNull()
                        unlockPin = ""
                    }
                }
            },
            onBiometric = { tryBiometricUnlock() },
        )
        return
    }

    val filteredNotes = remember(notes, query, selectedLabel) {
        notes.orEmpty().filter { note ->
            val matchesQuery = query.isBlank() ||
                note.title.contains(query, ignoreCase = true) ||
                note.content.contains(query, ignoreCase = true) ||
                note.labels.contains(query, ignoreCase = true)
            val labels = note.labelList()
            val matchesLabel = selectedLabel == null || selectedLabel in labels
            matchesQuery && matchesLabel
        }
    }
    val uniqueLabels = remember(notes) {
        notes.orEmpty().flatMap { it.labelList() }.distinct().sorted()
    }

    editorState?.let { currentEditor ->
        NoteEditorPage(
            state = currentEditor,
            existingLabels = uniqueLabels,
            repository = container.secureNoteRepository,
            onExpectActivityResult = {
                container.sensitiveKeyManager.expectingActivityResult = true
            },
            onClose = { editorState = null },
        )
        return
    }

    val gridState = rememberLazyStaggeredGridState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SearchAppTopBar(
                title = "Notes",
                query = query,
                onQueryChange = { query = it },
                searchActive = searchActive,
                onSearchActiveChange = { searchActive = it; if (!it) query = "" },
                onBack = onBack,
                searchPlaceholder = "Search notes",
            )
        },
        floatingActionButton = {
            AppFab(
                icon = Icons.Rounded.Add,
                contentDescription = "New note",
                onClick = { editorState = NoteEditorState.Add },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val currentNotes = notes) {
                null -> Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }

                else -> LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(2),
                    verticalItemSpacing = Spacing.md,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = innerPadding.calculateTopPadding() + Spacing.sm,
                        bottom = Spacing.xxl + 72.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (uniqueLabels.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.sm),
                            ) {
                                uniqueLabels.forEach { label ->
                                    FilterChipButton(
                                        text = label,
                                        selected = selectedLabel == label,
                                        onClick = {
                                            selectedLabel = if (selectedLabel == label) null else label
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (currentNotes.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            EmptyState(
                                icon = Icons.Rounded.Notes,
                                title = "No secure notes",
                                description = "Tap + to write your first note.",
                                modifier = Modifier.padding(top = Spacing.xxl),
                            )
                        }
                    } else if (filteredNotes.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            EmptyState(
                                icon = Icons.Rounded.SearchOff,
                                title = "No matching notes",
                                modifier = Modifier.padding(top = Spacing.xxl),
                            )
                        }
                    }

                    itemsIndexed(filteredNotes, key = { _, it -> it.noteId }) { _, note ->
                        NoteCard(
                            note = note,
                            previewImage = imagesByNote[note.noteId]?.firstOrNull(),
                            onClick = { editorState = NoteEditorState.Edit(note) },
                            onLongPress = { actionNote = note },
                        )
                    }
                }
            }
        }
    }

    actionNote?.let { note ->
        AppBottomSheet(onDismissRequest = { actionNote = null }) {
            NoteActionRow(
                icon = Icons.Rounded.Edit,
                text = "Edit",
                onClick = {
                    val target = note
                    actionNote = null
                    editorState = NoteEditorState.Edit(target)
                },
            )
            NoteActionRow(
                icon = Icons.Rounded.Delete,
                text = "Delete",
                destructive = true,
                onClick = {
                    actionNote = null
                    confirmDeleteNote = note
                },
            )
            Spacer(Modifier.height(Spacing.sm))
        }
    }

    confirmDeleteNote?.let { note ->
        AppAlertDialog(
            onDismissRequest = { confirmDeleteNote = null },
            title = "Delete note",
            text = "Remove this note?",
            confirmText = "Delete",
            destructiveConfirm = true,
            onConfirm = {
                scope.launch {
                    container.secureNoteRepository.deleteNote(note.noteId)
                    confirmDeleteNote = null
                }
            },
        )
    }
}

@Composable
private fun NoteCard(
    note: SecureNote,
    previewImage: com.daykit.feature.notes.data.SecureNoteImage?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        contentPadding = PaddingValues(Spacing.md),
    ) {
        if (previewImage != null) {
            val bitmap = remember(previewImage.imageId) {
                android.graphics.BitmapFactory
                    .decodeByteArray(previewImage.bytes, 0, previewImage.bytes.size)
                    ?.asImageBitmap()
            }
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        Text(
            text = note.title.ifBlank { "Untitled" },
            style = MaterialTheme.typography.titleMedium,
            color = if (note.title.isBlank()) {
                MaterialTheme.extendedColors.textMuted
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val preview = note.content.trim()
        if (preview.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val labels = note.labelList()
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                labels.forEach { label ->
                    NoteLabelChip(label)
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = relativeDate(note.updatedAtMillis),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.extendedColors.textMuted,
        )
    }
}

@Composable
private fun NoteLabelChip(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.extendedColors.inputField,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
    }
}

@Composable
private fun NoteActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) MaterialTheme.extendedColors.danger else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Spacing.md))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun NoteEditorPage(
    state: NoteEditorState,
    existingLabels: List<String>,
    repository: com.daykit.feature.notes.data.SecureNoteRepository,
    onExpectActivityResult: () -> Unit,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val editingNote = (state as? NoteEditorState.Edit)?.note
    var title by remember(editingNote?.noteId) { mutableStateOf(editingNote?.title.orEmpty()) }
    var content by remember(editingNote?.noteId) { mutableStateOf(editingNote?.content.orEmpty()) }
    var selectedLabels by remember(editingNote?.noteId) {
        mutableStateOf(editingNote?.labelList()?.toSet().orEmpty())
    }
    var labelSheetOpen by remember { mutableStateOf(false) }
    // Persisted id: non-null once the note exists in the DB (immediately for edits,
    // or after the first image is attached to a new note).
    var persistedNoteId by remember(editingNote?.noteId) { mutableStateOf(editingNote?.noteId) }
    var images by remember(editingNote?.noteId) {
        mutableStateOf<List<com.daykit.feature.notes.data.SecureNoteImage>>(emptyList())
    }

    LaunchedEffect(editingNote?.noteId) {
        val id = editingNote?.noteId
        if (id != null) images = repository.getImages(id)
    }

    val cleanTitleFor = { title.ifBlank { "Untitled" } }
    val cleanContentFor = { content.ifBlank { " " } }
    val labelsString = { selectedLabels.sorted().joinToString(", ") }

    // Ensures the note is saved and returns its id (creates it if new).
    suspend fun ensureNoteId(): String {
        persistedNoteId?.let { existing ->
            repository.updateNote(existing, cleanTitleFor(), cleanContentFor(), labelsString())
            return existing
        }
        val newId = repository.addNote(cleanTitleFor(), cleanContentFor(), labelsString())
        persistedNoteId = newId
        return newId
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { decodeImageBytes(context, uri) }
                if (bytes != null) {
                    val noteId = ensureNoteId()
                    repository.addImage(noteId, bytes)
                    images = repository.getImages(noteId)
                }
            }
        }
    }

    fun finish() {
        scope.launch {
            val existing = persistedNoteId
            if (existing != null) {
                // Note already persisted (e.g. images attached) — update text/labels.
                if (title.isNotBlank() || content.isNotBlank() || images.isNotEmpty()) {
                    repository.updateNote(existing, cleanTitleFor(), cleanContentFor(), labelsString())
                }
            } else if (title.isNotBlank() || content.isNotBlank()) {
                repository.addNote(cleanTitleFor(), cleanContentFor(), labelsString())
            }
            onClose()
        }
    }

    BackHandler { finish() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "",
                onBack = { finish() },
                actions = {
                    androidx.compose.material3.IconButton(
                        onClick = {
                            onExpectActivityResult()
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = "Add image",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = { labelSheetOpen = true }) {
                        Icon(
                            Icons.Rounded.Label,
                            contentDescription = "Add label",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xl)
                    .imePadding(),
            ) {
                NotePlainField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Title",
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.lg),
                )
                val selectedList = selectedLabels.sorted()
                if (selectedList.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        selectedList.forEach { label ->
                            FilterChipButton(
                                text = label,
                                selected = true,
                                onClick = { selectedLabels = selectedLabels - label },
                            )
                        }
                    }
                }
                if (images.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.md))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        images.forEach { image ->
                            EditorImageThumbnail(
                                image = image,
                                onDelete = {
                                    scope.launch {
                                        repository.deleteImage(image.imageId)
                                        persistedNoteId?.let { images = repository.getImages(it) }
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                NotePlainField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = "Write something…",
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.xxl),
                )
            }
        }
    }

    if (labelSheetOpen) {
        LabelSheet(
            existingLabels = existingLabels,
            selectedLabels = selectedLabels,
            onToggleLabel = { label ->
                selectedLabels = if (label in selectedLabels) {
                    selectedLabels - label
                } else {
                    selectedLabels + label
                }
            },
            onCreateLabel = { label -> selectedLabels = selectedLabels + label.trim() },
            onDismiss = { labelSheetOpen = false },
        )
    }
}

@Composable
private fun EditorImageThumbnail(
    image: com.daykit.feature.notes.data.SecureNoteImage,
    onDelete: () -> Unit,
) {
    val bitmap = remember(image.imageId) {
        android.graphics.BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.extendedColors.inputField),
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = "Note image",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        androidx.compose.material3.Surface(
            onClick = onDelete,
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove image",
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

/** Decodes and downscales the picked image to a reasonable-size JPEG byte array. */
private fun decodeImageBytes(
    context: android.content.Context,
    uri: android.net.Uri,
): ByteArray? {
    return runCatching {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.isMutableRequired = false
            val maxDimension = 1600
            val (w, h) = info.size.width to info.size.height
            val largest = maxOf(w, h)
            if (largest > maxDimension) {
                val scale = maxDimension.toFloat() / largest
                decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            }
        }
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
        bitmap.recycle()
        stream.toByteArray()
    }.getOrNull()
}

@Composable
private fun LabelSheet(
    existingLabels: List<String>,
    selectedLabels: Set<String>,
    onToggleLabel: (String) -> Unit,
    onCreateLabel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newLabel by remember { mutableStateOf("") }
    val cleanNewLabel = newLabel.trim()
    val visibleLabels = remember(existingLabels, selectedLabels) {
        (existingLabels + selectedLabels).map { it.trim() }
            .filter { it.isNotBlank() }.distinct().sorted()
    }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                "Labels",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (visibleLabels.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    visibleLabels.forEach { label ->
                        FilterChipButton(
                            text = label,
                            selected = label in selectedLabels,
                            onClick = { onToggleLabel(label) },
                        )
                    }
                }
            }
            AppTextField(
                value = newLabel,
                onValueChange = { newLabel = it },
                placeholder = "New label",
                trailingIcon = {
                    androidx.compose.material3.IconButton(
                        enabled = cleanNewLabel.isNotBlank(),
                        onClick = {
                            onCreateLabel(cleanNewLabel)
                            newLabel = ""
                        },
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "Add label",
                            tint = if (cleanNewLabel.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.extendedColors.textMuted
                            },
                        )
                    }
                },
            )
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

@Composable
private fun NotePlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    Box(modifier = modifier) {
        if (value.isEmpty()) {
            Text(placeholder, style = textStyle, color = MaterialTheme.extendedColors.textMuted)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun relativeDate(millis: Long): String {
    if (millis <= 0L) return ""
    return DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

private fun SecureNote.labelList(): List<String> {
    return labels.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
