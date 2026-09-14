package com.daykit.feature.widget

import com.daykit.feature.habit.data.Habit
import com.daykit.feature.habit.data.HabitLog
import com.daykit.feature.habit.data.HabitGoalType

internal fun habitProgress(habit: Habit, log: HabitLog?): Float {
    if (log == null) return 0f
    return when (habit.goalType) {
        HabitGoalType.Time -> if (habit.targetMinutes <= 0) {
            if (log.minutes > 0 || log.completed) 1f else 0f
        } else {
            log.minutes.toFloat() / habit.targetMinutes
        }
        HabitGoalType.Count -> if (habit.targetCount <= 0) {
            if (log.progressCount > 0 || log.completed) 1f else 0f
        } else {
            log.progressCount.toFloat() / habit.targetCount
        }
        HabitGoalType.Check -> if (log.completed) 1f else 0f
    }.coerceIn(0f, 1f)
}

internal fun isHabitComplete(habit: Habit, log: HabitLog?): Boolean {
    if (log == null) return false
    return when (habit.goalType) {
        HabitGoalType.Time -> if (habit.targetMinutes <= 0) log.minutes > 0 || log.completed else log.minutes >= habit.targetMinutes
        HabitGoalType.Count -> if (habit.targetCount <= 0) log.progressCount > 0 || log.completed else log.progressCount >= habit.targetCount
        HabitGoalType.Check -> log.completed
    }
}
