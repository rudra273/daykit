package com.daykit.feature.keystore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daykit.core.designsystem.MinTouchTarget
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.keystore.data.CardNetwork
import com.daykit.feature.keystore.data.CardNumbers
import com.daykit.feature.keystore.data.KeyExpiry
import com.daykit.feature.keystore.data.KeyFieldFormat
import com.daykit.feature.keystore.data.KeyFieldKind
import com.daykit.feature.keystore.data.KeyStoreEntry
import com.daykit.feature.keystore.data.KeyType

/** ISO/IEC 7810 ID-1, the shape of a bank card. */
private const val CardAspectRatio = 1.586f

private val OnFace = Color.White
private val OnFaceMuted = Color.White.copy(alpha = 0.72f)

/** Whether [KeyHero] draws anything for this entry. */
internal fun KeyStoreEntry.hasHero(): Boolean = when (type) {
    KeyType.Simple -> false
    KeyType.Recovery -> recoveryCodes.isNotEmpty()
    KeyType.Crypto -> value("seed").isNotBlank()
    else -> true
}

/** Fields the hero already shows with its own copy controls, left out of the field list. */
internal val HeroOwnedKinds = setOf(KeyFieldKind.RecoveryCodes, KeyFieldKind.SeedPhrase)

/**
 * The graphic at the top of the detail sheet. Secrets start masked; the eye on each graphic
 * reveals them in place, so the user can read a value without copying it.
 */
@Composable
internal fun KeyHero(
    entry: KeyStoreEntry,
    onCopy: (label: String, value: String) -> Unit,
    onToggleCode: (code: String, used: Boolean) -> Unit,
) {
    var revealed by rememberSaveable(entry.entryId) { mutableStateOf(false) }
    val toggle = { revealed = !revealed }
    when (entry.type) {
        KeyType.Simple -> Unit
        KeyType.Card -> CardFace(entry, revealed, toggle)
        KeyType.Bank -> BankFace(entry, revealed, toggle)
        KeyType.Identity -> IdentityFace(entry, revealed, toggle)
        KeyType.Login -> LoginFace(entry, revealed, toggle)
        KeyType.Recovery -> RecoveryCodesPanel(entry, revealed, toggle, onCopy, onToggleCode)
        KeyType.Crypto -> SeedPhraseFace(entry, revealed, toggle, onCopy)
    }
}

// ---- Shared pieces ----

/**
 * Solid face with two hard-edged translucent discs for texture. Crisp edges don't band on an
 * 8-bit panel the way a soft radial gradient would.
 */
@Composable
private fun FaceSurface(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(color)
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    radius = size.width * 0.55f,
                    center = Offset(size.width, 0f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = size.width * 0.38f,
                    center = Offset(size.width * 0.08f, size.height * 1.1f),
                )
            },
        content = content,
    )
}

@Composable
private fun RevealButton(revealed: Boolean, onToggle: () -> Unit, tint: Color) {
    IconButton(onClick = onToggle, modifier = Modifier.size(MinTouchTarget)) {
        Icon(
            if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = if (revealed) "Hide details" else "Show details",
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun FaceCaption(
    caption: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = OnFace,
    captionColor: Color = OnFaceMuted,
    monospace: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = caption.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = captionColor,
            letterSpacing = 0.8.sp,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FaceHeader(
    title: String,
    revealed: Boolean,
    onToggle: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.md))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OnFace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RevealButton(revealed, onToggle, OnFace)
    }
}

private fun display(kind: KeyFieldKind, value: String, revealed: Boolean): String =
    KeyFieldFormat.display(kind, value, revealed)

// ---- Card ----

/** Card number as printed on the face: full grouping, every digit but the last four masked. */
private fun cardFaceNumber(number: String, revealed: Boolean): String {
    if (number.isBlank()) return "•••• •••• •••• ••••"
    val formatted = CardNumbers.format(number)
    if (revealed) return formatted
    val visibleFrom = CardNumbers.digitsOf(number).length - 4
    var digitIndex = 0
    return formatted.map { c ->
        if (c.isDigit()) (if (digitIndex++ < visibleFrom) '•' else c) else c
    }.joinToString("")
}

@Composable
private fun CardFace(entry: KeyStoreEntry, revealed: Boolean, onToggle: () -> Unit) {
    val number = entry.value("number")
    val network = CardNumbers.networkOf(number)
    FaceSurface(faceColorFor(entry.name), Modifier.aspectRatio(CardAspectRatio)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Spacing.lg + Spacing.xs, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.lg),
        ) {
            FaceHeader(entry.name, revealed, onToggle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardChip()
                Spacer(Modifier.width(Spacing.md))
                Icon(
                    Icons.Rounded.Contactless,
                    contentDescription = null,
                    tint = OnFaceMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = cardFaceNumber(number, revealed),
                style = MaterialTheme.typography.titleLarge,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                color = OnFace,
                maxLines = 1,
                modifier = Modifier.semantics {
                    contentDescription = if (revealed) "Card number $number"
                    else "Card number ending ${KeyFieldFormat.lastFour(number)}"
                },
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                modifier = Modifier.padding(end = Spacing.md),
            ) {
                FaceCaption(
                    caption = "Card holder",
                    value = entry.value("holder").ifBlank { "—" },
                    modifier = Modifier.weight(1f),
                )
                entry.value("expiry").takeIf { it.isNotBlank() }?.let {
                    FaceCaption(if (KeyExpiry.isExpired(entry)) "Expired" else "Valid thru", it, monospace = true)
                }
                entry.value("cvv").takeIf { it.isNotBlank() }?.let {
                    FaceCaption("CVV", display(KeyFieldKind.Pin, it, revealed), monospace = true)
                }
                if (network != CardNetwork.Unknown) NetworkMark(network)
            }
        }
    }
}

