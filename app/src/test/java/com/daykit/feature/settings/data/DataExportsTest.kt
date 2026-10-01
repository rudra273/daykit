package com.daykit.feature.settings.data

import com.daykit.feature.expense.data.ExpenseEntry
import com.daykit.feature.expense.data.ExpenseEntryKind
import com.daykit.feature.notes.data.SecureNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class DataExportsTest {
    private fun expense(date: String, title: String, amountMinor: Long, note: String = "") = ExpenseEntry(
        entryId = title,
        monthKey = date.take(7),
        title = title,
        category = "Food",
        amountMinor = amountMinor,
        kind = ExpenseEntryKind.Daily,
        sourceBillId = null,
        expenseDate = date,
        note = note,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )

    @Test fun csvIsSortedQuotedAndInMajorUnits() {
        val csv = DataExports.expensesCsv(
            listOf(
                expense("2026-09-02", "Lunch, with \"team\"", 45050, note = "line1\nline2"),
                expense("2026-09-01", "Tea", 2000),
            ),
            currencyCode = "INR",
        )
        val lines = csv.split("\r\n")
        assertEquals("date,title,category,amount,currency,type,note", lines[0])
        assertEquals("2026-09-01,Tea,Food,20.00,INR,Daily,", lines[1])
        assertEquals("2026-09-02,\"Lunch, with \"\"team\"\"\",Food,450.50,INR,Daily,\"line1\nline2\"", lines[2])
    }

    @Test fun notesAreNewestFirstWithTitles() {
        val notes = listOf(
            SecureNote(1, "a", "Old", "first", "", 1, 0L, 0L),
            SecureNote(2, "b", "", "second", "work", 1, 0L, 86_400_000L),
        )
        val text = DataExports.notesMarkdown(notes, ZoneOffset.UTC)
        assertTrue(text.startsWith("# Untitled\n_Updated 2 Jan 1970 · work_\n\nsecond\n"))
        assertTrue(text.contains("\n---\n\n# Old\n"))
    }
}
