package com.daykit.feature.applock.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import com.daykit.DayKitApplication
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.designsystem.DayKitTheme
import com.daykit.core.designsystem.components.FrostedLockBackground
import com.daykit.core.security.BiometricAuthenticator
import com.daykit.core.session.AppLockSessionManager
import com.daykit.feature.lock.ui.LockChallengeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LockActivity : FragmentActivity() {
    private val container
        get() = (application as DayKitApplication).container

    private val lockedPackageName: String
        get() = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            DayKitTheme {
                LockChallengeScreen(
                    activity = this,
                    packageName = lockedPackageName,
                    appLabel = resolveLabel(lockedPackageName),
                    appIcon = resolveIcon(lockedPackageName),
                    credentialRepository = container.credentialRepository,
                    settings = container.secureSettingRepository,
                    onUnlocked = {
                        AppLockSessionManager.allow(lockedPackageName)
                        finish()
                    },
                )
            }
        }
    }

    private fun resolveLabel(packageName: String): String {
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    private fun resolveIcon(packageName: String): Drawable? {
        return runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "package_name"

        fun intent(context: Context, packageName: String): Intent {
            return Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_PACKAGE_NAME, packageName)
        }
    }
}

@Composable
private fun LockChallengeScreen(
    activity: FragmentActivity,
    packageName: String,
    appLabel: String,
    appIcon: Drawable?,
    credentialRepository: com.daykit.core.security.CredentialRepository,
    settings: SecureSettingRepository,
    onUnlocked: () -> Unit,
) {
    // Swallow back — the locked app must not be reachable without unlocking.
    BackHandler(enabled = true) {}

    val scope = rememberCoroutineScope()
    val biometricAuthenticator = remember(activity) { BiometricAuthenticator(activity) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricEnabled by remember { mutableStateOf(false) }

    val iconPainter: Painter? = remember(appIcon) {
        appIcon?.let { BitmapPainter(it.toBitmap().asImageBitmap()) }
    }

    fun tryBiometric(enabled: Boolean = biometricEnabled) {
        if (!enabled || !biometricAuthenticator.canAuthenticate()) return
        biometricAuthenticator.authenticate(
            title = "Unlock $appLabel",
            subtitle = "DayKit App Lock",
            onSuccess = onUnlocked,
            onError = { error = it },
        )
    }

    LaunchedEffect(Unit) {
        val enabled = settings.getBoolean(SecureSettingRepository.KEY_BIOMETRIC_ENABLED) == true
        biometricEnabled = enabled
        if (enabled) {
            tryBiometric(enabled)
        }
    }

    fun submit() {
        if (pin.length < 4) return
        scope.launch {
            val candidate = pin
            val valid = withContext(Dispatchers.Default) {
                credentialRepository.verify(candidate.toCharArray())
            }
            if (valid) {
                onUnlocked()
            } else {
                error = "Wrong PIN"
                pin = ""
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
                appIconPainter = iconPainter,
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
                onBiometric = if (biometricEnabled) { { tryBiometric() } } else null,
            )
        }
    }
}