@Composable
private fun CardChip() {
    val gold = lerp(MaterialTheme.extendedColors.accents.yellow, Color.White, 0.3f)
    val line = lerp(gold, Color.Black, 0.25f)
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 28.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(gold)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
                drawLine(line, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), stroke)
                drawLine(line, Offset(size.width * 2 / 3, 0f), Offset(size.width * 2 / 3, size.height), stroke)
            },
    )
}

/** A text wordmark, not the network's logo. */
@Composable
private fun NetworkMark(network: CardNetwork) {
    Text(
        text = network.displayName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        fontStyle = if (network == CardNetwork.Visa) FontStyle.Italic else FontStyle.Normal,
        color = OnFace,
        maxLines = 1,
    )
}

// ---- Bank ----

@Composable
private fun BankFace(entry: KeyStoreEntry, revealed: Boolean, onToggle: () -> Unit) {
    val account = entry.value("account")
    FaceSurface(faceColorFor(entry.name)) {
        Column(
            modifier = Modifier.padding(
                start = Spacing.lg + Spacing.xs,
                end = Spacing.xs,
                top = Spacing.xs,
                bottom = Spacing.lg + Spacing.xs,
            ),
        ) {
            FaceHeader(
                title = entry.name,
                revealed = revealed,
                onToggle = onToggle,
                leading = { FaceIconBadge(KeyType.Bank) },
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "ACCOUNT NUMBER",
                style = MaterialTheme.typography.labelSmall,
                color = OnFaceMuted,
                letterSpacing = 0.8.sp,
            )
            Text(
                text = when {
                    account.isBlank() -> "—"
                    revealed -> account.chunked(4).joinToString(" ")
                    else -> KeyFieldFormat.mask(KeyFieldKind.MaskedId, account)
                },
                style = MaterialTheme.typography.titleLarge,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                color = OnFace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOf("IFSC" to "ifsc", "Holder" to "holder", "UPI" to "upi")
                .mapNotNull { (caption, key) -> entry.value(key).takeIf { it.isNotBlank() }?.let { caption to it } }
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    modifier = Modifier.padding(end = Spacing.md),
                ) {
                    details.forEach { (caption, value) ->
                        FaceCaption(caption, value, modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceIconBadge(type: KeyType) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(type.icon, contentDescription = null, tint = OnFace, modifier = Modifier.size(19.dp))
    }
}

// ---- ID document ----

@Composable
private fun IdentityFace(entry: KeyStoreEntry, revealed: Boolean, onToggle: () -> Unit) {
    val accent = KeyType.Identity.accent()
    val colors = MaterialTheme.extendedColors
    val expired = KeyExpiry.isExpired(entry)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(accent.asAccentContainer()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(lerp(accent, Color.Black, if (colors.isDark) 0.45f else 0.15f))
                .padding(start = Spacing.lg, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.value("doc_type").ifBlank { entry.name }.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = OnFace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RevealButton(revealed, onToggle, OnFace)
        }
        Row(modifier = Modifier.padding(Spacing.lg)) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 68.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(accent.copy(alpha = if (colors.isDark) 0.24f else 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = accent, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = entry.value("holder").ifBlank { entry.name },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = display(KeyFieldKind.MaskedId, entry.value("number"), revealed).ifBlank { "—" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    entry.value("issued").takeIf { it.isNotBlank() }?.let {
                        FaceCaption(
                            caption = "Issued",
                            value = KeyFieldFormat.formatDate(it),
                            color = MaterialTheme.colorScheme.onSurface,
                            captionColor = colors.textMuted,
                        )
                    }
                    entry.value("expires").takeIf { it.isNotBlank() }?.let {
                        FaceCaption(
                            caption = if (expired) "Expired" else "Valid until",
                            value = KeyFieldFormat.formatDate(it),
                            color = if (expired) colors.danger else MaterialTheme.colorScheme.onSurface,
                            captionColor = if (expired) colors.danger else colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

// ---- Login ----

@Composable
private fun LoginFace(entry: KeyStoreEntry, revealed: Boolean, onToggle: () -> Unit) {
    FaceSurface(faceColorFor(entry.name)) {
        Column(
            modifier = Modifier.padding(
                start = Spacing.lg + Spacing.xs,
                end = Spacing.xs,
                top = Spacing.xs,
                bottom = Spacing.lg + Spacing.xs,
            ),
        ) {
            FaceHeader(
                title = entry.name,
                revealed = revealed,
                onToggle = onToggle,
                leading = {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = entry.name.trim().firstOrNull()?.uppercase() ?: "#",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnFace,
                        )
                    }
                },
            )
            entry.value("website").takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnFaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = Spacing.md),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                modifier = Modifier.padding(end = Spacing.md),
            ) {
                entry.value("username").takeIf { it.isNotBlank() }?.let {
                    FaceCaption("Username", it, modifier = Modifier.weight(1f))
                }
                entry.value("password").takeIf { it.isNotBlank() }?.let {
                    FaceCaption(
                        caption = "Password",
                        value = display(KeyFieldKind.Secret, it, revealed),
                        monospace = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ---- Recovery codes ----

@Composable
private fun RecoveryCodesPanel(
    entry: KeyStoreEntry,
    revealed: Boolean,
    onToggle: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onToggleCode: (code: String, used: Boolean) -> Unit,
) {
    val colors = MaterialTheme.extendedColors
    val codes = entry.recoveryCodes
    val left = codes.count { it !in entry.usedCodes }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.inputField)
            .padding(start = Spacing.lg, end = Spacing.xs, bottom = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$left of ${codes.size} codes left",
                style = MaterialTheme.typography.titleSmall,
                color = if (left == 0) colors.danger else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            RevealButton(revealed, onToggle, colors.textMuted)
        }
        codes.forEachIndexed { index, code ->
            val used = code in entry.usedCodes
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${index + 1}".padStart(2),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textMuted,
                )
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = if (revealed) code else KeyFieldFormat.MASK,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = if (used) colors.textMuted else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (used) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onCopy("Code ${index + 1}", code) }, modifier = Modifier.size(MinTouchTarget)) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code ${index + 1}",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = { onToggleCode(code, !used) }, modifier = Modifier.size(MinTouchTarget)) {
                    Icon(
                        if (used) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (used) "Mark code ${index + 1} unused" else "Mark code ${index + 1} used",
                        tint = if (used) colors.success else colors.textMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

// ---- Crypto seed phrase ----

@Composable
private fun SeedPhraseFace(
    entry: KeyStoreEntry,
    revealed: Boolean,
    onToggle: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
) {
    val seed = entry.value("seed")
    val words = seed.trim().split(Regex("\\s+"))
    FaceSurface(faceColorFor(entry.name)) {
        Column(
            modifier = Modifier.padding(
                start = Spacing.lg,
                end = Spacing.xs,
                top = Spacing.xs,
                bottom = Spacing.lg,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaceIconBadge(KeyType.Crypto)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnFace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Recovery phrase · ${words.size} words",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnFaceMuted,
                    )
                }
                IconButton(onClick = { onCopy("Recovery phrase", seed.trim()) }, modifier = Modifier.size(MinTouchTarget)) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy recovery phrase",
                        tint = OnFace,
                        modifier = Modifier.size(20.dp),
                    )
                }
                RevealButton(revealed, onToggle, OnFace)
            }
            Spacer(Modifier.height(Spacing.md))
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.padding(end = Spacing.md),
            ) {
                words.chunked(3).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        row.forEachIndexed { column, word ->
                            SeedWord(
                                number = rowIndex * 3 + column + 1,
                                word = if (revealed) word else "•••••",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeedWord(number: Int, word: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.labelSmall,
            color = OnFaceMuted,
            modifier = Modifier.width(18.dp),
        )
        Text(
            text = word,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = OnFace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
