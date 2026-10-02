package com.daykit.feature.keystore.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Display, masking and input rules per [KeyFieldKind]. Pure, so the masking is unit-tested. */
object KeyFieldFormat {
    const val MASK = "••••••••"
    private const val DOT = '•'

    /** The value as shown on screen; [revealed] = false masks secret kinds. */
    fun display(kind: KeyFieldKind, value: String, revealed: Boolean): String {
        if (value.isBlank()) return ""
        if (!revealed && kind.isSecret) return mask(kind, value)
        return when (kind) {
            KeyFieldKind.CardNumber -> CardNumbers.format(value)
            KeyFieldKind.Date -> formatDate(value)
            else -> value
        }
    }

    fun mask(kind: KeyFieldKind, value: String): String = when (kind) {
        KeyFieldKind.CardNumber, KeyFieldKind.MaskedId -> "$DOT$DOT$DOT$DOT ${lastFour(value)}"
        KeyFieldKind.Pin -> DOT.toString().repeat(value.length.coerceIn(3, 6))
        else -> MASK
    }

    fun lastFour(value: String): String = value.filter { !it.isWhitespace() }.takeLast(4)

    /** What a copy puts on the clipboard: the bare card number, without display spaces. */
    fun copyText(kind: KeyFieldKind, value: String): String = when (kind) {
        KeyFieldKind.CardNumber -> CardNumbers.digitsOf(value)
        else -> value
    }

    /** Cleans what the user typed into the value we store. */
    fun sanitizeInput(kind: KeyFieldKind, input: String, uppercase: Boolean = false): String {
        val clean = when (kind) {
            KeyFieldKind.CardNumber -> CardNumbers.digitsOf(input)
            KeyFieldKind.Pin -> input.filter(Char::isDigit).take(MAX_PIN_LENGTH)
            KeyFieldKind.Expiry -> input.filter(Char::isDigit).take(4)
            KeyFieldKind.MaskedId -> input.filter { !it.isWhitespace() }
            else -> input
        }
        return if (uppercase) clean.uppercase() else clean
    }

    /** Expiry is edited as up to four digits and stored as `MM/YY`. */
    fun expiryToStored(digits: String): String =
        if (digits.length <= 2) digits else "${digits.take(2)}/${digits.drop(2)}"

    fun expiryToInput(stored: String): String = stored.filter(Char::isDigit).take(4)

    fun isValidExpiry(stored: String): Boolean {
        val match = EXPIRY.matchEntire(stored) ?: return false
        return match.groupValues[1].toInt() in 1..12
    }

    fun parseDate(stored: String): LocalDate? = runCatching { LocalDate.parse(stored) }.getOrNull()

    fun formatDate(stored: String): String =
        parseDate(stored)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: stored

    private const val MAX_PIN_LENGTH = 8
    private val EXPIRY = Regex("""(\d{2})/(\d{2})""")
}
