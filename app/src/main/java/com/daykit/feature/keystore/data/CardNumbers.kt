package com.daykit.feature.keystore.data

enum class CardNetwork(val displayName: String) {
    Visa("VISA"),
    Mastercard("Mastercard"),
    Amex("AMEX"),
    RuPay("RuPay"),
    Discover("Discover"),
    Diners("Diners Club"),
    Jcb("JCB"),
    Maestro("Maestro"),
    Unknown(""),
}

/** Pure card-number helpers: network detection by IIN prefix, Luhn check, digit grouping. */
object CardNumbers {
    /** Longest card number we accept (19 digits per ISO/IEC 7812). */
    const val MAX_LENGTH = 19

    fun digitsOf(input: String): String = input.filter(Char::isDigit).take(MAX_LENGTH)

    /** Order matters: RuPay's 60/65/353/356 ranges overlap Discover and JCB. */
    fun networkOf(number: String): CardNetwork {
        val n = digitsOf(number)
        if (n.isEmpty()) return CardNetwork.Unknown
        fun prefixIn(length: Int, range: IntRange): Boolean =
            n.length >= length && n.take(length).toInt() in range
        return when {
            n.startsWith("4") -> CardNetwork.Visa
            n.startsWith("34") || n.startsWith("37") -> CardNetwork.Amex
            prefixIn(2, 51..55) || prefixIn(4, 2221..2720) -> CardNetwork.Mastercard
            n.startsWith("6011") -> CardNetwork.Discover
            prefixIn(2, 60..60) || prefixIn(2, 65..65) || prefixIn(2, 81..82) ||
                prefixIn(3, 508..508) || prefixIn(3, 353..353) || prefixIn(3, 356..356) -> CardNetwork.RuPay
            prefixIn(3, 644..649) -> CardNetwork.Discover
            prefixIn(2, 36..36) || prefixIn(2, 38..39) || prefixIn(3, 300..305) -> CardNetwork.Diners
            prefixIn(2, 35..35) -> CardNetwork.Jcb
            prefixIn(2, 50..50) || prefixIn(2, 56..58) || prefixIn(2, 63..63) || prefixIn(2, 67..67) ->
                CardNetwork.Maestro
            else -> CardNetwork.Unknown
        }
    }

    fun passesLuhn(number: String): Boolean {
        val n = digitsOf(number)
        if (n.length < 12) return false
        val sum = n.reversed().mapIndexed { index, c ->
            val d = c - '0'
            if (index % 2 == 1) (d * 2).let { if (it > 9) it - 9 else it } else d
        }.sum()
        return sum % 10 == 0
    }

    /** Indices (into the digit string) before which a space is shown. Amex is 4-6-5. */
    fun groupBreaks(number: String): List<Int> =
        if (networkOf(number) == CardNetwork.Amex) listOf(4, 10) else listOf(4, 8, 12, 16)

    fun format(number: String): String {
        val n = digitsOf(number)
        val breaks = groupBreaks(n)
        return buildString {
            n.forEachIndexed { index, c ->
                if (index in breaks) append(' ')
                append(c)
            }
        }
    }
}
