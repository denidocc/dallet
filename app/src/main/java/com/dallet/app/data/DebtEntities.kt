package com.dallet.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val direction: String,
    val note: String,
    val createdEpochDay: Long
)

@Entity(tableName = "debt_entries")
data class DebtEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val kind: String,
    val amountMinor: Long,
    val dateEpochDay: Long,
    val accountId: Long?,
    val operationId: Long?
)
