package com.dallet.app.ledger

import java.time.LocalDate

/** Something the user saves up for. The saved amount is the sum of its entries. */
data class Goal(
    val id: Long,
    val name: String,
    val targetMinor: Long,
    val createdAt: LocalDate,
    val archived: Boolean = false
)

/**
 * Money moved into a goal ([amountMinor] > 0) or back out of it (< 0) through [accountId].
 * A deposit lowers the account balance and a withdrawal raises it. [operationId] points at the
 * income operation created together with a deposit "as income".
 */
data class GoalEntry(
    val id: Long,
    val goalId: Long,
    val accountId: Long,
    val amountMinor: Long,
    val date: LocalDate,
    val operationId: Long? = null
)

data class GoalData(
    val goals: List<Goal>,
    val entries: List<GoalEntry>
) {
    companion object {
        val EMPTY = GoalData(emptyList(), emptyList())
    }
}

/** A goal with what has been saved so far. */
data class GoalSummary(
    val goal: Goal,
    val savedMinor: Long
) {
    val remainingMinor: Long get() = (goal.targetMinor - savedMinor).coerceAtLeast(0L)
    val reached: Boolean get() = savedMinor >= goal.targetMinor

    /** 0..100, capped; the integer part only. */
    val percent: Int
        get() = if (goal.targetMinor <= 0L) 0
        else (savedMinor.coerceAtLeast(0L) * 100L / goal.targetMinor).coerceAtMost(100L).toInt()
}

class GoalFundsException : Exception("Not enough saved in the goal")

interface GoalRepository {
    suspend fun getAll(): GoalData
    suspend fun replaceAll(data: GoalData)

    suspend fun goal(id: Long): Goal?
    suspend fun insertGoal(goal: Goal): Long
    suspend fun updateGoal(goal: Goal)

    /** Deletes the goal together with its entries. */
    suspend fun deleteGoal(id: Long)

    suspend fun insertEntry(entry: GoalEntry): Long
    suspend fun updateEntry(entry: GoalEntry)
    suspend fun deleteEntry(id: Long)
}

class InMemoryGoalRepository : GoalRepository {
    private var goals = mutableListOf<Goal>()
    private var entries = mutableListOf<GoalEntry>()
    private var nextGoalId = 1L
    private var nextEntryId = 1L

    override suspend fun getAll(): GoalData = GoalData(goals.toList(), entries.toList())

    override suspend fun replaceAll(data: GoalData) {
        goals = data.goals.toMutableList()
        entries = data.entries.toMutableList()
        nextGoalId = (goals.maxOfOrNull { it.id } ?: 0L) + 1L
        nextEntryId = (entries.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    override suspend fun goal(id: Long): Goal? = goals.firstOrNull { it.id == id }

    override suspend fun insertGoal(goal: Goal): Long {
        val id = if (goal.id == 0L) nextGoalId++ else goal.id
        goals.add(goal.copy(id = id))
        return id
    }

    override suspend fun updateGoal(goal: Goal) {
        val index = goals.indexOfFirst { it.id == goal.id }
        require(index >= 0) { "goal not found" }
        goals[index] = goal
    }

    override suspend fun deleteGoal(id: Long) {
        goals.removeAll { it.id == id }
        entries.removeAll { it.goalId == id }
    }

    override suspend fun insertEntry(entry: GoalEntry): Long {
        val id = if (entry.id == 0L) nextEntryId++ else entry.id
        entries.add(entry.copy(id = id))
        return id
    }

    override suspend fun updateEntry(entry: GoalEntry) {
        val index = entries.indexOfFirst { it.id == entry.id }
        require(index >= 0) { "entry not found" }
        entries[index] = entry
    }

    override suspend fun deleteEntry(id: Long) {
        entries.removeAll { it.id == id }
    }
}
