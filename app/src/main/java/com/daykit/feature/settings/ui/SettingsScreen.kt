@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.settings.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.daykit.core.designsystem.components.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import com.daykit.AppContainer
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.security.BiometricAuthenticator
import com.daykit.core.security.DayKitDeviceAdmin
import com.daykit.feature.applock.domain.SettingsPackageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SETTINGS_LABEL = "Settings"

private data class UtilityLockDisableRequest(
    val key: String,
    val title: String,
)

private fun deviceAdminComponent(context: Context) =
    ComponentName(context, DayKitDeviceAdmin::class.java)

private fun isDeviceAdminActive(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    return dpm.isAdminActive(deviceAdminComponent(context))
}

private fun deviceAdminIntent(context: Context): Intent {
    return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(
            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
            deviceAdminComponent(context),
        )
        putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Enable to protect DayKit from being uninstalled without your PIN.",
        )
    }
}

private fun removeDeviceAdmin(context: Context) {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val component = deviceAdminComponent(context)
    if (dpm.isAdminActive(component)) {
        dpm.removeActiveAdmin(component)
    }
}

private suspend fun setSettingsLocked(
    container: AppContainer,
    settingsPackage: String,
    locked: Boolean,
) {
    container.appLockRepository.getLockedApps()
        .filter { app -> app.label == SETTINGS_LABEL && app.packageName != settingsPackage }
        .forEach { app ->
            container.appLockRepository.setLocked(
                packageName = app.packageName,
                label = app.label,
                locked = false,
            )
        }

    container.appLockRepository.setLocked(
        packageName = settingsPackage,
        label = SETTINGS_LABEL,
        locked = locked,
    )
}

