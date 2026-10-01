package com.daykit.core.util

import com.daykit.core.data.AppPreferences
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Money formatting for amounts stored in minor units (1/100 of the currency).
 * The currency is the user's choice in Settings → General; INR keeps the
 * Indian lakh grouping, everything else follows the device locale.
 */
object Money {
    /** Common currencies offered in the picker, INR first as the historical default. */
    val COMMON_CURRENCIES = listOf(
        "INR", "USD", "EUR", "GBP", "JPY", "CNY", "AUD", "CAD", "SGD", "AED",
        "SAR", "CHF", "SEK", "NZD", "HKD", "KRW", "BRL", "ZAR", "MXN", "IDR",
        "THB", "MYR", "PHP", "PKR", "BDT", "LKR", "NPR", "NGN", "KES", "TRY",
    )

    private val currency: Currency
        get() = Currency.getInstance(AppPreferences.currencyCode)

    private val locale: Locale
        get() = if (currency.currencyCode == "INR") Locale("en", "IN") else Locale.getDefault()

    fun symbol(): String = currency.getSymbol(locale)

    fun format(amountMinor: Long): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = currency
        return format.format(amountMinor / 100.0)
    }

    /** Compact form for axes/labels: ₹1L, ₹12k, $1.2M, ₹450. */
    fun compact(amountMinor: Long): String {
        val symbol = symbol()
        val whole = amountMinor / 100
        return if (currency.currencyCode == "INR") {
            when {
                whole >= 100_000 -> "$symbol${whole / 100_000}L"
                whole >= 1_000 -> "$symbol${whole / 1_000}k"
                else -> "$symbol$whole"
            }
        } else {
            when {
                whole >= 1_000_000 -> "$symbol${whole / 1_000_000}M"
                whole >= 1_000 -> "$symbol${whole / 1_000}k"
                else -> "$symbol$whole"
            }
        }
    }

    /** "Indian Rupee (₹)" for the currency picker. */
    fun displayName(code: String): String {
        val c = Currency.getInstance(code)
        return "${c.getDisplayName(Locale.getDefault())} (${c.getSymbol(if (code == "INR") Locale("en", "IN") else Locale.getDefault())})"
    }
}
