package com.daykit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.IntentCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.permissions.AppLockPermissionChecker
import com.daykit.core.permissions.AppLockPermissionState
import com.daykit.core.security.BiometricAuthenticator
import com.daykit.core.security.PinVerifyResult
import com.daykit.core.security.errorMessageOrNull
import com.daykit.core.designsystem.DayKitTheme
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.feature.applock.data.LockedApp
import com.daykit.feature.applock.service.AppMonitorService
import com.daykit.feature.lock.ui.ToolUnlockScreen
import com.daykit.feature.onboarding.ui.BiometricSetupScreen
import com.daykit.feature.onboarding.ui.PermissionGrantScreen
import com.daykit.feature.onboarding.ui.SetupCredentialScreen
import com.daykit.navigation.RootScaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    private val container: AppContainer
        get() = (application as DayKitApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        handleShareIntent(intent)
        setContentView()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        // A finished activity must never leave the decrypted master key alive in
        // the application process. Configuration changes keep the same session.
        if (!isChangingConfigurations) {
            container.sensitiveKeyManager.lock()
            container.sensitiveKeyManager.discardPendingUnlockActions()
        }
        super.onDestroy()
    }

    /**
     * "Share to DayKit" from Gallery/other apps: stash the shared media URIs so
     * the file vault imports them once the user is past the unlock gate.
     */
    private fun handleShareIntent(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    .orEmpty()
                    .filterNotNull()
            else -> return
        }
        if (uris.isNotEmpty()) container.pendingVaultShares.value = uris
    }

    private fun setContentView() {
        setContent {
            DayKitTheme {
                val storageFailure by container.storageFailure.collectAsStateWithLifecycle()
                val failure = storageFailure
                if (failure != null) {
                    // The DB can't be opened at all, so no normal screen can render.
                    StorageRecoveryScreen(
                        message = failure.message.orEmpty(),
                        onReset = {
                            container.resetAllLocalData()
                            // Restart into a clean first-run state.
                            finishAffinity()
                            startActivity(
                                packageManager.getLaunchIntentForPackage(packageName)
                                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            Runtime.getRuntime().exit(0)
                        },
                    )
                } else {
                    DayKitApp(
                        activity = this,
                        container = container,
                    )
                }
            }
        }
    }
}

/**
 * Shown when the Android Keystore key protecting the database is gone, which makes
 * every stored byte permanently unreadable. There is no way back to the old data,
 * so the only meaningful action is a full reset — but the user gets an explanation
 * and an explicit choice instead of an app that crashes on every launch.
 */
@Composable
private fun StorageRecoveryScreen(
    message: String,
    onReset: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Secure storage unavailable",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "$message\n\nDayKit's encryption key is no longer available on this " +
                    "device, so your saved data can't be decrypted. This can happen after a " +
                    "system update or a device security change.\n\nResetting clears all local " +
                    "DayKit data and lets you start again. If you have a backup, you can " +
                    "restore it afterwards.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
            if (confirming) {
                Text(
                    text = "This permanently deletes all local DayKit data. Continue?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                PrimaryButton(text = "Reset all data", onClick = onReset)
                Spacer(Modifier.height(Spacing.sm))
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
            } else {
                PrimaryButton(text = "Reset DayKit", onClick = { confirming = true })
            }
        }
    }
}

