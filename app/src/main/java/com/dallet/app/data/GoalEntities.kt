package com.dallet.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val createdEpochDay: Long,
    val archived: Boolean
)

@Entity(tableName = "goal_entries")
data class GoalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val accountId: Long,
    val amountMinor: Long,
    val dateEpochDay: Long,
    val operationId: Long?
)
