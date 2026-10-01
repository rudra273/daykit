package com.daykit.feature.settings.data

import com.daykit.feature.expense.data.ExpenseEntry
import com.daykit.feature.notes.data.SecureNote
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Plain, unencrypted per-tool exports for use in other apps. Unlike the backup
 * these are readable by anyone who gets the file, which the UI says up front.
 */
object DataExports {
    /** RFC 4180 CSV of every expense, oldest first. Amounts are in major units. */
    fun expensesCsv(entries: List<ExpenseEntry>, currencyCode: String): String = buildString {
        append("date,title,category,amount,currency,type,note\r\n")
        entries.sortedWith(compareBy({ it.expenseDate }, { it.createdAtMillis })).forEach { entry ->
            listOf(
                entry.expenseDate,
                entry.title,
                entry.category,
                BigDecimal.valueOf(entry.amountMinor, 2).toPlainString(),
                currencyCode,
                entry.kind.name,
                entry.note,
            ).joinTo(this, separator = ",", transform = ::csvField)
            append("\r\n")
        }
    }

    /** Every note as Markdown, newest first. Images are not included. */
    fun notesMarkdown(notes: List<SecureNote>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
        notes.sortedByDescending { it.updatedAtMillis }.forEachIndexed { index, note ->
            if (index > 0) append("\n---\n\n")
            append("# ").appendLine(note.title.ifBlank { "Untitled" })
            val updated = Instant.ofEpochMilli(note.updatedAtMillis).atZone(zone).format(dateFormat)
            append("_Updated ").append(updated)
            if (note.labels.isNotBlank()) append(" · ").append(note.labels)
            appendLine("_")
            appendLine()
            appendLine(note.content.trimEnd())
        }
    }

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
