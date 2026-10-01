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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
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
import com.daykit.core.designsystem.components.AppTopBarCompactHeight
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.security.BiometricAuthenticator
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.security.CredentialKind
import com.daykit.core.security.LockGracePeriod
import com.daykit.core.security.CredentialRepository
import com.daykit.core.security.label
import com.daykit.core.security.DayKitDeviceAdmin
import com.daykit.core.security.PinVerifyResult
import com.daykit.core.security.errorMessageOrNull
import com.daykit.feature.applock.domain.SettingsPackageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SETTINGS_LABEL = "Settings"

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
    var screenshotProtection by remember { mutableStateOf<Boolean?>(null) }
    var lockGraceSeconds by remember {
        mutableStateOf(
            LockGracePeriod.sanitize(
                container.settingFlagCache.getInt(SecureSettingRepository.KEY_LOCK_GRACE_SECONDS),
            ),
        )
    }
    val settingsLoaded = biometricEnabled != null && screenshotProtection != null

    val settingsPackage = remember(context) { SettingsPackageResolver.resolve(context) }
    var isAdminActive by remember { mutableStateOf(isDeviceAdminActive(context)) }

    var credentialKind by remember { mutableStateOf(container.credentialRepository.credentialKind()) }
    var showChangePin by remember { mutableStateOf(false) }
    var changePinMessage by remember { mutableStateOf<String?>(null) }
    var biometricMessage by remember { mutableStateOf<String?>(null) }

    // Unified disable-confirm sheet state.
    var showBiometricDisableConfirm by remember { mutableStateOf(false) }
    var biometricDisableError by remember { mutableStateOf<String?>(null) }
    var showScreenshotDisableConfirm by remember { mutableStateOf(false) }
    var screenshotDisableError by remember { mutableStateOf<String?>(null) }
    var showLockGracePicker by remember { mutableStateOf(false) }
    // A longer window weakens the lock, so it needs the PIN; shorter applies at once.
    var pendingLockGraceSeconds by remember { mutableStateOf<Int?>(null) }
    var lockGraceError by remember { mutableStateOf<String?>(null) }
    var showAdminDisableConfirm by remember { mutableStateOf(false) }
    var adminDisableError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_BIOMETRIC_ENABLED)
            .collect { enabled ->
                biometricEnabled = enabled == true && container.biometricUnlockManager.isEnrolled()
            }
    }

    LaunchedEffect(Unit) {
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_SCREENSHOT_PROTECTION)
            .collect { enabled -> screenshotProtection = enabled != false }
    }

    LaunchedEffect(Unit) {
        container.secureSettingRepository
            .observeInt(SecureSettingRepository.KEY_LOCK_GRACE_SECONDS)
            .collect { seconds -> lockGraceSeconds = LockGracePeriod.sanitize(seconds) }
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

    val headerHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppTopBarCompactHeight
    Box(Modifier.fillMaxSize()) {

        if (!settingsLoaded) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
            AppTopBar(title = "Settings", height = AppTopBarCompactHeight, modifier = Modifier.align(Alignment.TopCenter))
            return
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = headerHeight + Spacing.md,
                bottom = bottomBarPadding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // ---- Data ----
            item { SectionHeader("Data", topPadding = 0.dp) }
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
                    // Change master PIN or password
                    AppListRow(
                        headline = "Change Master PIN or Password",
                        supporting = if (credentialKind == CredentialKind.Pin) {
                            "Currently a PIN"
                        } else {
                            "Currently a password"
                        },
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
                                            runCatching {
                                                container.biometricUnlockManager.enrollmentCipher()
                                            }.onSuccess { cipher ->
                                                biometricAuthenticator.authenticate(
                                                    cipher = cipher,
                                                    title = "Enable fingerprint",
                                                    subtitle = "Confirm to protect your DayKit master key",
                                                    onSuccess = { authenticatedCipher ->
                                                        runCatching {
                                                            container.biometricUnlockManager.completeEnrollment(
                                                                authenticatedCipher,
                                                                container.sensitiveKeyManager,
                                                            )
                                                        }.onSuccess {
                                                            scope.launch {
                                                                container.secureSettingRepository.putBoolean(
                                                                    SecureSettingRepository.KEY_BIOMETRIC_ENABLED,
                                                                    true,
                                                                )
                                                            }
                                                        }.onFailure {
                                                            biometricMessage = "Could not protect the biometric key"
                                                        }
                                                    },
                                                    onError = { biometricMessage = it },
                                                )
                                            }.onFailure {
                                                biometricMessage = "Could not create a biometric key on this device"
                                            }
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
                    // Lock grace window after leaving the app
                    AppListRow(
                        headline = "Auto-Lock",
                        supporting = LockGracePeriod.label(lockGraceSeconds),
                        leadingIcon = Icons.Rounded.Timer,
                        leadingAccent = accents.orange,
                        trailing = { NavChevron() },
                        onClick = { showLockGracePicker = true },
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

            // ---- Preferences ----
            item { SectionHeader("Preferences") }
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

        }
        AppTopBar(
            title = "Settings",
            height = AppTopBarCompactHeight,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    // ---- Change PIN sheet ----
    if (showChangePin) {
        ChangePinSheet(
            currentKind = credentialKind,
            onDismiss = { showChangePin = false },
            onSave = { oldPin, newPin, newKind, onError, onDone ->
                scope.launch {
                    runCatching {
                        val result = withContext(Dispatchers.Default) {
                            container.credentialRepository.verify(oldPin.toCharArray())
                        }
                        if (result is PinVerifyResult.Success) {
                            // Re-wrap the sensitive-data key under the new PIN FIRST.
                            // If this fails we do not change the PIN, so the vault /
                            // key store / notes can never be orphaned. If the key was
                            // somehow never created (shouldn't happen post-onboarding),
                            // create it under the new PIN rather than leaving the tools
                            // permanently unusable.
                            val rewrapped = withContext(Dispatchers.Default) {
                                if (container.sensitiveKeyManager.isInitialized()) {
                                    container.sensitiveKeyManager.rewrap(oldPin.toCharArray(), newPin.toCharArray())
                                } else {
                                    container.sensitiveKeyManager.initialize(newPin.toCharArray())
                                    true
                                }
                            }
                            if (!rewrapped) {
                                onError("Could not update PIN. Please try again.")
                                return@runCatching
                            }
                            withContext(Dispatchers.Default) {
                                container.credentialRepository.saveCredential(newPin.toCharArray(), newKind)
                            }
                            credentialKind = newKind
                            changePinMessage = if (newKind == CredentialKind.Pin) "PIN updated" else "Password updated"
                            onDone()
                            showChangePin = false
                        } else {
                            onError(
                                (result as? PinVerifyResult.LockedOut)?.let { result.errorMessageOrNull() }
                                    ?: "Current ${credentialKind.label} is incorrect",
                            )
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
            credentialKind = credentialKind,
            title = "Turn off fingerprint",
            message = "Enter your master ${credentialKind.label} to turn off fingerprint unlock.",
            error = biometricDisableError,
            onDismiss = {
                showBiometricDisableConfirm = false
                biometricDisableError = null
            },
            onConfirm = { pin ->
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (result is PinVerifyResult.Success) {
                        container.biometricUnlockManager.clear()
                        container.secureSettingRepository.putBoolean(
                            SecureSettingRepository.KEY_BIOMETRIC_ENABLED,
                            false,
                        )
                        biometricMessage = "Fingerprint unlock turned off"
                        showBiometricDisableConfirm = false
                        biometricDisableError = null
                    } else {
                        biometricDisableError = result.errorMessageOrNull(credentialKind)
                    }
                }
            },
        )
    }

    // ---- Screenshot disable confirm ----
    if (showScreenshotDisableConfirm) {
        ConfirmPinSheet(
            credentialKind = credentialKind,
            title = "Allow screenshots",
            message = "Enter your master ${credentialKind.label} to turn off screenshot protection.",
            error = screenshotDisableError,
            onDismiss = {
                showScreenshotDisableConfirm = false
                screenshotDisableError = null
            },
            onConfirm = { pin ->
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (result is PinVerifyResult.Success) {
                        container.secureSettingRepository.putBoolean(
                            SecureSettingRepository.KEY_SCREENSHOT_PROTECTION,
                            false,
                        )
                        showScreenshotDisableConfirm = false
                        screenshotDisableError = null
                    } else {
                        screenshotDisableError = result.errorMessageOrNull(credentialKind)
                    }
                }
            },
        )
    }

    // ---- Auto-lock picker ----
    if (showLockGracePicker) {
        LockGraceSheet(
            selectedSeconds = lockGraceSeconds,
            onDismiss = { showLockGracePicker = false },
            onSelect = { seconds ->
                showLockGracePicker = false
                if (seconds > lockGraceSeconds) {
                    lockGraceError = null
                    pendingLockGraceSeconds = seconds
                } else if (seconds != lockGraceSeconds) {
                    scope.launch {
                        container.secureSettingRepository.putInt(
                            SecureSettingRepository.KEY_LOCK_GRACE_SECONDS,
                            seconds,
                        )
                    }
                }
            },
        )
    }

    // ---- Auto-lock lengthen confirm ----
    pendingLockGraceSeconds?.let { seconds ->
        ConfirmPinSheet(
            credentialKind = credentialKind,
            title = "Lock later",
            message = "Enter your master ${credentialKind.label} to lock DayKit " +
                "${LockGracePeriod.label(seconds).replaceFirstChar { it.lowercase() }} instead.",
            error = lockGraceError,
            onDismiss = {
                pendingLockGraceSeconds = null
                lockGraceError = null
            },
            onConfirm = { pin ->
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (result is PinVerifyResult.Success) {
                        container.secureSettingRepository.putInt(
                            SecureSettingRepository.KEY_LOCK_GRACE_SECONDS,
                            seconds,
                        )
                        pendingLockGraceSeconds = null
                        lockGraceError = null
                    } else {
                        lockGraceError = result.errorMessageOrNull(credentialKind)
                    }
                }
            },
        )
    }

    // ---- Uninstall protection disable confirm ----
    if (showAdminDisableConfirm) {
        ConfirmPinSheet(
            credentialKind = credentialKind,
            title = "Turn off uninstall protection",
            message = "Enter your master ${credentialKind.label} to disable uninstall protection.",
            error = adminDisableError,
            onDismiss = {
                showAdminDisableConfirm = false
                adminDisableError = null
            },
            onConfirm = { pin ->
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (result is PinVerifyResult.Success) {
                        setSettingsLocked(container, settingsPackage, locked = false)
                        removeDeviceAdmin(context)
                        isAdminActive = false
                        showAdminDisableConfirm = false
                        adminDisableError = null
                    } else {
                        adminDisableError = result.errorMessageOrNull(credentialKind)
                    }
                }
            },
        )
    }

}

@Composable
private fun LockGraceSheet(
    selectedSeconds: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
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
                text = "Auto-Lock",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "How long DayKit stays unlocked after you switch to another app. " +
                    "Turning the screen off always locks it immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
            )
        }
        LockGracePeriod.OPTIONS_SECONDS.forEach { seconds ->
            AppListRow(
                headline = LockGracePeriod.label(seconds),
                trailing = if (seconds == selectedSeconds) {
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
                onClick = { onSelect(seconds) },
            )
        }
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
private fun ChangePinSheet(
    currentKind: CredentialKind,
    onDismiss: () -> Unit,
    onSave: (
        oldPin: String,
        newPin: String,
        newKind: CredentialKind,
        onError: (String) -> Unit,
        onDone: () -> Unit,
    ) -> Unit,
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf(currentKind) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val pinsMatch = newPin == confirmNewPin
    val newPinError = CredentialRepository.newCredentialError(newPin, newKind)
    val canChangePin = oldPin.isNotEmpty() &&
        newPinError == null &&
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
                text = "Change master ${currentKind.label}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CredentialField(
                value = oldPin,
                onValueChange = {
                    oldPin = it
                    error = null
                },
                kind = currentKind,
                label = "Current ${currentKind.label}",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CredentialKind.entries.forEach { kind ->
                    FilterChipButton(
                        text = if (kind == CredentialKind.Pin) "PIN" else "Password",
                        selected = newKind == kind,
                        onClick = {
                            if (newKind != kind) {
                                newKind = kind
                                newPin = ""
                                confirmNewPin = ""
                                error = null
                            }
                        },
                    )
                }
            }
            CredentialField(
                value = newPin,
                onValueChange = {
                    newPin = it
                    error = null
                },
                kind = newKind,
                label = "New ${newKind.label}",
                isError = newPin.isNotEmpty() && newPinError != null,
                supportingText = if (newPin.isNotEmpty()) newPinError else null,
            )
            CredentialField(
                value = confirmNewPin,
                onValueChange = {
                    confirmNewPin = it
                    error = null
                },
                kind = newKind,
                label = "Confirm new ${newKind.label}",
                isError = newPin.isNotEmpty() && confirmNewPin.isNotEmpty() && !pinsMatch,
                supportingText = if (newPin.isNotEmpty() && confirmNewPin.isNotEmpty() && !pinsMatch) {
                    if (newKind == CredentialKind.Pin) "PINs do not match" else "Passwords do not match"
                } else {
                    null
                },
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
                            newKind,
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

/** Masked input for a master credential: number pad for a PIN, full keyboard for a password. */
@Composable
private fun CredentialField(
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
private fun ConfirmPinSheet(
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
