package com.daykit.feature.dayflow.data

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroProgressTest {
    private val today = LocalDate.of(2026, 9, 18)
    private val start = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 60 * 60_000L
    private val now = start + 20 * 60_000L

    private fun session(
        state: String,
        remaining: Long,
        kind: String = "work",
        started: Long = start,
        end: Long = now + remaining,
    ) = PomodoroSessionEntity(state + kind + started, kind, started, end, remaining, state)

    @Test fun includesCompletedAndPartialWorkButNotBreaksOrOtherDays() {
        val sessions = listOf(
            session("completed", 0),
            session("stopped", 15 * 60_000L),
            session("paused", 20 * 60_000L),
            session("running", 18 * 60_000L),
            session("completed", 0, kind = "break"),
            session("completed", 0, started = start - 24 * 60 * 60_000L),
        )

        assertEquals(47L, focusedMinutesOn(sessions, today, now, ZoneOffset.UTC))
    }

    @Test fun activeMinutesAreClampedToSessionDuration() {
        assertEquals(25L, focusedMinutesOn(
            listOf(session("running", 25 * 60_000L, end = now - 60_000L)),
            today, now, ZoneOffset.UTC,
        ))
    }
}
