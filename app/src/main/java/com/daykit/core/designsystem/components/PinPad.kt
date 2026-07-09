package com.daykit.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.extendedColors

/** PIN entry dots. [filledCount] of [length] are filled; optional error tint. */
@Composable
fun PinDots(
    length: Int,
    filledCount: Int,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(length) { i ->
            val filled = i < filledCount
            val color = when {
                error -> MaterialTheme.colorScheme.error
                filled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** 3x4 numeric keypad with optional biometric key (bottom-left) and backspace (bottom-right). */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val rows = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit ->
                    PadKey(onClick = { onDigit(digit) }) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (onBiometric != null) {
                PadKey(onClick = onBiometric) {
                    Icon(
                        Icons.Rounded.Fingerprint,
                        contentDescription = "Use biometric",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Spacer(Modifier.size(72.dp))
            }
            PadKey(onClick = { onDigit('0') }) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            PadKey(onClick = onBackspace) {
                Icon(
                    Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PadKey(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.extendedColors.inputField)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Lock-screen background: theme background + two large soft blobs blurred behind
 * content + a scrim. Self-contained (can't sample the app behind — other process/FLAG_SECURE).
 */
@Composable
fun FrostedLockBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(60.dp),
        ) {
            val primary = MaterialTheme.colorScheme.primary
            val accent = MaterialTheme.extendedColors.accents.indigo
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .padding(24.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(primary.copy(alpha = 0.35f), Color.Transparent))
                    ),
            )
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .padding(24.dp)
                    .align(Alignment.BottomStart)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent))
                    ),
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.30f)))
        content()
    }
}
