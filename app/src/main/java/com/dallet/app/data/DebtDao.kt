package com.dallet.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY id ASC")
    suspend fun debts(): List<DebtEntity>

    @Query("SELECT * FROM debt_entries ORDER BY id ASC")
    suspend fun entries(): List<DebtEntryEntity>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun debt(id: Long): DebtEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntity): Long

    @Update
    suspend fun updateDebt(debt: DebtEntity)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebtRow(id: Long)

    @Query("DELETE FROM debt_entries WHERE debtId = :debtId")
    suspend fun deleteEntriesOfDebt(debtId: Long)

    @Transaction
    suspend fun deleteDebt(id: Long) {
        deleteEntriesOfDebt(id)
        deleteDebtRow(id)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DebtEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: DebtEntryEntity)

    @Query("DELETE FROM debt_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM debts")
    suspend fun deleteAllDebts()

    @Query("DELETE FROM debt_entries")
    suspend fun deleteAllEntries()

    @Insert
    suspend fun insertDebts(debts: List<DebtEntity>)

    @Insert
    suspend fun insertEntries(entries: List<DebtEntryEntity>)

    @Transaction
    suspend fun replaceAll(debts: List<DebtEntity>, entries: List<DebtEntryEntity>) {
        deleteAllEntries()
        deleteAllDebts()
        insertDebts(debts)
        insertEntries(entries)
    }
}