@Composable
private fun DayKitApp(
    activity: FragmentActivity,
    container: AppContainer,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val biometricAuthenticator = remember(activity) { BiometricAuthenticator(activity) }

    var credentialReady by remember { mutableStateOf(container.credentialRepository.hasCredential()) }
    // MSK unlock state for the sensitive tools. Starts from the manager (unlocked
    // right after onboarding init), and is wiped whenever the app is backgrounded.
    var sensitiveUnlocked by remember { mutableStateOf(container.sensitiveKeyManager.isUnlocked()) }
    var unlockPin by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var permissions by remember { mutableStateOf(AppLockPermissionChecker.check(context)) }
    var biometricMessage by remember { mutableStateOf<String?>(null) }
    var biometricPreferenceLoaded by remember { mutableStateOf(false) }
    var biometricEnabled by remember { mutableStateOf<Boolean?>(null) }
    var biometricAttemptedForLock by remember { mutableStateOf(false) }
    var preserveContentForActivityResult by remember { mutableStateOf(false) }
    var screenshotProtection by remember { mutableStateOf(true) }
    var lockedApps by remember { mutableStateOf(emptyList<LockedApp>()) }

    DisposableEffect(lifecycleOwner) {
        // ON_STOP fires when the user leaves, during rotation, and for external
        // activities such as file pickers. Every non-configuration stop wipes
        // the key; picker work waits behind the same unlock gate.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    container.sensitiveKeyManager.expectingActivityResult = false
                    permissions = AppLockPermissionChecker.check(context)
                    // Reflect any wipe that happened while backgrounded so the
                    // gate re-shows.
                    sensitiveUnlocked = container.sensitiveKeyManager.isUnlocked()
                }
                Lifecycle.Event.ON_STOP -> {
                    if (activity.isChangingConfigurations) return@LifecycleEventObserver
                    // Preserve the screen only to receive its pending result.
                    // The key is always wiped, including while a picker is open.
                    preserveContentForActivityResult =
                        container.sensitiveKeyManager.expectingActivityResult
                    container.sensitiveKeyManager.lock()
                    sensitiveUnlocked = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!activity.isChangingConfigurations) {
                container.sensitiveKeyManager.lock()
            }
        }
    }

    DisposableEffect(screenshotProtection) {
        if (screenshotProtection) {
            activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }

    LaunchedEffect(Unit) {
        val repository = withContext(Dispatchers.IO) {
            container.secureSettingRepository
        }
        repository
            .observeBoolean(SecureSettingRepository.KEY_BIOMETRIC_ENABLED)
            .catch {
                biometricEnabled = false
                biometricPreferenceLoaded = true
            }
            .collect { enabled ->
                biometricEnabled = enabled
                biometricPreferenceLoaded = true
            }
    }

    LaunchedEffect(Unit) {
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_SCREENSHOT_PROTECTION)
            .catch { screenshotProtection = true }
            .collect { enabled -> screenshotProtection = enabled != false }
    }

    LaunchedEffect(Unit) {
        val repository = withContext(Dispatchers.IO) {
            container.appLockRepository
        }
        repository
            .observeLockedApps()
            .catch { lockedApps = emptyList() }
            .collect { apps -> lockedApps = apps }
    }

    LaunchedEffect(permissions.allGranted, credentialReady, lockedApps.size) {
        if (permissions.allGranted && credentialReady) {
            AppMonitorService.start(context)
        }
    }

    // The automatic Drive backup is triggered here — on unlock — and nowhere else. Key
    // Store and Secure Notes are encrypted with the MSK, which only exists while
    // unlocked, so there is no background equivalent. launchIfDue() is a cheap
    // no-op unless the Daily/Weekly interval has actually elapsed.
    //
    // It runs on the runner's own process-lifetime scope, NOT this coroutine: the MSK
    // is wiped after backgrounding, which flips `sensitiveUnlocked` and would
    // cancel a composition-scoped upload mid-write, leaving a truncated file in Drive
    // that still counts as a backup for retention purposes.
    LaunchedEffect(sensitiveUnlocked) {
        if (sensitiveUnlocked) container.driveBackupRunner.launchIfDue()
    }

    fun tryBiometricUnlock() {
        if (biometricEnabled != true || !biometricAuthenticator.canAuthenticate()) return
        val cipher = container.biometricUnlockManager.unlockCipher()
        if (cipher == null) {
            unlockError = "Fingerprint changed or is unavailable. Unlock with your PIN to enable it again."
            scope.launch {
                container.secureSettingRepository.putBoolean(
                    SecureSettingRepository.KEY_BIOMETRIC_ENABLED,
                    false,
                )
            }
            return
        }
        val unlockGeneration = container.sensitiveKeyManager.unlockGeneration()
        biometricAuthenticator.authenticate(
            cipher = cipher,
            title = "Unlock DayKit",
            subtitle = "Touch the fingerprint sensor",
            onSuccess = { authenticatedCipher ->
                if (container.biometricUnlockManager.completeUnlock(
                        authenticatedCipher,
                        container.sensitiveKeyManager,
                        unlockGeneration,
                    )
                ) {
                    unlockPin = ""
                    unlockError = null
                    sensitiveUnlocked = true
                    container.sensitiveKeyManager.resumePendingUnlockActions()
                } else {
                    unlockError = "Fingerprint unlock failed. Use your master PIN."
                }
            },
            onError = { unlockError = it },
        )
    }

    LaunchedEffect(sensitiveUnlocked, biometricEnabled) {
        if (sensitiveUnlocked) {
            biometricAttemptedForLock = false
        } else if (credentialReady && permissions.allGranted &&
            biometricEnabled == true && !biometricAttemptedForLock
        ) {
            biometricAttemptedForLock = true
            tryBiometricUnlock()
        }
    }

    val unlockGate: @Composable () -> Unit = {
        ToolUnlockScreen(
            title = "Unlock DayKit",
            subtitle = if (biometricEnabled == true) "Use fingerprint or enter your master PIN" else "Enter your master PIN",
            pin = unlockPin,
            error = unlockError,
            pinLength = container.credentialRepository.pinLength(),
            biometricEnabled = biometricEnabled == true &&
                container.biometricUnlockManager.isEnrolled() &&
                biometricAuthenticator.canAuthenticate(),
            icon = Icons.Rounded.Lock,
            onBack = { activity.finish() },
            onPinChange = {
                unlockPin = it.filter(Char::isDigit).take(12)
                unlockError = null
            },
            onUnlock = {
                scope.launch {
                    val pin = unlockPin
                    val result = withContext(Dispatchers.Default) {
                        // The shared verifier enforces lockout before key derivation.
                        val verifyResult = container.credentialRepository.verify(pin.toCharArray())
                        if (verifyResult is PinVerifyResult.Success) {
                            container.sensitiveKeyManager.unlock(pin.toCharArray())
                        }
                        verifyResult
                    }
                    if (result is PinVerifyResult.Success && container.sensitiveKeyManager.isUnlocked()) {
                        unlockPin = ""
                        unlockError = null
                        sensitiveUnlocked = true
                        container.sensitiveKeyManager.resumePendingUnlockActions()
                    } else {
                        unlockPin = ""
                        unlockError = result.errorMessageOrNull() ?: "Wrong PIN"
                    }
                }
            },
            onBiometric = {
                unlockError = null
                tryBiometricUnlock()
            },
        )
    }

    when {
        !credentialReady -> SetupCredentialScreen(
            onCredentialReady = { pin ->
                scope.launch {
                    withContext(Dispatchers.Default) {
                        // Save the PIN credential AND create the sensitive-data key,
                        // wrapped by this PIN. Both derive from the same PIN chars;
                        // read them before saveCredential wipes its copy.
                        container.credentialRepository.saveCredential(pin.toCharArray())
                        container.sensitiveKeyManager.initialize(pin.toCharArray())
                    }
                    sensitiveUnlocked = true
                    credentialReady = true
                }
            },
        )

        !biometricPreferenceLoaded -> StartupLoadingScreen()

        // Onboarding may have been backgrounded after the PIN was created but
        // before biometric enrollment. Restore the MSK before enrollment.
        !sensitiveUnlocked && !preserveContentForActivityResult -> unlockGate()

        biometricEnabled == null -> BiometricSetupScreen(
            canUseBiometric = biometricAuthenticator.canAuthenticate(),
            message = biometricMessage,
            onEnable = {
                runCatching { container.biometricUnlockManager.enrollmentCipher() }
                    .onSuccess { cipher ->
                        biometricAuthenticator.authenticate(
                            cipher = cipher,
                            title = "Enable biometric",
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
                                    biometricMessage = "Could not protect the biometric key. Use your PIN."
                                }
                            },
                            onError = { biometricMessage = it },
                        )
                    }
                    .onFailure {
                        biometricMessage = "Could not create a biometric key on this device"
                    }
            },
            onSkip = {
                scope.launch {
                    container.secureSettingRepository.putBoolean(
                        SecureSettingRepository.KEY_BIOMETRIC_ENABLED,
                        false,
                    )
                }
            },
        )

        !permissions.allGranted -> PermissionGrantScreen(
            permissions = permissions,
            onRefresh = { permissions = AppLockPermissionChecker.check(context) },
        )

        else -> Box {
            RootScaffold(
                activity = activity,
                container = container,
                lockedCount = lockedApps.size,
                onAppLockSelectionChanged = {
                    permissions = AppLockPermissionChecker.check(context)
                    if (permissions.allGranted) {
                        AppMonitorService.start(context)
                    }
                },
            )
            if (!sensitiveUnlocked) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    unlockGate()
                }
            }
        }
    }
}

@Composable
private fun StartupLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}
