package com.daykit.feature.today.ui

import com.daykit.feature.expense.data.ExpenseEntry
import com.daykit.feature.expense.data.ExpenseEntryKind
import com.daykit.feature.habit.data.Habit
import com.daykit.feature.habit.data.HabitDashboard
import com.daykit.feature.habit.data.HabitGoalType
import com.daykit.feature.habit.data.HabitKind
import com.daykit.feature.habit.data.HabitLog
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class TodayStatsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 10)

    private fun habit(id: String, created: LocalDate) = Habit(
        habitId = id, name = id, kind = HabitKind.Build, goalType = HabitGoalType.Check,
        targetMinutes = 0, targetCount = 0, unitLabel = "", colorIndex = 0,
        reminderEnabled = false, reminderHour = 0, reminderMinute = 0, active = true,
        createdAtMillis = created.atStartOfDay(zone).toInstant().toEpochMilli(), updatedAtMillis = 0,
    )

    private fun log(habitId: String, date: LocalDate) = HabitLog(
        logId = "$habitId-$date", habitId = habitId, date = date.toString(), minutes = 0, progressCount = 0,
        completed = true, relapse = false, note = "", createdAtMillis = 0, updatedAtMillis = 0,
    )

    private fun expense(date: String, amount: Long, category: String = "Food") = ExpenseEntry(
        entryId = "$date-$amount-$category", monthKey = "2026-10", title = "", category = category,
        amountMinor = amount, kind = ExpenseEntryKind.Daily, sourceBillId = null, expenseDate = date,
        note = "", createdAtMillis = 0, updatedAtMillis = 0,
    )

    @Test
    fun habitCompletionIgnoresHabitsNotYetCreated() {
        val dashboard = HabitDashboard(
            habits = listOf(habit("a", today.minusDays(10)), habit("b", today)),
            logs = listOf(log("a", today.minusDays(1)), log("a", today)),
            today = today,
        )
        val result = TodayStats.habitCompletion(dashboard, listOf(today.minusDays(1), today), zone)
        assertEquals(listOf(1f, 0.5f), result)
    }

    @Test
    fun streakCountsFromYesterdayWhenTodayIsOpen() {
        val logs = (1L..3L).map { log("a", today.minusDays(it)) }
        val dashboard = HabitDashboard(listOf(habit("a", today.minusDays(30))), logs, today)
        assertEquals(3, TodayStats.streak(dashboard, "a", today))
        val withToday = dashboard.copy(logs = logs + log("a", today))
        assertEquals(4, TodayStats.streak(withToday, "a", today))
    }

    @Test
    fun cumulativeSpendRunsThroughToday() {
        val entries = listOf(expense("2026-10-01", 100), expense("2026-10-03", 50), expense("2026-10-20", 999))
        val result = TodayStats.cumulativeSpend(entries, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 4))
        assertEquals(listOf(100L, 100L, 150L, 150L), result)
    }

    @Test
    fun topCategoriesFoldsTailIntoOther() {
        val entries = listOf(
            expense("2026-10-01", 500, "Rent"), expense("2026-10-01", 300, "Food"),
            expense("2026-10-01", 200, "Travel"), expense("2026-10-01", 50, "Books"),
            expense("2026-10-01", 25, "Games"),
        )
        val result = TodayStats.topCategories(entries, limit = 4)
        assertEquals(listOf("Rent" to 500L, "Food" to 300L, "Travel" to 200L, "Other" to 75L), result)
    }
}
