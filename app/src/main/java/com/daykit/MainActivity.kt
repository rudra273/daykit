package com.daykit

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.permissions.AppLockPermissionChecker
import com.daykit.core.permissions.AppLockPermissionState
import com.daykit.core.security.BiometricAuthenticator
import com.daykit.core.security.PinVerifyResult
import com.daykit.core.security.errorMessageOrNull
import com.daykit.core.designsystem.DayKitTheme
import com.daykit.core.designsystem.components.LoadingIndicator
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        setContentView()
    }

    private fun setContentView() {
        setContent {
            DayKitTheme {
                DayKitApp(
                    activity = this,
                    container = container,
                )
            }
        }
    }
}

/**
 * How long the app may sit backgrounded before the sensitive key is wiped. A
 * brief grace period lets a rotation, a quick app-switch glance, or a returning
 * picker resume without a fresh PIN prompt, while a genuine departure still
 * locks the vault.
 */
private const val LOCK_GRACE_MILLIS = 2_000L

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
    var screenshotProtection by remember { mutableStateOf(true) }
    var lockedApps by remember { mutableStateOf(emptyList<LockedApp>()) }

    DisposableEffect(lifecycleOwner) {
        // ON_STOP fires not only when the user leaves the app, but also for a
        // rotation, a config change, or any full-screen activity we launch
        // ourselves (file picker, account chooser). Wiping the key on every one
        // of those would break imports/exports/backups and re-prompt for the PIN
        // constantly. So: skip the wipe entirely when we launched the activity
        // ourselves, and otherwise wipe after a short grace period that is
        // cancelled if we return to the foreground quickly.
        var pendingLock: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    pendingLock?.cancel()
                    pendingLock = null
                    container.sensitiveKeyManager.expectingActivityResult = false
                    permissions = AppLockPermissionChecker.check(context)
                    // Reflect any wipe that happened while backgrounded so the
                    // gate re-shows.
                    sensitiveUnlocked = container.sensitiveKeyManager.isUnlocked()
                }
                Lifecycle.Event.ON_STOP -> {
                    if (container.sensitiveKeyManager.expectingActivityResult) {
                        // We opened a picker/chooser; keep the key so its result
                        // callback can encrypt/decrypt. Reset below on resume.
                        return@LifecycleEventObserver
                    }
                    pendingLock?.cancel()
                    pendingLock = scope.launch {
                        delay(LOCK_GRACE_MILLIS)
                        // Leaving the app wipes the in-memory sensitive key: the
                        // vault, key store, and secure notes cannot be decrypted
                        // again until the user re-enters their PIN.
                        container.sensitiveKeyManager.lock()
                        sensitiveUnlocked = false
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            pendingLock?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
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

        biometricEnabled == null -> BiometricSetupScreen(
            canUseBiometric = biometricAuthenticator.canAuthenticate(),
            message = biometricMessage,
            onEnable = {
                biometricAuthenticator.authenticate(
                    title = "Enable biometric",
                    subtitle = "Confirm once to use biometric unlock",
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

        // Mandatory session unlock: derives the sensitive-data key from the PIN.
        // Reached on every cold start and after the app returns from background.
        !sensitiveUnlocked -> ToolUnlockScreen(
            title = "Unlock DayKit",
            subtitle = "Enter your master PIN",
            pin = unlockPin,
            error = unlockError,
            pinLength = container.credentialRepository.pinLength(),
            biometricEnabled = false,
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
                        // C1 lockout runs here; only then derive the key.
                        val verifyResult = container.credentialRepository.verify(pin.toCharArray())
                        if (verifyResult is PinVerifyResult.Success) {
                            container.sensitiveKeyManager.unlock(pin.toCharArray())
                        }
                        verifyResult
                    }
                    when {
                        result is PinVerifyResult.Success && container.sensitiveKeyManager.isUnlocked() -> {
                            unlockPin = ""
                            unlockError = null
                            sensitiveUnlocked = true
                        }
                        else -> {
                            unlockPin = ""
                            unlockError = result.errorMessageOrNull() ?: "Wrong PIN"
                        }
                    }
                }
            },
            onBiometric = {},
        )

        else -> RootScaffold(
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
