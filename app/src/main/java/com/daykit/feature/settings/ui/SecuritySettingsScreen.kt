@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.settings.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.os.Build
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.ReportGmailerrorred
import com.daykit.core.data.AppLockRelock
import com.daykit.core.data.AppPreferences
import com.daykit.core.data.ClipboardClear
import com.daykit.core.util.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import com.daykit.core.designsystem.components.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import com.daykit.AppContainer
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppTextButton
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
import com.daykit.core.security.PinVerifyResult
import com.daykit.core.security.errorMessageOrNull
import com.daykit.feature.applock.domain.AntiTheftProtection
import com.daykit.feature.applock.domain.SettingsPackageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SecuritySettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
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
    var isAdminActive by remember { mutableStateOf(AntiTheftProtection.isActive(context)) }
    var showAntiTheftDisclosure by remember { mutableStateOf(false) }

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
    var showClipboardPicker by remember { mutableStateOf(false) }
    var showRelockPicker by remember { mutableStateOf(false) }
    var showFailedAttempts by remember { mutableStateOf(false) }
    val hideInRecents by AppPreferences.rememberPreference(AppPreferences.KEY_HIDE_IN_RECENTS) {
        AppPreferences.hideInRecents
    }
    val clipboardSeconds by AppPreferences.rememberPreference(AppPreferences.KEY_CLIPBOARD_CLEAR_SECONDS) {
        AppPreferences.clipboardClearSeconds
    }
    val appLockRelock by AppPreferences.rememberPreference(AppPreferences.KEY_APP_LOCK_RELOCK) {
        AppPreferences.appLockRelock
    }
    // Re-read whenever the page resumes, since a wrong entry may have happened elsewhere.
    var failedAttempts by remember { mutableStateOf(container.credentialRepository.failedAttemptLog()) }

    // Relaxing any of these needs the master credential; tightening applies at once.
    val lockGraceChange = rememberPinGatedChange<Int>(
        container = container,
        title = "Lock later",
        message = { seconds ->
            "Enter your master ${credentialKind.label} to lock DayKit " +
                "${LockGracePeriod.label(seconds).replaceFirstChar { it.lowercase() }} instead."
        },
        apply = { seconds ->
            scope.launch {
                container.secureSettingRepository.putInt(SecureSettingRepository.KEY_LOCK_GRACE_SECONDS, seconds)
            }
        },
    )
    val clipboardChange = rememberPinGatedChange<Int>(
        container = container,
        title = "Keep copied secrets longer",
        message = { seconds ->
            "Enter your master ${credentialKind.label} to " + if (seconds == 0) {
                "stop clearing copied secrets."
            } else {
                "clear copied secrets ${ClipboardClear.label(seconds).replaceFirstChar { it.lowercase() }}."
            }
        },
        apply = { seconds -> AppPreferences.clipboardClearSeconds = seconds },
    )
    val relockChange = rememberPinGatedChange<AppLockRelock>(
        container = container,
        title = "Re-lock apps later",
        message = { mode ->
            "Enter your master ${credentialKind.label} to re-lock apps ${mode.label.replaceFirstChar { it.lowercase() }}."
        },
        apply = { mode -> AppPreferences.appLockRelock = mode },
    )
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
                failedAttempts = container.credentialRepository.failedAttemptLog()
                val wasAdmin = isAdminActive
                isAdminActive = AntiTheftProtection.isActive(context)
                if (!wasAdmin && isAdminActive) {
                    scope.launch {
                        AntiTheftProtection.setSettingsLocked(container.appLockRepository, settingsPackage, locked = true)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accents = MaterialTheme.extendedColors.accents

    SettingsSubPage(title = "Security & Privacy", onBack = onBack) {
        if (!settingsLoaded) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = Spacing.xxl), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            return@SettingsSubPage
        }

            item { SectionHeader("Unlock", topPadding = 0.dp) }
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
                }
            }
            item { SectionHeader("Privacy") }
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        AppListRow(
                            headline = "Hide in Recents",
                            supporting = "Blank DayKit's preview in the app switcher",
                            leadingIcon = Icons.Rounded.Layers,
                            leadingAccent = accents.indigo,
                            onClick = { AppPreferences.hideInRecents = !hideInRecents },
                            trailing = {
                                AppSwitch(
                                    checked = hideInRecents,
                                    onCheckedChange = { AppPreferences.hideInRecents = it },
                                )
                            },
                        )
                        RowDivider(startIndent = Spacing.lg)
                    }
                    AppListRow(
                        headline = "Clear Copied Secrets",
                        supporting = ClipboardClear.label(clipboardSeconds),
                        leadingIcon = Icons.Rounded.ContentPaste,
                        leadingAccent = accents.teal,
                        trailing = { NavChevron() },
                        onClick = { showClipboardPicker = true },
                    )
                }
            }
            item { SettingsFootnote("Copied Key Store values are hidden from the clipboard preview and cleared after this time.") }
            item { SectionHeader("App Lock") }
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    AppListRow(
                        headline = "Re-lock Locked Apps",
                        supporting = appLockRelock.label,
                        leadingIcon = Icons.Rounded.LockClock,
                        leadingAccent = accents.blue,
                        trailing = { NavChevron() },
                        onClick = { showRelockPicker = true },
                    )
                }
            }
            item { SettingsFootnote("Turning the screen off always re-locks every app.") }
            item { SectionHeader("Protection") }
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    // Anti-theft: device admin + Settings locked behind the PIN.
                    AppListRow(
                        headline = "Anti-theft Protection",
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
                                            showAntiTheftDisclosure = true
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
                            text = "DayKit can't be uninstalled and the Settings app is locked. " +
                                "Forgot your ${credentialKind.label}? Open Settings and tap " +
                                "\"Forgot ${credentialKind.label}?\" to turn this off with your phone's screen lock.",
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
            item { SectionHeader("Activity") }
            item {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    AppListRow(
                        headline = "Failed Unlock Attempts",
                        supporting = when {
                            failedAttempts.isEmpty() -> "None recorded"
                            else -> "${failedAttempts.size} recent · last ${formatAttemptTime(failedAttempts.first())}"
                        },
                        leadingIcon = Icons.Rounded.ReportGmailerrorred,
                        leadingAccent = accents.red,
                        trailing = { NavChevron() },
                        onClick = { showFailedAttempts = true },
                    )
                }
            }
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
        OptionSheet(
            title = "Auto-Lock",
            description = "How long DayKit stays unlocked after you switch to another app. " +
                "Turning the screen off always locks it immediately.",
            options = LockGracePeriod.OPTIONS_SECONDS,
            selected = lockGraceSeconds,
            label = LockGracePeriod::label,
            onDismiss = { showLockGracePicker = false },
            onSelect = { seconds ->
                showLockGracePicker = false
                if (seconds != lockGraceSeconds) lockGraceChange.request(seconds, seconds > lockGraceSeconds)
            },
        )
    }

    // ---- Clipboard picker ----
    if (showClipboardPicker) {
        OptionSheet(
            title = "Clear Copied Secrets",
            description = "When a value copied from Key Store is wiped from the clipboard.",
            options = ClipboardClear.OPTIONS_SECONDS,
            selected = clipboardSeconds,
            label = ClipboardClear::label,
            onDismiss = { showClipboardPicker = false },
            onSelect = { seconds ->
                showClipboardPicker = false
                if (seconds != clipboardSeconds) {
                    // 0 means never, the weakest choice.
                    fun strength(value: Int) = if (value == 0) Int.MAX_VALUE else value
                    clipboardChange.request(seconds, strength(seconds) > strength(clipboardSeconds))
                }
            },
        )
    }

    // ---- App Lock re-lock picker ----
    if (showRelockPicker) {
        OptionSheet(
            title = "Re-lock Locked Apps",
            description = "When an app you unlocked through App Lock asks for your " +
                "${credentialKind.label} again.",
            options = AppLockRelock.entries,
            selected = appLockRelock,
            label = { it.label },
            onDismiss = { showRelockPicker = false },
            onSelect = { mode ->
                showRelockPicker = false
                if (mode != appLockRelock) relockChange.request(mode, mode.ordinal > appLockRelock.ordinal)
            },
        )
    }

    // ---- Failed attempts ----
    if (showFailedAttempts) {
        FailedAttemptsSheet(
            attempts = failedAttempts,
            onDismiss = { showFailedAttempts = false },
            onClear = {
                container.credentialRepository.clearFailedAttemptLog()
                failedAttempts = emptyList()
                showFailedAttempts = false
            },
        )
    }

    // ---- Anti-theft disclosure: consent before the system admin prompt ----
    if (showAntiTheftDisclosure) {
        AntiTheftDisclosureSheet(
            credentialLabel = credentialKind.label,
            deviceSecure = AntiTheftProtection.isDeviceSecure(context),
            onDismiss = { showAntiTheftDisclosure = false },
            onContinue = {
                showAntiTheftDisclosure = false
                context.startActivity(AntiTheftProtection.activationIntent(context))
            },
        )
    }

    // ---- Anti-theft protection disable confirm ----
    if (showAdminDisableConfirm) {
        ConfirmPinSheet(
            credentialKind = credentialKind,
            title = "Turn off anti-theft protection",
            message = "Enter your master ${credentialKind.label} to turn off anti-theft protection.",
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
                        AntiTheftProtection.disable(context, container.appLockRepository)
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
private fun FailedAttemptsSheet(
    attempts: List<Long>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "Failed Unlock Attempts",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (attempts.isEmpty()) {
                    "No wrong PIN or password entries have been recorded."
                } else {
                    "Wrong entries on any DayKit lock screen, including locked apps. " +
                        "The last ${attempts.size} are kept."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
            )
            attempts.forEach { millis ->
                Text(
                    text = formatAttemptTime(millis),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            ) {
                if (attempts.isNotEmpty()) {
                    AppTextButton(
                        text = "Clear",
                        color = MaterialTheme.extendedColors.textMuted,
                        onClick = onClear,
                    )
                }
                PrimaryButton(text = "Done", onClick = onDismiss)
            }
        }
    }
}

/** "Today, 9:41 PM" / "12 Sep, 21:41". */
private fun formatAttemptTime(millis: Long): String {
    val dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val time = TimeFormat.format(dateTime.hour, dateTime.minute)
    val date = dateTime.toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today, $time"
        today.minusDays(1) -> "Yesterday, $time"
        else -> "${date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}, $time"
    }
}

@Composable
private fun AntiTheftDisclosureSheet(
    credentialLabel: String,
    deviceSecure: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "Anti-theft Protection",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Stops someone holding your unlocked phone from removing DayKit to get around App Lock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
            )
            listOf(
                "DayKit is registered as a device admin, so it can't be uninstalled.",
                "The Settings app is locked behind your DayKit $credentialLabel.",
                "Turn it off any time in Security & Privacy with your $credentialLabel.",
                "Forgot your $credentialLabel? Open Settings and tap \"Forgot $credentialLabel?\" " +
                    "to turn it off with your phone's screen lock.",
            ).forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Restarting in safe mode or factory-resetting the phone still removes any app. " +
                    "For full theft protection, also turn on Android's Theft Protection and Find My Device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
            )
            if (!deviceSecure) {
                Text(
                    text = "Set a screen lock on your phone first. It's how you turn this off if you forget your $credentialLabel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            PrimaryButton(
                text = "Continue",
                enabled = deviceSecure,
                modifier = Modifier.fillMaxWidth(),
                onClick = onContinue,
            )
            SecondaryButton(
                text = "Not now",
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}
