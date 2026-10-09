package com.dallet.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operations")
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val amountMinor: Long,
    val dateEpochDay: Long,
    val comment: String?,
    val accountId: Long?,
    val categoryId: Long?,
    val fromAccountId: Long?,
    val toAccountId: Long?,
    val timeSecondOfDay: Int? = null
)
