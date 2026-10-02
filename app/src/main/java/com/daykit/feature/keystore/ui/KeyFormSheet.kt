@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.keystore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.keystore.data.CardNetwork
import com.daykit.feature.keystore.data.CardNumbers
import com.daykit.feature.keystore.data.KeyDraft
import com.daykit.feature.keystore.data.KeyEntryDocument
import com.daykit.feature.keystore.data.KeyField
import com.daykit.feature.keystore.data.KeyFieldFormat
import com.daykit.feature.keystore.data.KeyFieldKind
import com.daykit.feature.keystore.data.KeyFieldSpec
import com.daykit.feature.keystore.data.KeyStoreEntry
import com.daykit.feature.keystore.data.KeyType
import com.daykit.feature.keystore.data.PasswordGenerator
import com.daykit.feature.keystore.data.PasswordGeneratorOptions
import com.daykit.feature.keystore.data.RecoveryCodes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Composable
internal fun KeyFormSheet(
    entry: KeyStoreEntry?,
    existingLabels: List<String>,
    onDismiss: () -> Unit,
    onSave: (KeyDraft) -> Unit,
) {
    val editKey = entry?.entryId ?: "new"
    var type by remember(editKey) { mutableStateOf(entry?.type ?: KeyType.Simple) }
    var name by remember(editKey) { mutableStateOf(entry?.name ?: "") }
    var label by remember(editKey) { mutableStateOf(entry?.label ?: "") }
    var fields by remember(editKey) {
        mutableStateOf(entry?.fields ?: KeyEntryDocument.normalize(KeyType.Simple, emptyList()))
    }

    fun updateField(key: String, transform: (KeyField) -> KeyField) {
        fields = fields.map { if (it.key == key) transform(it) else it }
    }

    fun changeType(newType: KeyType) {
        // Keep what still fits: values for fields the new type shares, and every added field.
        fields = KeyEntryDocument.normalize(
            newType,
            fields.filter { it.isCustom || newType.spec(it.key) != null },
        )
        type = newType
    }

    val expiryValid = fields
        .filter { it.kind == KeyFieldKind.Expiry && it.value.isNotEmpty() }
        .all { KeyFieldFormat.isValidExpiry(it.value) }
    val canSave = name.isNotBlank() && fields.any { it.value.isNotBlank() } && expiryValid
    val noCaps = KeyboardOptions(capitalization = KeyboardCapitalization.None)

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = if (entry != null) "Edit ${type.title.lowercase()}" else "Add to Key Store",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            // The type is fixed once saved: switching would orphan most of the fields.
            if (entry == null) {
                KeyTypePicker(selected = type, onSelect = ::changeType)
            }
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = type.nameLabel,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            fields.filter { !it.isCustom }.forEach { field ->
                val spec = type.spec(field.key)
                    ?: KeyFieldSpec(field.key, field.label.ifBlank { "Field" }, field.kind)
                TemplateFieldInput(
                    field = field,
                    spec = spec,
                    onValueChange = { value -> updateField(field.key) { it.copy(value = value) } },
                )
            }
            fields.filter { it.isCustom }.forEach { field ->
                CustomFieldInput(
                    field = field,
                    onChange = { updated -> updateField(field.key) { updated } },
                    onRemove = { fields = fields.filterNot { it.key == field.key } },
                )
            }
            SecondaryButton(
                text = "Add field",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    fields = fields + KeyField(
                        key = KeyType.CUSTOM_PREFIX + UUID.randomUUID().toString().take(8),
                        label = "",
                        kind = KeyFieldKind.Secret,
                        value = "",
                    )
                },
            )
            LabelInput(
                value = label,
                onValueChange = { label = it },
                existingLabels = existingLabels,
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
                    onClick = {
                        onSave(
                            KeyDraft(
                                name = name,
                                label = label,
                                type = type,
                                fields = fields.map { if (it.isCustom) it.copy(label = it.label.trim().ifBlank { "Field" }) else it },
                                usedCodes = entry?.usedCodes.orEmpty(),
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun KeyTypePicker(selected: KeyType, onSelect: (KeyType) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        KeyType.entries.forEach { type ->
            val isSelected = type == selected
            val accent = type.accent()
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) accent.asAccentContainer() else MaterialTheme.extendedColors.inputField)
                    .then(
                        if (isSelected) Modifier.border(BorderStroke(1.dp, accent), MaterialTheme.shapes.small)
                        else Modifier,
                    )
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(type) })
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    type.icon,
                    contentDescription = null,
                    tint = if (isSelected) accent else MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = type.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Free-text label, with the labels already in use offered as one-tap chips. */
@Composable
private fun LabelInput(
    value: String,
    onValueChange: (String) -> Unit,
    existingLabels: List<String>,
    keyboardOptions: KeyboardOptions,
) {
    val typed = value.trim()
    val suggestions = remember(existingLabels, typed) {
        existingLabels.filter { it != typed && it.contains(typed, ignoreCase = true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            label = "Label (optional)",
            keyboardOptions = keyboardOptions,
            trailingIcon = if (value.isNotEmpty()) {
                {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear label", modifier = Modifier.size(20.dp))
                    }
                }
            } else null,
        )
        if (suggestions.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                suggestions.forEach { suggestion ->
                    FilterChipButton(text = suggestion, selected = false, onClick = { onValueChange(suggestion) })
                }
            }
        }
    }
}

@Composable
private fun TemplateFieldInput(
    field: KeyField,
    spec: KeyFieldSpec,
    onValueChange: (String) -> Unit,
) {
    val capitalization = if (spec.uppercase) KeyboardCapitalization.Characters else KeyboardCapitalization.None
    fun sanitize(input: String) = KeyFieldFormat.sanitizeInput(spec.kind, input, spec.uppercase)
    when (spec.kind) {
        KeyFieldKind.Text, KeyFieldKind.MaskedId -> AppTextField(
            value = field.value,
            onValueChange = { onValueChange(sanitize(it)) },
            label = spec.label,
            keyboardOptions = KeyboardOptions(capitalization = capitalization),
        )
        KeyFieldKind.Url -> AppTextField(
            value = field.value,
            onValueChange = onValueChange,
            label = spec.label,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        KeyFieldKind.Secret -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SecretTextField(field.value, onValueChange, spec.label, KeyboardType.Password)
            if (spec.generator) PasswordGeneratorToggle(onGenerated = onValueChange)
        }
        KeyFieldKind.Pin -> SecretTextField(
            value = field.value,
            onValueChange = { onValueChange(sanitize(it)) },
            label = spec.label,
            keyboardType = KeyboardType.NumberPassword,
        )
        KeyFieldKind.CardNumber -> {
            val network = CardNumbers.networkOf(field.value)
            val looksWrong = field.value.length >= 12 && !CardNumbers.passesLuhn(field.value)
            AppTextField(
                value = field.value,
                onValueChange = { onValueChange(sanitize(it)) },
                label = spec.label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = SeparatorTransformation(' ') { CardNumbers.groupBreaks(it) },
                supportingText = when {
                    looksWrong -> "This number doesn't look right. Check for a typo."
                    network != CardNetwork.Unknown -> network.displayName
                    else -> null
                },
            )
        }
        KeyFieldKind.Expiry -> AppTextField(
            value = KeyFieldFormat.expiryToInput(field.value),
            onValueChange = { onValueChange(KeyFieldFormat.expiryToStored(sanitize(it))) },
            label = spec.label,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = SeparatorTransformation('/') { listOf(2) },
            isError = field.value.length == 5 && !KeyFieldFormat.isValidExpiry(field.value),
            supportingText = if (field.value.isNotEmpty() && !KeyFieldFormat.isValidExpiry(field.value)) {
                "Use MM/YY"
            } else null,
        )
        KeyFieldKind.Date -> DateFieldInput(field.value, spec.label, onValueChange)
        KeyFieldKind.Choice -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(spec.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.extendedColors.textMuted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                spec.options.forEach { option ->
                    val selected = field.value == option
                    FilterChipButton(text = option, selected = selected, onClick = {
                        onValueChange(if (selected) "" else option)
                    })
                }
            }
        }
        KeyFieldKind.SeedPhrase -> {
            val words = field.value.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            AppTextField(
                value = field.value,
                onValueChange = onValueChange,
                label = spec.label,
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                supportingText = if (words > 0) "$words words" else "Words separated by spaces",
            )
        }
        KeyFieldKind.RecoveryCodes -> {
            val count = RecoveryCodes.parse(field.value).size
            AppTextField(
                value = field.value,
                onValueChange = onValueChange,
                label = spec.label,
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                supportingText = if (count > 0) "$count codes" else "Paste the codes, one per line",
            )
        }
    }
}

