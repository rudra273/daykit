package com.daykit.feature.lock.ui

import com.daykit.core.security.HideOverlayWindows
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.PinDots
import com.daykit.core.designsystem.components.PinPad
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.security.CredentialKind
import com.daykit.core.security.label
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Stateless PIN-challenge surface shared by every lock host (LockActivity, the
 * overlay window, and in-app tool gates). The caller owns the PIN string and
 * verification; this only renders identity + dots + pad and animates the shake.
 *
 * Auto-submit: whenever [pin] length reaches [pinLength] the caller's [onSubmit]
 * fires. [pinLength] must be the stored PIN's actual digit count
 * (CredentialRepository.pinLength()) so the dots match what the user has to type.
 *
 * For a [CredentialKind.Password] the pad is replaced by a password field: the
 * length is unknown, so it submits from the keyboard's Done key or the Unlock
 * button instead, and edits arrive through [onTextChange].
 *
 * [onForgotCredential], when set, adds a "Forgot PIN?" link under the input.
 */
@Composable
fun LockChallengeContent(
    title: String,
    subtitle: String,
    pin: String,
    error: String?,
    pinLength: Int,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    appIcon: ImageVector? = null,
    appIconPainter: Painter? = null,
    onBiometric: (() -> Unit)? = null,
    credentialKind: CredentialKind = CredentialKind.Pin,
    onTextChange: (String) -> Unit = {},
    onForgotCredential: (() -> Unit)? = null,
) {
    HideOverlayWindows()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }

    // Auto-submit on the exact keystroke that completes the PIN; on a wrong
    // PIN the caller clears [pin], re-arming this.
    LaunchedEffect(pin) {
        if (credentialKind == CredentialKind.Pin && pin.length == pinLength) onSubmit()
    }

    // Error -> shake + haptic.
    LaunchedEffect(error) {
        if (error != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                shake.snapTo(0f)
                shake.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 320
                        (-14f) at 40
                        14f at 90
                        (-10f) at 140
                        10f at 190
                        (-4f) at 240
                        0f at 320
                    },
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // App identity
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.extendedColors.card),
            contentAlignment = Alignment.Center,
        ) {
            when {
                appIconPainter != null -> Icon(
                    painter = appIconPainter,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                    modifier = Modifier.size(40.dp),
                )
                appIcon != null -> Icon(
                    imageVector = appIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = error ?: subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        if (credentialKind == CredentialKind.Password) {
            PasswordEntry(
                password = pin,
                error = error != null,
                onPasswordChange = onTextChange,
                onSubmit = onSubmit,
                onBiometric = onBiometric,
                modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
            )
        } else {
            PinDots(
                length = pinLength,
                filledCount = pin.length.coerceAtMost(pinLength),
                error = error != null,
                modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
            )
            Spacer(Modifier.height(Spacing.xxl))
            PinPad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                onBiometric = onBiometric,
            )
        }
        if (onForgotCredential != null) {
            Spacer(Modifier.height(Spacing.md))
            AppTextButton(
                text = "Forgot ${credentialKind.label}?",
                color = MaterialTheme.extendedColors.textMuted,
                onClick = onForgotCredential,
            )
        }
    }
}

@Composable
private fun PasswordEntry(
    password: String,
    error: Boolean,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBiometric: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    // Bring the keyboard up straight away, as the PIN pad is always visible.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Master password",
            isError = error,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) onSubmit() }),
            modifier = Modifier.focusRequester(focusRequester),
        )
        PrimaryButton(
            text = "Unlock",
            enabled = password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            onClick = onSubmit,
        )
        if (onBiometric != null) {
            SecondaryButton(
                text = "Use fingerprint",
                leadingIcon = {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.fillMaxWidth(),
                onClick = onBiometric,
            )
        }
    }
}
