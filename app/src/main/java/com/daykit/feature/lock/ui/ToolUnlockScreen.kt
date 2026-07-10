package com.daykit.feature.lock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBackButton
import com.daykit.core.designsystem.components.FrostedLockBackground

/**
 * Shared in-app tool unlock gate. Replaces the near-duplicate KeyStoreUnlock /
 * NotesUnlock / UtilityUnlock screens. The caller owns [pin]/[error] and the
 * verification in [onUnlock]; this only renders the challenge.
 */
@Composable
fun ToolUnlockScreen(
    title: String,
    subtitle: String,
    pin: String,
    error: String?,
    pinLength: Int,
    biometricEnabled: Boolean,
    icon: ImageVector,
    onBack: () -> Unit,
    onPinChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onBiometric: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { _ ->
        FrostedLockBackground {
            Box(Modifier.fillMaxSize()) {
                AppBackButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(Spacing.md),
                )
                LockChallengeContent(
                    title = title,
                    subtitle = subtitle,
                    pin = pin,
                    error = error,
                    pinLength = pinLength,
                    appIcon = icon,
                    onDigit = { d -> onPinChange((pin + d).filter(Char::isDigit).take(12)) },
                    onBackspace = { onPinChange(pin.dropLast(1)) },
                    onSubmit = onUnlock,
                    onBiometric = if (biometricEnabled) onBiometric else null,
                )
            }
        }
    }
}
