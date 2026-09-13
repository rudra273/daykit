package com.daykit.feature.reminder.data

import com.daykit.core.backup.BackupContributor
import com.daykit.core.backup.BackupToolKeys
import org.json.JSONArray
import org.json.JSONObject

class ReminderBackupContributor(
    private val repository: ReminderRepository,
    private val onImported: suspend () -> Unit = {},
) : BackupContributor {
    override val toolKey = BackupToolKeys.REMINDERS
    override val schemaVersion = 2

    override suspend fun exportJson(): JSONObject = JSONObject().put("reminders", JSONArray().also { rows ->
        repository.exportForBackup().forEach { r ->
            rows.put(JSONObject().put("reminderId", r.reminderId).put("title", r.title)
                .put("scheduledAtMillis", r.scheduledAtMillis).put("completed", r.completed)
                .put("acknowledgedAtMillis", r.acknowledgedAtMillis ?: JSONObject.NULL)
                .put("createdAtMillis", r.createdAtMillis).put("updatedAtMillis", r.updatedAtMillis)
                .put("recurrenceRule", r.recurrence?.encode() ?: JSONObject.NULL)
                .put("pendingOccurrenceMillis", r.pendingOccurrenceMillis ?: JSONObject.NULL)
                .put("paused", r.paused)
                .put("snoozedUntilMillis", r.snoozedUntilMillis ?: JSONObject.NULL))
        }
    })

    override suspend fun importJson(payload: JSONObject) {
        val rows = payload.getJSONArray("reminders")
        val reminders = (0 until rows.length()).map { index ->
            val r = rows.getJSONObject(index)
            Reminder(
                reminderId = r.getString("reminderId"), title = r.getString("title"),
                scheduledAtMillis = r.getLong("scheduledAtMillis"), completed = r.getBoolean("completed"),
                acknowledgedAtMillis = r.nullableLong("acknowledgedAtMillis"),
                createdAtMillis = r.getLong("createdAtMillis"), updatedAtMillis = r.getLong("updatedAtMillis"),
                recurrence = if (r.isNull("recurrenceRule")) null else ReminderRecurrence.decode(r.getString("recurrenceRule")),
                pendingOccurrenceMillis = r.nullableLong("pendingOccurrenceMillis"),
                paused = r.getBoolean("paused"), snoozedUntilMillis = r.nullableLong("snoozedUntilMillis"),
            )
        }
        repository.importFromBackup(reminders)
        onImported()
    }

    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)
}
