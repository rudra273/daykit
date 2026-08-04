package com.daykit.feature.focus.data

/** A named set of apps blocked together. */
data class FocusGroup(
    val groupId: String,
    val name: String,
    val colorIndex: Int,
    val packageNames: List<String>,
)

/** A recurring window during which a [FocusGroup] is blocked. */
data class FocusSchedule(
    val scheduleId: String,
    val groupId: String,
    val label: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysMask: Int,
    val strict: Boolean,
    val enabled: Boolean,
)

internal fun FocusGroupEntity.toGroup(): FocusGroup = FocusGroup(
    groupId = groupId,
    name = name,
    colorIndex = colorIndex,
    // A blank stored value would otherwise decode to a single empty package name.
    packageNames = packageNames.split('\n').filter { it.isNotBlank() },
)

internal fun FocusScheduleEntity.toSchedule(): FocusSchedule = FocusSchedule(
    scheduleId = scheduleId,
    groupId = groupId,
    label = label,
    startHour = startHour,
    startMinute = startMinute,
    endHour = endHour,
    endMinute = endMinute,
    daysMask = daysMask,
    strict = strict,
    enabled = enabled,
)