@Composable
fun SettingsScreen(
    container: AppContainer,
    bottomBarPadding: androidx.compose.foundation.layout.PaddingValues,
    onOpenBackupRestore: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAboutApp: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val biometricAuthenticator = remember(activity) { BiometricAuthenticator(activity) }
    var biometricEnabled by remember { mutableStateOf<Boolean?>(null) }
    var appLockToolLocked by remember { mutableStateOf<Boolean?>(null) }
    var keyStoreToolLocked by remember { mutableStateOf<Boolean?>(null) }
    var notesToolLocked by remember { mutableStateOf<Boolean?>(null) }
    var screenshotProtection by remember { mutableStateOf<Boolean?>(null) }
    val settingsLoaded = biometricEnabled != null &&
        appLockToolLocked != null &&
        keyStoreToolLocked != null &&
        notesToolLocked != null &&
        screenshotProtection != null

    val settingsPackage = remember(context) { SettingsPackageResolver.resolve(context) }
    var isAdminActive by remember { mutableStateOf(isDeviceAdminActive(context)) }

    var showChangePin by remember { mutableStateOf(false) }
    var changePinMessage by remember { mutableStateOf<String?>(null) }
    var biometricMessage by remember { mutableStateOf<String?>(null) }

    // Unified disable-confirm sheet state.
    var showBiometricDisableConfirm by remember { mutableStateOf(false) }
    var biometricDisableError by remember { mutableStateOf<String?>(null) }
    var showScreenshotDisableConfirm by remember { mutableStateOf(false) }
    var screenshotDisableError by remember { mutableStateOf<String?>(null) }
    var showAdminDisableConfirm by remember { mutableStateOf(false) }
    var adminDisableError by remember { mutableStateOf<String?>(null) }
    var pendingUtilityDisable by remember { mutableStateOf<UtilityLockDisableRequest?>(null) }
    var utilityDisableError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_BIOMETRIC_ENABLED)
            .collect { enabled -> biometricEnabled = enabled ?: false }
    }

    LaunchedEffect(Unit) {
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_TOOL_LOCK_APP_LOCK)
            .collect { locked -> appLockToolLocked = locked ?: true }
    }

    LaunchedEffect(Unit) {
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_TOOL_LOCK_KEY_STORE)
            .collect { locked -> keyStoreToolLocked = locked ?: true }
    }

    LaunchedEffect(Unit) {
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_TOOL_LOCK_NOTES)
            .collect { locked -> notesToolLocked = locked ?: true }
    }

    LaunchedEffect(Unit) {
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_SCREENSHOT_PROTECTION)
            .collect { enabled -> screenshotProtection = enabled != false }
    }

    fun requestUtilityLockChange(key: String, title: String, locked: Boolean) {
        if (locked) {
            scope.launch {
                container.secureSettingRepository.putBoolean(key, true)
            }
        } else {
            pendingUtilityDisable = UtilityLockDisableRequest(key = key, title = title)
            utilityDisableError = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val wasAdmin = isAdminActive
                isAdminActive = isDeviceAdminActive(context)
                if (!wasAdmin && isAdminActive) {
                    scope.launch {
                        setSettingsLocked(container, settingsPackage, locked = true)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accents = MaterialTheme.extendedColors.accents
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Settings")

        if (!settingsLoaded) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
            return
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = bottomBarPadding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // ---- Data ----
            item { SectionHeader("Data") }
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    AppListRow(
                        headline = "Backup & Restore",
                        leadingIcon = Icons.Rounded.CloudUpload,
                        leadingAccent = accents.blue,
                        trailing = { NavChevron() },
                        onClick = onOpenBackupRestore,
                    )
                }
            }

            // ---- Security ----
            item { SectionHeader("Security") }
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    // Change Master PIN
                    AppListRow(
                        headline = "Change Master PIN",
                        leadingIcon = Icons.Rounded.Lock,
                        leadingAccent = accents.indigo,
                        trailing = { NavChevron() },
                        onClick = {
                            changePinMessage = null
                            showChangePin = true
                        },
                    )
                    RowDivider(startIndent = Spacing.lg)
                    // Fingerprint
                    AppListRow(
                        headline = "Fingerprint Unlock",
                        leadingIcon = Icons.Rounded.Fingerprint,
                        leadingAccent = accents.teal,
                        trailing = {
                            AppSwitch(
                                checked = biometricEnabled == true,
                                onCheckedChange = { enable ->
                                    biometricMessage = null
                                    if (enable) {
                                        if (!biometricAuthenticator.canAuthenticate()) {
                                            biometricMessage = "Fingerprint is unavailable on this device"
                                        } else {
                                            biometricAuthenticator.authenticate(
                                                title = "Enable fingerprint",
                                                subtitle = "Confirm once for DayKit tools",
                                                onSuccess = {
                                                    scope.launch {
                                                        container.secureSettingRepository.putBoolean(
                                                            SecureSettingRepository.KEY_BIOMETRIC_ENABLED,
                                                            true,
                                                        )
                                                    }
                                                },
                                                onError = { biometricMessage = it },
                                            )
                                        }
                                    } else {
                                        biometricDisableError = null
                                        showBiometricDisableConfirm = true
                                    }
                                },
                            )
                        },
                    )
                    RowDivider(startIndent = Spacing.lg)
                    // Screenshot protection
                    AppListRow(
                        headline = "Screenshot Protection",
                        leadingIcon = Icons.Rounded.VisibilityOff,
                        leadingAccent = accents.purple,
                        trailing = {
                            AppSwitch(
                                checked = screenshotProtection == true,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        scope.launch {
                                            container.secureSettingRepository.putBoolean(
                                                SecureSettingRepository.KEY_SCREENSHOT_PROTECTION,
                                                true,
                                            )
                                        }
                                    } else {
                                        screenshotDisableError = null
                                        showScreenshotDisableConfirm = true
                                    }
                                },
                            )
                        },
                    )
                    RowDivider(startIndent = Spacing.lg)
                    // Uninstall protection
                    AppListRow(
                        headline = "Uninstall Protection",
                        leadingIcon = Icons.Rounded.Shield,
                        leadingAccent = accents.red,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isAdminActive) {
                                    ActiveBadge()
                                    Spacer(Modifier.width(Spacing.sm))
                                }
                                AppSwitch(
                                    checked = isAdminActive,
                                    onCheckedChange = { enable ->
                                        if (enable) {
                                            context.startActivity(deviceAdminIntent(context))
                                        } else {
                                            adminDisableError = null
                                            showAdminDisableConfirm = true
                                        }
                                    },
                                )
                            }
                        },
                    )
                    if (isAdminActive) {
                        Text(
                            text = "The Settings app is locked to prevent admin deactivation. " +
                                "Enter your PIN to disable this protection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.textMuted,
                            modifier = Modifier.padding(
                                start = Spacing.lg,
                                end = Spacing.lg,
                                bottom = Spacing.md,
                            ),
                        )
                    }
                }
            }

            // ---- Tool locks ----
            item { SectionHeader("Tool locks") }
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    UtilityToolLockRow(
                        icon = Icons.Rounded.Apps,
                        accent = accents.blue,
                        title = "App Lock",
                        locked = appLockToolLocked == true,
                        onLockedChange = { locked ->
                            requestUtilityLockChange(
                                key = SecureSettingRepository.KEY_TOOL_LOCK_APP_LOCK,
                                title = "App Lock",
                                locked = locked,
                            )
                        },
                    )
                    RowDivider(startIndent = Spacing.lg)
                    UtilityToolLockRow(
                        icon = Icons.Rounded.Key,
                        accent = accents.indigo,
                        title = "Key Store",
                        locked = keyStoreToolLocked == true,
                        onLockedChange = { locked ->
                            requestUtilityLockChange(
                                key = SecureSettingRepository.KEY_TOOL_LOCK_KEY_STORE,
                                title = "Key Store",
                                locked = locked,
                            )
                        },
                    )
                    RowDivider(startIndent = Spacing.lg)
                    UtilityToolLockRow(
                        icon = Icons.Rounded.NoteAlt,
                        accent = accents.teal,
                        title = "Notes",
                        locked = notesToolLocked == true,
                        onLockedChange = { locked ->
                            requestUtilityLockChange(
                                key = SecureSettingRepository.KEY_TOOL_LOCK_NOTES,
                                title = "Notes",
                                locked = locked,
                            )
                        },
                    )
                }
            }

            // ---- Appearance ----
            item { SectionHeader("Appearance") }
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    AppListRow(
                        headline = "Appearance",
                        leadingIcon = Icons.Rounded.Palette,
                        leadingAccent = accents.orange,
                        trailing = { NavChevron() },
                        onClick = onOpenAppearance,
                    )
                }
            }

            // ---- About ----
            item { SectionHeader("About") }
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    AppListRow(
                        headline = "About DayKit",
                        leadingIcon = Icons.Rounded.Info,
                        leadingAccent = accents.blue,
                        trailing = { NavChevron() },
                        onClick = onOpenAboutApp,
                    )
                    RowDivider(startIndent = Spacing.lg)
                    AppListRow(
                        headline = "Privacy Policy",
                        leadingIcon = Icons.Rounded.Policy,
                        leadingAccent = accents.green,
                        trailing = { NavChevron() },
                        onClick = onOpenPrivacyPolicy,
                    )
                }
            }

            item {
                Text(
                    text = "DayKit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                )
            }
        }
    }

    // ---- Change PIN sheet ----
    if (showChangePin) {
        ChangePinSheet(
            onDismiss = { showChangePin = false },
            onSave = { oldPin, newPin, onError, onDone ->
                scope.launch {
                    runCatching {
                        val valid = withContext(Dispatchers.Default) {
                            container.credentialRepository.verify(oldPin.toCharArray())
                        }
                        if (valid) {
                            withContext(Dispatchers.Default) {
                                container.credentialRepository.saveCredential(newPin.toCharArray())
                            }
                            changePinMessage = "PIN updated"
                            onDone()
                            showChangePin = false
                        } else {
                            onError("Old PIN is incorrect")
                        }
                    }.onFailure { error ->
                        onError(error.message ?: "Could not update PIN")
                    }
                }
            },
        )
    }

    // ---- Fingerprint disable confirm ----
    if (showBiometricDisableConfirm) {
        ConfirmPinSheet(
            title = "Turn off fingerprint",
            message = "Enter your master PIN to turn off fingerprint unlock.",
            error = biometricDisableError,
            onDismiss = {
                showBiometricDisableConfirm = false
                biometricDisableError = null
            },
            onConfirm = { pin ->
                scope.launch {
                    val valid = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (valid) {
                        container.secureSettingRepository.putBoolean(
                            SecureSettingRepository.KEY_BIOMETRIC_ENABLED,
                            false,
                        )
                        biometricMessage = "Fingerprint unlock turned off"
                        showBiometricDisableConfirm = false
                        biometricDisableError = null
                    } else {
                        biometricDisableError = "Wrong PIN"
                    }
                }
            },
        )
    }

    // ---- Screenshot disable confirm ----
    if (showScreenshotDisableConfirm) {
        ConfirmPinSheet(
            title = "Allow screenshots",
            message = "Enter your master PIN to turn off screenshot protection.",
            error = screenshotDisableError,
            onDismiss = {
                showScreenshotDisableConfirm = false
                screenshotDisableError = null
            },
            onConfirm = { pin ->
                scope.launch {
                    val valid = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (valid) {
                        container.secureSettingRepository.putBoolean(
                            SecureSettingRepository.KEY_SCREENSHOT_PROTECTION,
                            false,
                        )
                        showScreenshotDisableConfirm = false
                        screenshotDisableError = null
                    } else {
                        screenshotDisableError = "Wrong PIN"
                    }
                }
            },
        )
    }

    // ---- Uninstall protection disable confirm ----
    if (showAdminDisableConfirm) {
        ConfirmPinSheet(
            title = "Turn off uninstall protection",
            message = "Enter your master PIN to disable uninstall protection.",
            error = adminDisableError,
            onDismiss = {
                showAdminDisableConfirm = false
                adminDisableError = null
            },
            onConfirm = { pin ->
                scope.launch {
                    val valid = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (valid) {
                        setSettingsLocked(container, settingsPackage, locked = false)
                        removeDeviceAdmin(context)
                        isAdminActive = false
                        showAdminDisableConfirm = false
                        adminDisableError = null
                    } else {
                        adminDisableError = "Wrong PIN"
                    }
                }
            },
        )
    }

    // ---- Utility tool lock disable confirm ----
    pendingUtilityDisable?.let { request ->
        ConfirmPinSheet(
            title = "Turn off ${request.title} lock",
            message = "Confirm with fingerprint or master PIN.",
            error = utilityDisableError,
            showFingerprint = biometricEnabled == true && biometricAuthenticator.canAuthenticate(),
            onFingerprint = {
                biometricAuthenticator.authenticate(
                    title = "Confirm change",
                    subtitle = "Turn off ${request.title} lock",
                    onSuccess = {
                        scope.launch {
                            container.secureSettingRepository.putBoolean(request.key, false)
                            pendingUtilityDisable = null
                            utilityDisableError = null
                        }
                    },
                    onError = { utilityDisableError = it },
                )
            },
            onDismiss = {
                pendingUtilityDisable = null
                utilityDisableError = null
            },
            onConfirm = { pin ->
                scope.launch {
                    val valid = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (valid) {
                        container.secureSettingRepository.putBoolean(request.key, false)
                        pendingUtilityDisable = null
                        utilityDisableError = null
                    } else {
                        utilityDisableError = "Wrong PIN"
                    }
                }
            },
        )
    }

}

