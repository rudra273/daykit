@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.settings.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.security.CredentialKind
import com.daykit.core.security.CredentialRepository
import com.daykit.core.security.PinVerifyResult
import com.daykit.core.security.errorMessageOrNull
import com.daykit.core.security.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared shell for a settings sub-page: back handling, top bar, scrolling list. */
@Composable
internal fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = title, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.xs,
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}

/** Muted explanatory text under a settings card. */
@Composable
internal fun SettingsFootnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.extendedColors.textMuted,
        modifier = Modifier.padding(horizontal = Spacing.sm),
    )
}

/** A bottom sheet listing [options] with a check on [selected]. */
@Composable
internal fun <T> OptionSheet(
    title: String,
    description: String?,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }
        }
        options.forEach { option ->
            AppListRow(
                headline = label(option),
                trailing = if (option == selected) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    null
                },
                onClick = { onSelect(option) },
            )
        }
    }
}

/**
 * State for a setting whose relaxing direction needs the master credential,
 * like Auto-Lock or clipboard clearing. Call [request] with the new value and
 * whether it weakens protection; weakening shows a PIN sheet first.
 */
internal class PinGatedChange<T>(
    val pending: T?,
    val error: String?,
    val request: (value: T, weakens: Boolean) -> Unit,
)

@Composable
internal fun <T : Any> rememberPinGatedChange(
    container: AppContainer,
    title: String,
    message: (T) -> String,
    apply: (T) -> Unit,
): PinGatedChange<T> {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<T?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val credentialKind = remember { container.credentialRepository.credentialKind() }

    pending?.let { value ->
        ConfirmPinSheet(
            credentialKind = credentialKind,
            title = title,
            message = message(value),
            error = error,
            onDismiss = {
                pending = null
                error = null
            },
            onConfirm = { pin ->
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (result is PinVerifyResult.Success) {
                        apply(value)
                        pending = null
                        error = null
                    } else {
                        error = result.errorMessageOrNull(credentialKind)
                    }
                }
            },
        )
    }

    return PinGatedChange(pending, error) { value, weakens ->
        if (weakens) {
            error = null
            pending = value
        } else {
            apply(value)
        }
    }
}

@Composable
internal fun NavChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.extendedColors.textMuted,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
internal fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    kind: CredentialKind,
    label: String,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    AppTextField(
        value = value,
        onValueChange = { onValueChange(CredentialRepository.sanitize(it, kind)) },
        label = label,
        isError = isError,
        supportingText = supportingText,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (kind == CredentialKind.Pin) KeyboardType.NumberPassword else KeyboardType.Password,
            autoCorrectEnabled = false,
        ),
    )
}

@Composable
internal fun ConfirmPinSheet(
    credentialKind: CredentialKind,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit,
    error: String? = null,
    showFingerprint: Boolean = false,
    onFingerprint: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    // Clear the PIN field whenever a new error arrives from the caller.
    LaunchedEffect(error) {
        if (error != null) pin = ""
    }

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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
            )
            CredentialField(
                value = pin,
                onValueChange = { pin = it },
                kind = credentialKind,
                label = "Master ${credentialKind.label}",
                isError = error != null,
                supportingText = error,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showFingerprint && onFingerprint != null) {
                    SecondaryButton(
                        text = "Fingerprint",
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = onFingerprint,
                    )
                    Spacer(Modifier.weight(1f))
                }
                AppTextButton(
                    text = "Cancel",
                    color = MaterialTheme.extendedColors.textMuted,
                    onClick = onDismiss,
                )
                PrimaryButton(
                    text = "Confirm",
                    enabled = pin.isNotEmpty(),
                    onClick = { onConfirm(pin) },
                )
            }
        }
    }
}

/** Small rounded status label: green when [ok], amber otherwise. */
@Composable
internal fun StatusPill(text: String, ok: Boolean) {
    val colors = MaterialTheme.extendedColors
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (ok) colors.successContainer else colors.warningContainer)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (ok) colors.success else colors.warning,
        )
    }
}
