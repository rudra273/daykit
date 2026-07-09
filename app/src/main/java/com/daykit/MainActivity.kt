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
import com.daykit.core.designsystem.DayKitTheme
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.feature.applock.data.LockedApp
import com.daykit.feature.applock.service.AppMonitorService
import com.daykit.feature.applock.ui.BiometricSetupScreen
import com.daykit.feature.applock.ui.PermissionGrantScreen
import com.daykit.feature.applock.ui.SetupCredentialScreen
import com.daykit.navigation.RootScaffold
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
    var permissions by remember { mutableStateOf(AppLockPermissionChecker.check(context)) }
    var biometricMessage by remember { mutableStateOf<String?>(null) }
    var biometricPreferenceLoaded by remember { mutableStateOf(false) }
    var biometricEnabled by remember { mutableStateOf<Boolean?>(null) }
    var screenshotProtection by remember { mutableStateOf(true) }
    var lockedApps by remember { mutableStateOf(emptyList<LockedApp>()) }
    var accessibilityEnabled by remember {
        mutableStateOf(AppLockPermissionChecker.hasAccessibilityService(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = AppLockPermissionChecker.check(context)
                accessibilityEnabled = AppLockPermissionChecker.hasAccessibilityService(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    LaunchedEffect(permissions.allGranted, credentialReady, lockedApps.size, accessibilityEnabled) {
        if (accessibilityEnabled) {
            AppMonitorService.stop(context)
        } else if (permissions.allGranted && credentialReady) {
            AppMonitorService.start(context)
        }
    }

    when {
        !credentialReady -> SetupCredentialScreen(
            onCredentialReady = { pin ->
                scope.launch {
                    withContext(Dispatchers.Default) {
                        container.credentialRepository.saveCredential(pin.toCharArray())
                    }
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

        else -> RootScaffold(
            activity = activity,
            container = container,
            lockedCount = lockedApps.size,
            onAppLockSelectionChanged = {
                permissions = AppLockPermissionChecker.check(context)
                accessibilityEnabled = AppLockPermissionChecker.hasAccessibilityService(context)
                if (permissions.allGranted && !accessibilityEnabled) {
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
