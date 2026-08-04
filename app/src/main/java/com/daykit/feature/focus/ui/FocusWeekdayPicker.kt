package com.daykit.feature.focus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.feature.focus.data.FocusRecurrence
import java.time.DayOfWeek

/**
 * Monday-first weekday selector backed by a [FocusRecurrence] bitmask.
 *
 * Built from [FilterChipButton] because that is how every other multi-choice row
 * in the app is rendered — there is no segmented-control component, and chips
 * already carry the selected/unselected styling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusWeekdayPicker(
    daysMask: Int,
    onDaysMaskChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // DayOfWeek.entries is Monday-first already.
        DayOfWeek.entries.forEach { day ->
            val selected = FocusRecurrence.includes(daysMask, day)
            FilterChipButton(
                text = FocusRecurrence.shortLabel(day),
                selected = selected,
                onClick = {
                    val bit = 1 shl (day.value - 1)
                    onDaysMaskChange(if (selected) daysMask and bit.inv() else daysMask or bit)
                },
            )
        }
    }
}
