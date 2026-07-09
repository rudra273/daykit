package com.daykit.core.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Shared INR money formatting for amounts stored in minor units (paise). */
object Money {
    private val locale = Locale("en", "IN")

    fun format(amountMinor: Long): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        format.currency = Currency.getInstance("INR")
        return format.format(amountMinor / 100.0)
    }

    /** Compact form for axes/labels: ₹1L, ₹12k, ₹450. */
    fun compact(amountMinor: Long): String {
        val rupees = amountMinor / 100
        return when {
            rupees >= 100_000 -> "₹${rupees / 100_000}L"
            rupees >= 1_000 -> "₹${rupees / 1_000}k"
            else -> "₹$rupees"
        }
    }
}
