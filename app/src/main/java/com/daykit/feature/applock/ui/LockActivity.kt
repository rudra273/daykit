package com.daykit.feature.applock.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.daykit.DayKitApplication
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.security.BiometricAuthenticator
import com.daykit.core.session.AppLockSessionManager
import com.daykit.core.ui.Cyan
import com.daykit.core.ui.DayKitTheme
import com.daykit.core.ui.MutedText
import com.daykit.core.ui.Panel
import com.daykit.core.ui.SoftText
import com.daykit.core.ui.Teal
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
    credentialRepository: com.daykit.core.security.CredentialRepository,
    settings: SecureSettingRepository,
    onUnlocked: () -> Unit,
) {
    BackHandler(enabled = true) {}

    val scope = rememberCoroutineScope()
    val biometricAuthenticator = remember(activity) { BiometricAuthenticator(activity) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricEnabled by remember { mutableStateOf(false) }

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

    LaunchedEffect(pin) {
        if (pin.length >= 4) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Teal, Cyan))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFF001716), modifier = Modifier.size(34.dp))
            }
            Text(
                text = "Unlock",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = appLabel,
                color = SoftText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = packageName,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PinDots(count = pin.length)
            error?.let {
                Text(text = it, color = Color(0xFFFFA8A8), style = MaterialTheme.typography.bodyMedium)
            }
            PinPad(
                biometricEnabled = biometricEnabled,
                onDigit = { digit ->
                    if (pin.length < 12) {
                        pin += digit
                        error = null
                    }
                },
                onBackspace = {
                    pin = pin.dropLast(1)
                    error = null
                },
                onBiometric = { tryBiometric() },
            )
        }
    }
}

@Composable
private fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (index < count) Cyan else Panel),
            )
        }
    }
}

@Composable
private fun PinPad(
    biometricEnabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit ->
                    KeyButton(label = digit, onClick = { onDigit(digit) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconKeyButton(
                enabled = biometricEnabled,
                icon = Icons.Rounded.Fingerprint,
                onClick = onBiometric,
            )
            KeyButton(label = "0", onClick = { onDigit("0") })
            IconKeyButton(
                enabled = true,
                icon = Icons.Rounded.Backspace,
                onClick = onBackspace,
            )
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(76.dp, 58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Panel)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IconKeyButton(
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(76.dp, 58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Panel else Panel.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) SoftText else MutedText.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp),
        )
    }
}
