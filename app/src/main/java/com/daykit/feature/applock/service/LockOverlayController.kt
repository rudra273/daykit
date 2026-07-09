package com.daykit.feature.applock.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.daykit.core.designsystem.DayKitTheme
import com.daykit.core.designsystem.components.FrostedLockBackground
import com.daykit.core.security.CredentialRepository
import com.daykit.core.session.AppLockSessionManager
import com.daykit.feature.lock.ui.LockChallengeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LockOverlayController(
    private val context: Context,
    private val credentialRepository: CredentialRepository,
    private val onBiometricRequested: (String) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var showingPackageName: String? = null

    fun show(packageName: String, appLabel: String) {
        if (showingPackageName == packageName && composeView != null) return
        dismiss()

        val owner = OverlayLifecycleOwner().also { it.start() }
        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                DayKitTheme {
                    OverlayLockContent(
                        packageName = packageName,
                        appLabel = appLabel,
                        credentialRepository = credentialRepository,
                        onUnlocked = {
                            AppLockSessionManager.allow(packageName)
                            dismiss()
                        },
                        onBiometric = {
                            dismiss()
                            onBiometricRequested(packageName)
                        },
                    )
                }
            }
        }

        windowManager.addView(view, layoutParams())
        composeView = view
        lifecycleOwner = owner
        showingPackageName = packageName
    }

    fun dismiss() {
        composeView?.let { runCatching { windowManager.removeView(it) } }
        composeView = null
        showingPackageName = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
    }
}

@Composable
private fun OverlayLockContent(
    packageName: String,
    appLabel: String,
    credentialRepository: CredentialRepository,
    onUnlocked: () -> Unit,
    onBiometric: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (pin.length < 4) return
        val candidate = pin
        scope.launch {
            val valid = withContext(Dispatchers.Default) {
                credentialRepository.verify(candidate.toCharArray())
            }
            if (valid) {
                onUnlocked()
            } else {
                pin = ""
                error = "Wrong PIN"
            }
        }
    }

    FrostedLockBackground {
        Box(Modifier.fillMaxSize()) {
            LockChallengeContent(
                title = "Unlock",
                subtitle = appLabel,
                pin = pin,
                error = error,
                appIcon = Icons.Rounded.Lock,
                onDigit = { d ->
                    if (pin.length < 12) {
                        pin += d
                        error = null
                    }
                },
                onBackspace = {
                    pin = pin.dropLast(1)
                    error = null
                },
                onSubmit = { submit() },
                onBiometric = onBiometric,
            )
        }
    }
}

private class OverlayLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
