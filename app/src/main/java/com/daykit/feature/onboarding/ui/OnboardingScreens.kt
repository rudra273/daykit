package com.daykit.feature.onboarding.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.FrostedLockBackground
import com.daykit.core.designsystem.components.PinDots
import com.daykit.core.designsystem.components.PinPad
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.permissions.AppLockPermissionState
import com.daykit.core.permissions.PermissionIntents

/** Shared onboarding scaffold: step dots, icon, headline + copy, and a body slot. */
@Composable
private fun OnboardingScaffold(
    stepIndex: Int,
    stepCount: Int,
    icon: ImageVector,
    headline: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Step dots
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                repeat(stepCount) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == stepIndex) 22.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == stepIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.extendedColors.divider,
                            ),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
            AccentIconTile(
                icon = icon,
                accent = MaterialTheme.colorScheme.primary,
                size = 64.dp,
                iconSize = 34.dp,
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.extendedColors.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
            content()
        }
    }
}

@Composable
fun SetupCredentialScreen(
    onCredentialReady: (String) -> Unit,
) {
    // Two-phase PinPad flow: enter, then confirm.
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val confirming = firstPin != null
    val subtitle = when {
        error != null -> error!!
        confirming -> "Re-enter your PIN to confirm"
        else -> "Choose a 4–12 digit PIN to secure DayKit"
    }

    FrostedLockBackground {
        OnboardingScaffold(
            stepIndex = 0,
            stepCount = 3,
            icon = Icons.Rounded.Security,
            headline = if (confirming) "Confirm PIN" else "Create PIN",
            subtitle = subtitle,
        ) {
            Spacer(Modifier.height(Spacing.sm))
            PinDots(length = 4, filledCount = pin.length.coerceAtMost(4), error = error != null)
            Spacer(Modifier.height(Spacing.xxl))
            PinPad(
                onDigit = { d ->
                    if (pin.length < 12) {
                        pin += d
                        error = null
                    }
                },
                onBackspace = { pin = pin.dropLast(1); error = null },
            )
            Spacer(Modifier.height(Spacing.xl))
            PrimaryButton(
                text = if (confirming) "Confirm" else "Continue",
                enabled = pin.length >= 4,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!confirming) {
                        firstPin = pin
                        pin = ""
                    } else {
                        if (pin == firstPin) {
                            onCredentialReady(pin)
                        } else {
                            error = "PINs do not match"
                            pin = ""
                            firstPin = null
                        }
                    }
                },
            )
            if (confirming) {
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton(
                    text = "Start over",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { firstPin = null; pin = ""; error = null },
                )
            }
        }
    }
}

@Composable
fun BiometricSetupScreen(
    canUseBiometric: Boolean,
    message: String?,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
) {
    OnboardingScaffold(
        stepIndex = 1,
        stepCount = 3,
        icon = Icons.Rounded.Fingerprint,
        headline = "Biometric unlock",
        subtitle = if (canUseBiometric) {
            "Use your fingerprint or face to unlock DayKit tools quickly."
        } else {
            "Biometrics are unavailable on this device. You'll use your master PIN."
        },
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    tint = if (canUseBiometric) MaterialTheme.extendedColors.success else MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (canUseBiometric) "Ready" else "Unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (canUseBiometric) "Strong biometric detected" else "Use the master PIN on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.textMuted,
                    )
                }
            }
        }
        message?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(it, color = MaterialTheme.extendedColors.warning, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.weight(1f))
        if (canUseBiometric) {
            PrimaryButton(
                text = "Enable biometric",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = onEnable,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        SecondaryButton(
            text = if (canUseBiometric) "Skip for now" else "Continue",
            modifier = Modifier.fillMaxWidth(),
            onClick = onSkip,
        )
    }
}

@Composable
fun PermissionGrantScreen(
    permissions: AppLockPermissionState,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current

    fun openSettings(block: () -> android.content.Intent) {
        try {
            context.startActivity(block())
        } catch (_: ActivityNotFoundException) {
            onRefresh()
        }
    }

    OnboardingScaffold(
        stepIndex = 2,
        stepCount = 3,
        icon = Icons.Rounded.Settings,
        headline = "Permissions",
        subtitle = "App Lock needs these to detect and cover locked apps. Everything stays on your device.",
    ) {
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            PermissionRow(
                icon = Icons.Rounded.Apps,
                title = "Usage access",
                description = "Detect which app is in the foreground.",
                granted = permissions.usageAccess,
                onClick = { openSettings { PermissionIntents.usageAccessSettings() } },
            )
            PermissionRow(
                icon = Icons.Rounded.VisibilityOff,
                title = "Display over other apps",
                description = "Show the lock screen over a protected app.",
                granted = permissions.overlay,
                onClick = { openSettings { PermissionIntents.overlaySettings(context) } },
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = "Continue",
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
            onClick = onRefresh,
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (granted) Modifier else Modifier.clickable(onClick = onClick))
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentIconTile(
            icon = icon,
            accent = if (granted) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textMuted)
        }
        if (granted) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = MaterialTheme.extendedColors.success,
                modifier = Modifier.size(24.dp),
            )
        } else {
            SecondaryButton(text = "Grant", onClick = onClick)
        }
    }
}
