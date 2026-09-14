package com.daykit.feature.widget

import java.math.BigDecimal
import java.math.RoundingMode

internal fun String.cleanWidgetAmountInput(): String {
    val filtered = filter { it in '0'..'9' || it == '.' }
    val whole = filtered.substringBefore('.').take(9)
    return if ('.' in filtered) whole + "." + filtered.substringAfter('.')
        .filter { it in '0'..'9' }.take(2) else whole
}

internal fun String.toWidgetMinorOrNull(): Long? = runCatching {
    BigDecimal(this).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
}.getOrNull()

