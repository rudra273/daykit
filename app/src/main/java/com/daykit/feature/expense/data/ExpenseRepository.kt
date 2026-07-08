package com.rudra.daykit.feature.expense.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class ExpenseRepository(
    private val dao: ExpenseDao,
) {
    fun observeMonth(monthKey: String): Flow<ExpenseMonthSummary> {
        return combine(
            dao.observeEntriesForMonth(monthKey),
            dao.observeActiveBills(),
            dao.observeMonth(monthKey),
            dao.observeBillAmounts(),
        ) { entries, bills, month, amounts ->
            ExpenseMonthSummary(
                monthKey = monthKey,
                limitMinor = month?.limitMinor ?: 0L,
                entries = entries.map { it.toDomain() },
                monthlyBills = bills.map { it.toDomain(amountForMonth(it.billId, monthKey, bills, amounts)) },
            )
        }
    }

    fun observeBills(): Flow<List<MonthlyBill>> {
        return combine(
            dao.observeBills(),
            dao.observeBillAmounts(),
        ) { bills, amounts ->
            val currentMonth = YearMonth.now().toString()
            bills.map { bill ->
                bill.toDomain(amountForMonth(bill.billId, currentMonth, bills, amounts))
            }
        }
    }

    fun observeAllEntries(): Flow<List<ExpenseEntry>> {
        return dao.observeAllEntries().map { entries ->
            entries.map { it.toDomain() }
        }
    }

    suspend fun ensureMonth(monthKey: String) {
        val now = System.currentTimeMillis()
        if (dao.getMonth(monthKey) == null) {
            dao.upsertMonth(
                ExpenseMonthEntity(
                    monthKey = monthKey,
                    limitMinor = 0L,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
        }
        dao.getActiveBillsForMonth(monthKey).forEach { bill ->
            if (dao.getBillOccurrence(monthKey, bill.billId) == null) {
                dao.upsertEntry(bill.toOccurrence(monthKey, bill.amountForMonth(monthKey), now))
            }
        }
    }

    suspend fun setMonthlyLimit(monthKey: String, amountMinor: Long) {
        val now = System.currentTimeMillis()
        val existing = dao.getMonth(monthKey)
        dao.upsertMonth(
            ExpenseMonthEntity(
                monthKey = monthKey,
                limitMinor = amountMinor.coerceAtLeast(0L),
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now,
            ),
        )
    }

    suspend fun addDailyExpense(expenseDate: String, title: String, category: String, amountMinor: Long, note: String) {
        require(title.trim().isNotBlank()) { "Expense title cannot be empty" }
        require(amountMinor > 0L) { "Amount must be greater than zero" }
        val parsedDate = LocalDate.parse(expenseDate)
        val monthKey = YearMonth.from(parsedDate).toString()
        ensureMonth(monthKey)
        val now = System.currentTimeMillis()
        dao.upsertEntry(
            ExpenseEntryEntity(
                entryId = UUID.randomUUID().toString(),
                monthKey = monthKey,
                title = title.trim(),
                category = category.trim().ifBlank { "General" },
                amountMinor = amountMinor,
                kind = KIND_DAILY,
                sourceBillId = null,
                expenseDate = parsedDate.toString(),
                note = note.trim(),
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    suspend fun addMonthlyBill(
        title: String,
        category: String,
        amountMinor: Long,
        startMonthKey: String,
        endMonthKey: String?,
        dueDay: Int,
    ) {
        require(title.trim().isNotBlank()) { "Bill title cannot be empty" }
        require(amountMinor > 0L) { "Amount must be greater than zero" }
        val startMonth = YearMonth.parse(startMonthKey)
        val normalizedEnd = endMonthKey?.takeIf { it.isNotBlank() }
        normalizedEnd?.let { require(!YearMonth.parse(it).isBefore(startMonth)) { "End month cannot be before start month" } }
        require(dueDay in 1..31) { "Due day must be between 1 and 31" }
        val now = System.currentTimeMillis()
        val billId = UUID.randomUUID().toString()
        val bill = MonthlyBillEntity(
            billId = billId,
            title = title.trim(),
            category = category.trim().ifBlank { "Bills" },
            amountMinor = amountMinor,
            active = true,
            startMonthKey = startMonth.toString(),
            endMonthKey = normalizedEnd,
            dueDay = dueDay,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        dao.upsertBill(bill)
        dao.upsertBillAmount(
            MonthlyBillAmountEntity(
                changeId = UUID.randomUUID().toString(),
                billId = billId,
                effectiveMonthKey = startMonth.toString(),
                amountMinor = amountMinor,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
        ensureMonth(startMonth.toString())
    }

    suspend fun deleteEntry(entryId: String) {
        dao.deleteEntry(entryId)
    }

    suspend fun updateEntry(entryId: String, title: String, category: String, amountMinor: Long, note: String, expenseDate: String) {
        require(title.trim().isNotBlank()) { "Expense title cannot be empty" }
        require(amountMinor > 0L) { "Amount must be greater than zero" }
        val parsedDate = LocalDate.parse(expenseDate)
        val existing = dao.getEntry(entryId) ?: return
        ensureMonth(YearMonth.from(parsedDate).toString())
        dao.upsertEntry(
            existing.copy(
                monthKey = YearMonth.from(parsedDate).toString(),
                title = title.trim(),
                category = category.trim().ifBlank { "General" },
                amountMinor = amountMinor,
                expenseDate = parsedDate.toString(),
                note = note.trim(),
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun stopMonthlyBill(billId: String) {
        val bill = dao.getBill(billId) ?: return
        dao.upsertBill(bill.copy(active = false, updatedAtMillis = System.currentTimeMillis()))
        dao.deleteFutureBillOccurrences(billId, YearMonth.now().plusMonths(1).toString())
    }

    suspend fun updateMonthlyBill(
        billId: String,
        title: String,
        category: String,
        startMonthKey: String,
        endMonthKey: String?,
        dueDay: Int,
    ) {
        require(title.trim().isNotBlank()) { "Bill title cannot be empty" }
        val startMonth = YearMonth.parse(startMonthKey)
        val normalizedEnd = endMonthKey?.takeIf { it.isNotBlank() }
        normalizedEnd?.let { require(!YearMonth.parse(it).isBefore(startMonth)) { "End month cannot be before start month" } }
        require(dueDay in 1..31) { "Due day must be between 1 and 31" }
        val bill = dao.getBill(billId) ?: return
        dao.upsertBill(
            bill.copy(
                title = title.trim(),
                category = category.trim().ifBlank { "Bills" },
                startMonthKey = startMonth.toString(),
                endMonthKey = normalizedEnd,
                dueDay = dueDay,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        dao.deleteFutureBillOccurrences(billId, YearMonth.now().toString())
    }

    suspend fun updateMonthlyBillAmount(billId: String, effectiveMonthKey: String, amountMinor: Long) {
        require(amountMinor > 0L) { "Amount must be greater than zero" }
        YearMonth.parse(effectiveMonthKey)
        val now = System.currentTimeMillis()
        dao.upsertBillAmount(
            MonthlyBillAmountEntity(
                changeId = UUID.randomUUID().toString(),
                billId = billId,
                effectiveMonthKey = effectiveMonthKey,
                amountMinor = amountMinor,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
        dao.getBill(billId)?.let { bill ->
            if (effectiveMonthKey <= YearMonth.now().toString()) {
                dao.upsertBill(bill.copy(amountMinor = amountMinor, updatedAtMillis = now))
            }
        }
        dao.deleteFutureBillOccurrences(billId, effectiveMonthKey)
    }

    suspend fun exportRecords(): ExpenseBackupRecord {
        return ExpenseBackupRecord(
            entries = dao.getAllEntries().map { it.toDomain() },
            bills = dao.getAllBills().map { it.toDomain() },
            billAmounts = dao.getAllBillAmounts().map { it.toDomain() },
            months = dao.getAllMonths(),
        )
    }

    suspend fun importRecords(record: ExpenseBackupRecord) {
        record.months.forEach { dao.upsertMonth(it) }
        record.bills.forEach { bill ->
            dao.upsertBill(
                MonthlyBillEntity(
                    billId = bill.billId,
                    title = bill.title,
                    category = bill.category,
                    amountMinor = bill.amountMinor,
                    active = bill.active,
                    startMonthKey = bill.startMonthKey,
                    endMonthKey = bill.endMonthKey,
                    dueDay = bill.dueDay,
                    createdAtMillis = bill.createdAtMillis,
                    updatedAtMillis = bill.updatedAtMillis,
                ),
            )
        }
        record.billAmounts.forEach { change ->
            dao.upsertBillAmount(change.toEntity())
        }
        record.entries.forEach { entry ->
            dao.upsertEntry(entry.toEntity())
        }
    }

    private suspend fun MonthlyBillEntity.amountForMonth(monthKey: String): Long {
        return dao.getBillAmountForMonth(billId, monthKey)?.amountMinor ?: amountMinor
    }

    private fun amountForMonth(
        billId: String,
        monthKey: String,
        bills: List<MonthlyBillEntity>,
        amounts: List<MonthlyBillAmountEntity>,
    ): Long {
        return amounts
            .filter { it.billId == billId && it.effectiveMonthKey <= monthKey }
            .maxByOrNull { it.effectiveMonthKey }
            ?.amountMinor
            ?: bills.firstOrNull { it.billId == billId }?.amountMinor
            ?: 0L
    }

    private fun MonthlyBillEntity.toOccurrence(monthKey: String, amountMinor: Long, now: Long): ExpenseEntryEntity {
        val month = YearMonth.parse(monthKey)
        val clampedDay = dueDay.coerceIn(1, month.lengthOfMonth())
        return ExpenseEntryEntity(
            entryId = UUID.randomUUID().toString(),
            monthKey = monthKey,
            title = title,
            category = category,
            amountMinor = amountMinor,
            kind = KIND_MONTHLY_BILL,
            sourceBillId = billId,
            expenseDate = month.atDay(clampedDay).toString(),
            note = "Static monthly bill",
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    }

    private fun ExpenseEntryEntity.toDomain(): ExpenseEntry {
        return ExpenseEntry(
            entryId = entryId,
            monthKey = monthKey,
            title = title,
            category = category,
            amountMinor = amountMinor,
            kind = if (kind == KIND_MONTHLY_BILL) ExpenseEntryKind.MonthlyBill else ExpenseEntryKind.Daily,
            sourceBillId = sourceBillId,
            expenseDate = expenseDate.ifBlank { monthKey + "-01" },
            note = note,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun MonthlyBillEntity.toDomain(): MonthlyBill {
        return MonthlyBill(
            billId = billId,
            title = title,
            category = category,
            amountMinor = amountMinor,
            active = active,
            startMonthKey = startMonthKey.ifBlank { YearMonth.now().toString() },
            endMonthKey = endMonthKey?.takeIf { it.isNotBlank() },
            dueDay = dueDay.coerceIn(1, 31),
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun MonthlyBillEntity.toDomain(displayAmountMinor: Long): MonthlyBill {
        return toDomain().copy(amountMinor = displayAmountMinor)
    }

    private fun MonthlyBillAmountEntity.toDomain(): MonthlyBillAmountChange {
        return MonthlyBillAmountChange(
            changeId = changeId,
            billId = billId,
            effectiveMonthKey = effectiveMonthKey,
            amountMinor = amountMinor,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun MonthlyBillAmountChange.toEntity(): MonthlyBillAmountEntity {
        return MonthlyBillAmountEntity(
            changeId = changeId,
            billId = billId,
            effectiveMonthKey = effectiveMonthKey,
            amountMinor = amountMinor,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun ExpenseEntry.toEntity(): ExpenseEntryEntity {
        return ExpenseEntryEntity(
            entryId = entryId,
            monthKey = monthKey,
            title = title,
            category = category,
            amountMinor = amountMinor,
            kind = if (kind == ExpenseEntryKind.MonthlyBill) KIND_MONTHLY_BILL else KIND_DAILY,
            sourceBillId = sourceBillId,
            expenseDate = expenseDate.ifBlank { monthKey + "-01" },
            note = note,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private companion object {
        const val KIND_DAILY = "daily"
        const val KIND_MONTHLY_BILL = "monthly_bill"
    }
}

data class ExpenseBackupRecord(
    val entries: List<ExpenseEntry>,
    val bills: List<MonthlyBill>,
    val billAmounts: List<MonthlyBillAmountChange> = emptyList(),
    val months: List<ExpenseMonthEntity>,
)
