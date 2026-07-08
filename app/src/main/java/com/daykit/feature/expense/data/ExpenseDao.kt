package com.daykit.feature.expense.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense_entries WHERE monthKey = :monthKey ORDER BY expenseDate DESC, createdAtMillis DESC")
    fun observeEntriesForMonth(monthKey: String): Flow<List<ExpenseEntryEntity>>

    @Query("SELECT * FROM monthly_bills WHERE active = 1 ORDER BY title COLLATE NOCASE")
    fun observeActiveBills(): Flow<List<MonthlyBillEntity>>

    @Query("SELECT * FROM monthly_bills ORDER BY active DESC, title COLLATE NOCASE")
    fun observeBills(): Flow<List<MonthlyBillEntity>>

    @Query("SELECT * FROM expense_entries ORDER BY expenseDate ASC, createdAtMillis ASC")
    fun observeAllEntries(): Flow<List<ExpenseEntryEntity>>

    @Query("SELECT * FROM expense_months WHERE monthKey = :monthKey LIMIT 1")
    fun observeMonth(monthKey: String): Flow<ExpenseMonthEntity?>

    @Query("SELECT * FROM expense_months WHERE monthKey = :monthKey LIMIT 1")
    suspend fun getMonth(monthKey: String): ExpenseMonthEntity?

    @Query("SELECT * FROM monthly_bills WHERE active = 1 ORDER BY title COLLATE NOCASE")
    suspend fun getActiveBills(): List<MonthlyBillEntity>

    @Query(
        """
        SELECT * FROM monthly_bills
        WHERE active = 1
        AND startMonthKey <= :monthKey
        AND (endMonthKey IS NULL OR endMonthKey = '' OR endMonthKey >= :monthKey)
        ORDER BY title COLLATE NOCASE
        """,
    )
    suspend fun getActiveBillsForMonth(monthKey: String): List<MonthlyBillEntity>

    @Query(
        """
        SELECT * FROM monthly_bill_amounts
        WHERE billId = :billId AND effectiveMonthKey <= :monthKey
        ORDER BY effectiveMonthKey DESC
        LIMIT 1
        """,
    )
    suspend fun getBillAmountForMonth(billId: String, monthKey: String): MonthlyBillAmountEntity?

    @Query("SELECT * FROM monthly_bill_amounts ORDER BY effectiveMonthKey ASC")
    suspend fun getAllBillAmounts(): List<MonthlyBillAmountEntity>

    @Query("SELECT * FROM monthly_bill_amounts ORDER BY effectiveMonthKey ASC")
    fun observeBillAmounts(): Flow<List<MonthlyBillAmountEntity>>

    @Query("SELECT * FROM expense_entries WHERE monthKey = :monthKey AND sourceBillId = :billId LIMIT 1")
    suspend fun getBillOccurrence(monthKey: String, billId: String): ExpenseEntryEntity?

    @Query("SELECT * FROM expense_entries WHERE entryId = :entryId LIMIT 1")
    suspend fun getEntry(entryId: String): ExpenseEntryEntity?

    @Query("SELECT * FROM monthly_bills")
    suspend fun getAllBills(): List<MonthlyBillEntity>

    @Query("SELECT * FROM expense_entries")
    suspend fun getAllEntries(): List<ExpenseEntryEntity>

    @Query("SELECT * FROM expense_months")
    suspend fun getAllMonths(): List<ExpenseMonthEntity>

    @Upsert
    suspend fun upsertMonth(entity: ExpenseMonthEntity)

    @Upsert
    suspend fun upsertEntry(entity: ExpenseEntryEntity)

    @Upsert
    suspend fun upsertBill(entity: MonthlyBillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBillAmount(entity: MonthlyBillAmountEntity)

    @Query("DELETE FROM expense_entries WHERE entryId = :entryId")
    suspend fun deleteEntry(entryId: String)

    @Query("SELECT * FROM monthly_bills WHERE billId = :billId LIMIT 1")
    suspend fun getBill(billId: String): MonthlyBillEntity?

    @Query("DELETE FROM expense_entries WHERE sourceBillId = :billId AND monthKey >= :fromMonthKey AND kind = 'monthly_bill'")
    suspend fun deleteFutureBillOccurrences(billId: String, fromMonthKey: String)
}
