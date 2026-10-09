package com.daykit.feature.habit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.util.TimeFormat
import com.daykit.feature.habit.data.Habit

@Composable
internal fun HabitReminderTime(habit: Habit) {
    if (!habit.reminderEnabled) return
    val time = TimeFormat.format(habit.reminderHour, habit.reminderMinute)
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Reminder at $time"
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.NotificationsNone,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textMuted)
    }
}
