package com.daykit.feature.keystore.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyBitcoin
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.keystore.data.CardNumbers
import com.daykit.feature.keystore.data.KeyExpiry
import com.daykit.feature.keystore.data.KeyFieldFormat
import com.daykit.feature.keystore.data.KeyFieldKind
import com.daykit.feature.keystore.data.KeyStoreEntry
import com.daykit.feature.keystore.data.KeyType

/** One icon and one accent per type, used by the type picker, list rows and sheet header. */
internal val KeyType.icon: ImageVector
    get() = when (this) {
        KeyType.Simple -> Icons.Rounded.VpnKey
        KeyType.Login -> Icons.Rounded.AccountCircle
        KeyType.Card -> Icons.Rounded.CreditCard
        KeyType.Bank -> Icons.Rounded.AccountBalance
        KeyType.Identity -> Icons.Rounded.Badge
        KeyType.Recovery -> Icons.Rounded.LockReset
        KeyType.Crypto -> Icons.Rounded.CurrencyBitcoin
    }

@Composable
internal fun KeyType.accent(): Color {
    val a = MaterialTheme.extendedColors.accents
    return when (this) {
        KeyType.Simple -> a.indigo
        KeyType.Login -> a.blue
        KeyType.Card -> a.purple
        KeyType.Bank -> a.teal
        KeyType.Identity -> a.orange
        KeyType.Recovery -> a.green
        KeyType.Crypto -> a.yellow
    }
}

@Composable
internal fun accentFor(key: String): Color {
    val a = MaterialTheme.extendedColors.accents
    val palette = listOf(a.blue, a.teal, a.green, a.red, a.orange, a.yellow, a.purple, a.pink, a.indigo)
    return palette[kotlin.math.abs(key.hashCode()) % palette.size]
}

/**
 * Solid fill for the card-style graphics: the entry's accent pulled toward black so white
 * text stays legible in both themes. Solid on purpose — a wide gradient would band.
 */
@Composable
internal fun faceColorFor(key: String): Color =
    lerp(accentFor(key), Color.Black, if (MaterialTheme.extendedColors.isDark) 0.5f else 0.3f)

/** One-line summary under the entry name in the list; null when there's nothing safe to show. */
internal fun KeyStoreEntry.summary(): String? {
    val base = baseSummary()
    return if (KeyExpiry.isExpired(this)) listOfNotNull("Expired", base).joinToString(" · ") else base
}

private fun KeyStoreEntry.baseSummary(): String? {
    fun masked(key: String, kind: KeyFieldKind) =
        value(key).takeIf { it.isNotBlank() }?.let { KeyFieldFormat.mask(kind, it) }
    return when (type) {
        KeyType.Simple -> null
        KeyType.Login -> value("username").ifBlank { null }
        KeyType.Card -> listOfNotNull(
            CardNumbers.networkOf(value("number")).displayName.ifBlank { null },
            masked("number", KeyFieldKind.CardNumber),
            value("expiry").ifBlank { null },
        ).joinToString(" · ").ifBlank { null }
        KeyType.Bank -> masked("account", KeyFieldKind.MaskedId)?.let { "A/c $it" }
        KeyType.Identity -> listOfNotNull(
            value("doc_type").ifBlank { null },
            masked("number", KeyFieldKind.MaskedId),
        ).joinToString(" · ").ifBlank { null }
        KeyType.Recovery -> recoveryCodes.let { codes ->
            "${codes.count { it !in usedCodes }} of ${codes.size} left"
        }
        KeyType.Crypto -> value("address").takeIf { it.length > 12 }
            ?.let { "${it.take(6)}…${it.takeLast(4)}" }
            ?: value("address").ifBlank { null }
    }
}
