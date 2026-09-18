package com.daykit.feature.dayflow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

class DayflowRepository(private val dao: DayflowDao) {
    private val mutex = Mutex()

    fun observeDay(date: LocalDate): Flow<DayflowDayEntity?> = dao.observeDay(date.toString())
    fun observeMoodHistory(): Flow<List<DayflowDayEntity>> = dao.observeMoodHistory()
    fun observeSessions(): Flow<List<PomodoroSessionEntity>> = dao.observeSessions()

    suspend fun saveJournal(date: LocalDate, text: String) = mutex.withLock {
        val key = date.toString()
        dao.upsertDay((dao.getDay(key) ?: DayflowDayEntity(key)).copy(
            journal = text, updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun saveMood(date: LocalDate, emoji: String) = mutex.withLock {
        val key = date.toString()
        dao.upsertDay((dao.getDay(key) ?: DayflowDayEntity(key)).copy(
            mood = emoji, updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun currentSession(): PomodoroSessionEntity? = mutex.withLock {
        normalize(dao.getActiveSession())
    }

    suspend fun start(kind: String): PomodoroSessionEntity = mutex.withLock {
        val active = normalize(dao.getActiveSession())
        require(active == null) { "Finish the current Pomodoro first" }
        val now = System.currentTimeMillis()
        val duration = if (kind == "break") 5 * 60_000L else 25 * 60_000L
        val session = PomodoroSessionEntity(UUID.randomUUID().toString(), kind, now, now + duration,
            duration, "running")
        dao.upsertSession(session)
        session
    }

    suspend fun pause(): PomodoroSessionEntity? = mutex.withLock {
        val current = normalize(dao.getActiveSession()) ?: return@withLock null
        if (current.state != "running") return@withLock current
        val updated = current.copy(state = "paused", remainingMillis =
            (current.endAtMillis - System.currentTimeMillis()).coerceAtLeast(0))
        dao.upsertSession(updated)
        updated
    }

    suspend fun resume(): PomodoroSessionEntity? = mutex.withLock {
        val current = normalize(dao.getActiveSession()) ?: return@withLock null
        if (current.state != "paused") return@withLock current
        val updated = current.copy(state = "running", endAtMillis = System.currentTimeMillis() + current.remainingMillis)
        dao.upsertSession(updated)
        updated
    }

    suspend fun stop(): PomodoroSessionEntity? = mutex.withLock {
        val current = normalize(dao.getActiveSession()) ?: return@withLock null
        val updated = current.copy(state = "stopped", finishedAtMillis = System.currentTimeMillis())
        dao.upsertSession(updated)
        updated
    }

    private suspend fun normalize(session: PomodoroSessionEntity?): PomodoroSessionEntity? {
        if (session == null) return null
        if (session.state == "running" && System.currentTimeMillis() >= session.endAtMillis) {
            dao.upsertSession(session.copy(state = "completed", remainingMillis = 0,
                finishedAtMillis = session.endAtMillis))
            return null
        }
        return session
    }

    suspend fun exportDays() = dao.getDays()
    suspend fun exportSessions() = dao.getSessions()
    suspend fun importDay(day: DayflowDayEntity) = dao.upsertDay(day)
    suspend fun importSession(session: PomodoroSessionEntity) = dao.upsertSession(session)
}
