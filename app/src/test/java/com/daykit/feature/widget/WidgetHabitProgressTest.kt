package com.daykit.feature.widget

import com.daykit.feature.habit.data.*
import org.junit.Assert.*
import org.junit.Test

class WidgetHabitProgressTest {
    private fun habit(type: HabitGoalType, target: Int) = Habit(
        "id", "Read", HabitKind.Build, type, target, target, "pages", 0,
        false, 9, 0, true, 0, 0)
    private fun log(amount: Int) = HabitLog("log", "id", "2026-09-14", amount,
        amount, false, false, "Keep this note", 0, 0)

    @Test fun zeroTargetProgressAgreesWithCompletion() {
        listOf(HabitGoalType.Time, HabitGoalType.Count).forEach { type ->
            assertTrue(isHabitComplete(habit(type, 0), log(1)))
            assertEquals(1f, habitProgress(habit(type, 0), log(1)), 0f)
        }
    }
    @Test fun partialAndExceededGoals() {
        assertEquals(0.5f, habitProgress(habit(HabitGoalType.Count, 10), log(5)), 0f)
        assertFalse(isHabitComplete(habit(HabitGoalType.Count, 10), log(5)))
        assertEquals(1f, habitProgress(habit(HabitGoalType.Time, 10), log(15)), 0f)
        assertEquals(0f, habitProgress(habit(HabitGoalType.Check, 0), null), 0f)
    }
}