/** A field the user added: their own name for it, and whether it's masked. */
@Composable
private fun CustomFieldInput(
    field: KeyField,
    onChange: (KeyField) -> Unit,
    onRemove: () -> Unit,
) {
    val secret = field.kind == KeyFieldKind.Secret
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextField(
                value = field.label,
                onValueChange = { onChange(field.copy(label = it)) },
                placeholder = "Field name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                onChange(field.copy(kind = if (secret) KeyFieldKind.Text else KeyFieldKind.Secret))
            }) {
                Icon(
                    if (secret) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = if (secret) "Hidden field, tap to show it plainly" else "Plain field, tap to hide it",
                    tint = if (secret) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove field",
                    tint = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (secret) {
            SecretTextField(field.value, { onChange(field.copy(value = it)) }, "Value", KeyboardType.Password)
        } else {
            AppTextField(
                value = field.value,
                onValueChange = { onChange(field.copy(value = it)) },
                label = "Value",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            )
        }
    }
}

@Composable
private fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
) {
    var visible by remember { mutableStateOf(false) }
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = keyboardType,
            autoCorrectEnabled = false,
        ),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

@Composable
private fun DateFieldInput(value: String, label: String, onValueChange: (String) -> Unit) {
    var pickerOpen by remember { mutableStateOf(false) }
    Box {
        AppTextField(
            value = if (value.isBlank()) "" else KeyFieldFormat.formatDate(value),
            onValueChange = {},
            label = label,
            trailingIcon = {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
            },
        )
        // The field is display-only; the whole box opens the picker.
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = "Pick $label") { pickerOpen = true },
        )
    }
    if (pickerOpen) {
        val initial = KeyFieldFormat.parseDate(value) ?: LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    pickerOpen = false
                }) { Text("Select") }
            },
            dismissButton = {
                Row {
                    if (value.isNotBlank()) {
                        TextButton(onClick = { onValueChange(""); pickerOpen = false }) { Text("Clear") }
                    }
                    TextButton(onClick = { pickerOpen = false }) { Text("Cancel") }
                }
            },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.extendedColors.card),
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun PasswordGeneratorToggle(onGenerated: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var length by remember { mutableStateOf(20) }
    var useUppercase by remember { mutableStateOf(true) }
    var useLowercase by remember { mutableStateOf(true) }
    var useNumbers by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }

    SecondaryButton(
        text = if (expanded) "Hide generator" else "Generate password",
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Rounded.Password, contentDescription = null, modifier = Modifier.size(18.dp)) },
        onClick = { expanded = !expanded },
    )
    if (expanded) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text("Password generator", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Length", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { length = (length - 1).coerceAtLeast(8) }) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Shorter")
                    }
                    Text("$length", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { length = (length + 1).coerceAtMost(128) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Longer")
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // Each toggle refuses to turn off the last enabled character set.
                FilterChipButton("Uppercase", useUppercase) {
                    if (!useUppercase || useLowercase || useNumbers || useSymbols) useUppercase = !useUppercase
                }
                FilterChipButton("Lowercase", useLowercase) {
                    if (!useLowercase || useUppercase || useNumbers || useSymbols) useLowercase = !useLowercase
                }
                FilterChipButton("Numbers", useNumbers) {
                    if (!useNumbers || useUppercase || useLowercase || useSymbols) useNumbers = !useNumbers
                }
                FilterChipButton("Symbols", useSymbols) {
                    if (!useSymbols || useUppercase || useLowercase || useNumbers) useSymbols = !useSymbols
                }
            }
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(
                text = "Use generated password",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onGenerated(
                        PasswordGenerator.generate(
                            PasswordGeneratorOptions(
                                length = length,
                                uppercase = useUppercase,
                                lowercase = useLowercase,
                                numbers = useNumbers,
                                symbols = useSymbols,
                            ),
                        ),
                    )
                },
            )
        }
    }
}

/**
 * Shows [separator] before each index [breaksFor] returns, without putting it in the stored
 * text: the value stays digits-only while the field reads `4242 4242 …` or `08/28`.
 */
private class SeparatorTransformation(
    private val separator: Char,
    private val breaksFor: (String) -> List<Int>,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        // A separator only appears once there's a character after it.
        val breaks = breaksFor(raw).filter { it in 1 until raw.length }
        val out = buildString {
            raw.forEachIndexed { index, c ->
                if (index in breaks) append(separator)
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + breaks.count { it < offset }

            override fun transformedToOriginal(offset: Int): Int =
                (offset - breaks.withIndex().count { (k, b) -> b + k < offset }).coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }

    override fun equals(other: Any?): Boolean = other is SeparatorTransformation && other.separator == separator
    override fun hashCode(): Int = separator.hashCode()
}
