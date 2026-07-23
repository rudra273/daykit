package com.daykit.feature.applock.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.extendedColors
import kotlinx.coroutines.delay
import com.daykit.DayKitApplication
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.designsystem.DayKitTheme
import com.daykit.core.designsystem.components.FrostedLockBackground
import com.daykit.core.security.BiometricAuthenticator
import com.daykit.core.security.errorMessageOrNull
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

    private val focusBlockUntil: Long
        get() = intent.getLongExtra(EXTRA_FOCUS_BLOCK_UNTIL, 0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            DayKitTheme {
                // A strict focus block shows a countdown with no way out — no PIN,
                // no biometric — until the timer expires.
                if (focusBlockUntil > System.currentTimeMillis()) {
                    FocusBlockScreen(
                        appLabel = resolveLabel(lockedPackageName),
                        appIcon = resolveIcon(lockedPackageName),
                        lockUntilMillis = focusBlockUntil,
                        onExpired = { finish() },
                    )
                } else {
                    LockChallengeScreen(
                        activity = this,
                        packageName = lockedPackageName,
                        appLabel = resolveLabel(lockedPackageName),
                        appIcon = resolveIcon(lockedPackageName),
                        credentialRepository = container.credentialRepository,
                        settings = container.secureSettingRepository,
                        onUnlocked = {
                            // Belt-and-suspenders: never let a PIN grant open an app
                            // whose focus block is still active.
                            if (container.appLockRepository.focusBlockUntil(lockedPackageName) == null) {
                                AppLockSessionManager.allow(lockedPackageName)
                            }
                            finish()
                        },
                    )
                }
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
        private const val EXTRA_FOCUS_BLOCK_UNTIL = "focus_block_until"

        fun intent(context: Context, packageName: String, focusBlockUntil: Long? = null): Intent {
            return Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_PACKAGE_NAME, packageName)
                .putExtra(EXTRA_FOCUS_BLOCK_UNTIL, focusBlockUntil ?: 0L)
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
    val pinLength = remember { credentialRepository.pinLength() }
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
        if (pin.length < pinLength) return
        scope.launch {
            val candidate = pin
            val result = withContext(Dispatchers.Default) {
                credentialRepository.verify(candidate.toCharArray())
            }
            if (result is com.daykit.core.security.PinVerifyResult.Success) {
                onUnlocked()
            } else {
                error = result.errorMessageOrNull()
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
                pinLength = pinLength,
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

/**
 * Full-screen, non-dismissible countdown shown while an app is under a strict
 * focus block. There is deliberately no PIN pad or biometric — the only way out
 * is for the timer to expire, at which point [onExpired] fires and the host
 * finishes so the app underneath becomes reachable again.
 */
@Composable
private fun FocusBlockScreen(
    appLabel: String,
    appIcon: Drawable?,
    lockUntilMillis: Long,
    onExpired: () -> Unit,
) {
    // Swallow back — the app must stay unreachable until the timer ends.
    BackHandler(enabled = true) {}

    val iconPainter: Painter? = remember(appIcon) {
        appIcon?.let { BitmapPainter(it.toBitmap().asImageBitmap()) }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val remaining = (lockUntilMillis - now).coerceAtLeast(0L)

    LaunchedEffect(lockUntilMillis) {
        while (System.currentTimeMillis() < lockUntilMillis) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
        onExpired()
    }

    FrostedLockBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.extendedColors.card),
                contentAlignment = Alignment.Center,
            ) {
                if (iconPainter != null) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = appLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Locked with a focus block",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = formatFocusRemaining(remaining),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "You chose to block this app. It will unlock when the timer ends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
