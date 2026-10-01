package com.daykit.feature.today.ui

import com.daykit.feature.expense.data.ExpenseEntry
import com.daykit.feature.habit.data.Habit
import com.daykit.feature.habit.data.HabitDashboard
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Pure derivations behind the Today charts; kept out of composables so they're testable. */
internal object TodayStats {

    /** The seven days ending on [today], oldest first. */
    fun lastSevenDays(today: LocalDate): List<LocalDate> = (6 downTo 0).map { today.minusDays(it.toLong()) }

    /**
     * Share of build habits completed on each of [days]. A habit only counts on days it
     * already existed, so adding one today doesn't drag last week's bars down.
     */
    fun habitCompletion(
        dashboard: HabitDashboard,
        days: List<LocalDate>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Float> = days.map { date ->
        val existing = dashboard.buildHabits.filter { createdOn(it, zone) <= date }
        if (existing.isEmpty()) 0f
        else existing.count { dashboard.logFor(it.habitId, date)?.completed == true }.toFloat() / existing.size
    }

    /**
     * Consecutive completed days ending today — or yesterday, so a streak isn't shown as
     * broken just because today's check-in hasn't happened yet.
     */
    fun streak(dashboard: HabitDashboard, habitId: String, today: LocalDate): Int {
        fun done(date: LocalDate) = dashboard.logFor(habitId, date)?.completed == true
        var day = if (done(today)) today else today.minusDays(1)
        var count = 0
        while (done(day) && count < 3650) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Running total of the month's entries for day 1..[through], one value per day. */
    fun cumulativeSpend(entries: List<ExpenseEntry>, month: YearMonth, through: LocalDate): List<Long> {
        val lastDay = if (YearMonth.from(through) == month) through.dayOfMonth else month.lengthOfMonth()
        val perDay = LongArray(lastDay)
        entries.forEach { entry ->
            val day = runCatching { LocalDate.parse(entry.expenseDate).dayOfMonth }.getOrDefault(1)
            if (day <= lastDay) perDay[day - 1] += entry.amountMinor
        }
        var running = 0L
        return perDay.map { running += it; running }
    }

    /** Largest categories by amount, with the tail folded into "Other". */
    fun topCategories(entries: List<ExpenseEntry>, limit: Int = 4): List<Pair<String, Long>> {
        val sorted = entries
            .groupBy { it.category.ifBlank { "General" } }
            .map { (category, list) -> category to list.sumOf { it.amountMinor } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        if (sorted.size <= limit) return sorted
        return sorted.take(limit - 1) + ("Other" to sorted.drop(limit - 1).sumOf { it.second })
    }

    private fun createdOn(habit: Habit, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(habit.createdAtMillis).atZone(zone).toLocalDate()
}