@Composable
private fun NavChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.extendedColors.textMuted,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun ActiveBadge() {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.extendedColors.successContainer)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) {
        Text(
            text = "Active",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.extendedColors.success,
        )
    }
}

@Composable
private fun UtilityToolLockRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    title: String,
    locked: Boolean,
    onLockedChange: (Boolean) -> Unit,
) {
    AppListRow(
        headline = title,
        leadingIcon = icon,
        leadingAccent = accent,
        trailing = {
            AppSwitch(checked = locked, onCheckedChange = onLockedChange)
        },
    )
}

@Composable
private fun ChangePinSheet(
    onDismiss: () -> Unit,
    onSave: (
        oldPin: String,
        newPin: String,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) -> Unit,
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val pinsMatch = newPin == confirmNewPin
    val canChangePin = oldPin.length >= 4 &&
        newPin.length >= 4 &&
        confirmNewPin.length >= 4 &&
        pinsMatch &&
        !saving

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
                text = "Change PIN",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AppTextField(
                value = oldPin,
                onValueChange = {
                    oldPin = it.filter(Char::isDigit).take(12)
                    error = null
                },
                label = "Old PIN",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            AppTextField(
                value = newPin,
                onValueChange = {
                    newPin = it.filter(Char::isDigit).take(12)
                    error = null
                },
                label = "New PIN",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            AppTextField(
                value = confirmNewPin,
                onValueChange = {
                    confirmNewPin = it.filter(Char::isDigit).take(12)
                    error = null
                },
                label = "Confirm new PIN",
                isError = newPin.isNotEmpty() && confirmNewPin.isNotEmpty() && !pinsMatch,
                supportingText = if (newPin.isNotEmpty() && confirmNewPin.isNotEmpty() && !pinsMatch) {
                    "PINs do not match"
                } else {
                    null
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            ) {
                SecondaryButton(
                    text = "Cancel",
                    enabled = !saving,
                    onClick = onDismiss,
                )
                PrimaryButton(
                    text = "Save",
                    enabled = canChangePin,
                    loading = saving,
                    onClick = {
                        saving = true
                        error = null
                        onSave(
                            oldPin,
                            newPin,
                            { message ->
                                error = message
                                oldPin = ""
                                saving = false
                            },
                            {
                                saving = false
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfirmPinSheet(
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
            AppTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                label = "Master PIN",
                isError = error != null,
                supportingText = error,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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
                    enabled = pin.length >= 4,
                    onClick = { onConfirm(pin) },
                )
            }
        }
    }
}
