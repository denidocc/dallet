package com.dallet.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY id ASC")
    suspend fun goals(): List<GoalEntity>

    @Query("SELECT * FROM goal_entries ORDER BY id ASC")
    suspend fun entries(): List<GoalEntryEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun goal(id: Long): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoalRow(id: Long)

    @Query("DELETE FROM goal_entries WHERE goalId = :goalId")
    suspend fun deleteEntriesOfGoal(goalId: Long)

    @Transaction
    suspend fun deleteGoal(id: Long) {
        deleteEntriesOfGoal(id)
        deleteGoalRow(id)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: GoalEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: GoalEntryEntity)

    @Query("DELETE FROM goal_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()

    @Query("DELETE FROM goal_entries")
    suspend fun deleteAllEntries()

    @Insert
    suspend fun insertGoals(goals: List<GoalEntity>)

    @Insert
    suspend fun insertEntries(entries: List<GoalEntryEntity>)

    @Transaction
    suspend fun replaceAll(goals: List<GoalEntity>, entries: List<GoalEntryEntity>) {
        deleteAllEntries()
        deleteAllGoals()
        insertGoals(goals)
        insertEntries(entries)
    }
}
