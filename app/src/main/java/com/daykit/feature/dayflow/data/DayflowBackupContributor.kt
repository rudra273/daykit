package com.daykit.feature.dayflow.data

import com.daykit.core.backup.BackupContributor
import com.daykit.core.backup.BackupToolKeys
import org.json.JSONArray
import org.json.JSONObject

class DayflowBackupContributor(
    private val repository: DayflowRepository,
    private val onImported: () -> Unit = {},
) : BackupContributor {
    override val toolKey = BackupToolKeys.DAYFLOW
    override val schemaVersion = 1

    override suspend fun exportJson(): JSONObject = JSONObject()
        .put("days", JSONArray().also { rows ->
            repository.exportDays().forEach { day ->
                rows.put(JSONObject().put("date", day.date).put("journal", day.journal)
                    .put("journalTitle", day.journalTitle)
                    .put("mood", day.mood).put("updatedAtMillis", day.updatedAtMillis))
            }
        })
        .put("sessions", JSONArray().also { rows ->
            repository.exportSessions().forEach { session ->
                rows.put(JSONObject().put("id", session.id).put("kind", session.kind)
                    .put("startedAtMillis", session.startedAtMillis)
                    .put("endAtMillis", session.endAtMillis)
                    .put("remainingMillis", session.remainingMillis)
                    .put("state", session.state)
                    .put("finishedAtMillis", session.finishedAtMillis))
            }
        })

    override suspend fun importJson(payload: JSONObject) {
        val days = payload.getJSONArray("days")
        for (i in 0 until days.length()) {
            val row = days.getJSONObject(i)
            repository.importDay(DayflowDayEntity(row.getString("date"), row.optString("journal"),
                row.optString("mood"), row.optLong("updatedAtMillis"),
                row.optString("journalTitle")))
        }
        val sessions = payload.getJSONArray("sessions")
        for (i in 0 until sessions.length()) {
            val row = sessions.getJSONObject(i)
            repository.importSession(PomodoroSessionEntity(row.getString("id"), row.getString("kind"),
                row.getLong("startedAtMillis"), row.getLong("endAtMillis"),
                row.getLong("remainingMillis"), row.getString("state"),
                if (row.isNull("finishedAtMillis")) null else row.getLong("finishedAtMillis")))
        }
        onImported()
    }
}
