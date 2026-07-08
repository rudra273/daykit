package com.rudra.daykit.feature.habit.data

import com.rudra.daykit.core.backup.BackupContributor
import org.json.JSONArray
import org.json.JSONObject

class HabitBackupContributor(
    private val repository: HabitRepository,
) : BackupContributor {
    override val toolKey: String = "habits"
    override val schemaVersion: Int = 1

    override suspend fun exportJson(): JSONObject {
        val record = repository.exportRecords()
        return JSONObject()
            .put("habits", JSONArray().also { rows ->
                record.habits.forEach { habit ->
                    rows.put(
                        JSONObject()
                            .put("habitId", habit.habitId)
                            .put("name", habit.name)
                            .put("kind", habit.kind.name)
                            .put("goalType", habit.goalType.name)
                            .put("targetMinutes", habit.targetMinutes)
                            .put("targetCount", habit.targetCount)
                            .put("unitLabel", habit.unitLabel)
                            .put("colorIndex", habit.colorIndex)
                            .put("reminderEnabled", habit.reminderEnabled)
                            .put("reminderHour", habit.reminderHour)
                            .put("reminderMinute", habit.reminderMinute)
                            .put("active", habit.active)
                            .put("createdAtMillis", habit.createdAtMillis)
                            .put("updatedAtMillis", habit.updatedAtMillis),
                    )
                }
            })
            .put("logs", JSONArray().also { rows ->
                record.logs.forEach { log ->
                    rows.put(
                        JSONObject()
                            .put("logId", log.logId)
                            .put("habitId", log.habitId)
                            .put("date", log.date)
                            .put("minutes", log.minutes)
                            .put("progressCount", log.progressCount)
                            .put("completed", log.completed)
                            .put("relapse", log.relapse)
                            .put("note", log.note)
                            .put("createdAtMillis", log.createdAtMillis)
                            .put("updatedAtMillis", log.updatedAtMillis),
                    )
                }
            })
    }

    override suspend fun importJson(payload: JSONObject) {
        val habits = payload.getJSONArray("habits")
        val logs = payload.getJSONArray("logs")
        repository.importRecords(
            HabitBackupRecord(
                habits = buildList {
                    for (index in 0 until habits.length()) {
                        val habit = habits.getJSONObject(index)
                        add(
                            Habit(
                                habitId = habit.getString("habitId"),
                                name = habit.getString("name"),
                                kind = HabitKind.valueOf(habit.getString("kind")),
                                goalType = HabitGoalType.valueOf(habit.getString("goalType")),
                                targetMinutes = habit.optInt("targetMinutes", 0),
                                targetCount = habit.optInt("targetCount", 0),
                                unitLabel = habit.optString("unitLabel", "times"),
                                colorIndex = habit.optInt("colorIndex", 0),
                                reminderEnabled = habit.optBoolean("reminderEnabled", false),
                                reminderHour = habit.optInt("reminderHour", 20),
                                reminderMinute = habit.optInt("reminderMinute", 0),
                                active = habit.optBoolean("active", true),
                                createdAtMillis = habit.getLong("createdAtMillis"),
                                updatedAtMillis = habit.getLong("updatedAtMillis"),
                            ),
                        )
                    }
                },
                logs = buildList {
                    for (index in 0 until logs.length()) {
                        val log = logs.getJSONObject(index)
                        add(
                            HabitLog(
                                logId = log.getString("logId"),
                                habitId = log.getString("habitId"),
                                date = log.getString("date"),
                                minutes = log.optInt("minutes", 0),
                                progressCount = log.optInt("progressCount", 0),
                                completed = log.optBoolean("completed", false),
                                relapse = log.optBoolean("relapse", false),
                                note = log.optString("note", ""),
                                createdAtMillis = log.getLong("createdAtMillis"),
                                updatedAtMillis = log.getLong("updatedAtMillis"),
                            ),
                        )
                    }
                },
            ),
        )
    }
}
