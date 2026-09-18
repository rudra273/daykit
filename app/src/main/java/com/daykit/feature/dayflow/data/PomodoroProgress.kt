package com.daykit.feature.dayflow.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val WORK_MILLIS = 25 * 60_000L

/** Focus time spent on work sessions started on [date], including an active session. */
fun focusedMinutesOn(
    sessions: List<PomodoroSessionEntity>,
    date: LocalDate,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long = sessions.asSequence()
    .filter { it.kind == "work" }
    .filter { Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate() == date }
    .sumOf { session ->
        val remaining = when (session.state) {
            "completed" -> 0L
            "running" -> (session.endAtMillis - nowMillis).coerceAtLeast(0)
            else -> session.remainingMillis
        }
        (WORK_MILLIS - remaining).coerceIn(0L, WORK_MILLIS)
    } / 60_000L
