package com.daykit.feature.expense.data

import com.daykit.core.backup.BackupContributor
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

class ExpenseBackupContributor(
    private val repository: ExpenseRepository,
) : BackupContributor {
    override val toolKey: String = "expenses"
    override val schemaVersion: Int = 1

    override suspend fun exportJson(): JSONObject {
        val record = repository.exportRecords()
        return JSONObject()
            .put("entries", JSONArray().also { rows ->
                record.entries.forEach { entry ->
                    rows.put(
                        JSONObject()
                            .put("entryId", entry.entryId)
                            .put("monthKey", entry.monthKey)
                            .put("title", entry.title)
                            .put("category", entry.category)
                            .put("amountMinor", entry.amountMinor)
                            .put("kind", entry.kind.name)
                            .put("sourceBillId", entry.sourceBillId)
                            .put("expenseDate", entry.expenseDate)
                            .put("note", entry.note)
                            .put("createdAtMillis", entry.createdAtMillis)
                            .put("updatedAtMillis", entry.updatedAtMillis),
                    )
                }
            })
            .put("bills", JSONArray().also { rows ->
                record.bills.forEach { bill ->
                    rows.put(
                        JSONObject()
                            .put("billId", bill.billId)
                            .put("title", bill.title)
                            .put("category", bill.category)
                            .put("amountMinor", bill.amountMinor)
                            .put("active", bill.active)
                            .put("startMonthKey", bill.startMonthKey)
                            .put("endMonthKey", bill.endMonthKey)
                            .put("dueDay", bill.dueDay)
                            .put("createdAtMillis", bill.createdAtMillis)
                            .put("updatedAtMillis", bill.updatedAtMillis),
                    )
                }
            })
            .put("billAmounts", JSONArray().also { rows ->
                record.billAmounts.forEach { change ->
                    rows.put(
                        JSONObject()
                            .put("changeId", change.changeId)
                            .put("billId", change.billId)
                            .put("effectiveMonthKey", change.effectiveMonthKey)
                            .put("amountMinor", change.amountMinor)
                            .put("createdAtMillis", change.createdAtMillis)
                            .put("updatedAtMillis", change.updatedAtMillis),
                    )
                }
            })
            .put("months", JSONArray().also { rows ->
                record.months.forEach { month ->
                    rows.put(
                        JSONObject()
                            .put("monthKey", month.monthKey)
                            .put("limitMinor", month.limitMinor)
                            .put("createdAtMillis", month.createdAtMillis)
                            .put("updatedAtMillis", month.updatedAtMillis),
                    )
                }
            })
    }

    override suspend fun importJson(payload: JSONObject) {
        val entries = payload.getJSONArray("entries")
        val bills = payload.getJSONArray("bills")
        val billAmounts = payload.optJSONArray("billAmounts")
        val months = payload.getJSONArray("months")
        repository.importRecords(
            ExpenseBackupRecord(
                entries = buildList {
                    for (index in 0 until entries.length()) {
                        val entry = entries.getJSONObject(index)
                        add(
                            ExpenseEntry(
                                entryId = entry.getString("entryId"),
                                monthKey = entry.getString("monthKey"),
                                title = entry.getString("title"),
                                category = entry.getString("category"),
                                amountMinor = entry.getLong("amountMinor"),
                                kind = ExpenseEntryKind.valueOf(entry.getString("kind")),
                                sourceBillId = if (entry.isNull("sourceBillId")) {
                                    null
                                } else {
                                    entry.optString("sourceBillId").takeIf { it.isNotBlank() }
                                },
                                expenseDate = entry.optString("expenseDate")
                                    .ifBlank { dateFromMillis(entry.getLong("createdAtMillis")) },
                                note = entry.getString("note"),
                                createdAtMillis = entry.getLong("createdAtMillis"),
                                updatedAtMillis = entry.getLong("updatedAtMillis"),
                            ),
                        )
                    }
                },
                bills = buildList {
                    for (index in 0 until bills.length()) {
                        val bill = bills.getJSONObject(index)
                        add(
                            MonthlyBill(
                                billId = bill.getString("billId"),
                                title = bill.getString("title"),
                                category = bill.getString("category"),
                                amountMinor = bill.getLong("amountMinor"),
                                active = bill.getBoolean("active"),
                                startMonthKey = bill.optString("startMonthKey")
                                    .ifBlank { dateFromMillis(bill.getLong("createdAtMillis")).take(7) },
                                endMonthKey = if (bill.isNull("endMonthKey")) {
                                    null
                                } else {
                                    bill.optString("endMonthKey").takeIf { it.isNotBlank() }
                                },
                                dueDay = bill.optInt("dueDay", 1).coerceIn(1, 31),
                                createdAtMillis = bill.getLong("createdAtMillis"),
                                updatedAtMillis = bill.getLong("updatedAtMillis"),
                            ),
                        )
                    }
                },
                billAmounts = buildList {
                    if (billAmounts != null) {
                        for (index in 0 until billAmounts.length()) {
                            val change = billAmounts.getJSONObject(index)
                            add(
                                MonthlyBillAmountChange(
                                    changeId = change.getString("changeId"),
                                    billId = change.getString("billId"),
                                    effectiveMonthKey = change.getString("effectiveMonthKey"),
                                    amountMinor = change.getLong("amountMinor"),
                                    createdAtMillis = change.getLong("createdAtMillis"),
                                    updatedAtMillis = change.getLong("updatedAtMillis"),
                                ),
                            )
                        }
                    }
                },
                months = buildList {
                    for (index in 0 until months.length()) {
                        val month = months.getJSONObject(index)
                        add(
                            ExpenseMonthEntity(
                                monthKey = month.getString("monthKey"),
                                limitMinor = month.getLong("limitMinor"),
                                createdAtMillis = month.getLong("createdAtMillis"),
                                updatedAtMillis = month.getLong("updatedAtMillis"),
                            ),
                        )
                    }
                },
            ),
        )
    }
}

private fun dateFromMillis(millis: Long): String {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
