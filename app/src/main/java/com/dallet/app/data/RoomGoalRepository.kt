package com.dallet.app.data

import com.dallet.app.ledger.Goal
import com.dallet.app.ledger.GoalData
import com.dallet.app.ledger.GoalEntry
import com.dallet.app.ledger.GoalRepository
import java.time.LocalDate

class RoomGoalRepository(
    private val dao: GoalDao
) : GoalRepository {
    override suspend fun getAll(): GoalData =
        GoalData(
            goals = dao.goals().map { it.toDomain() },
            entries = dao.entries().map { it.toDomain() }
        )

    override suspend fun replaceAll(data: GoalData) {
        dao.replaceAll(data.goals.map { it.toEntity() }, data.entries.map { it.toEntity() })
    }

    override suspend fun goal(id: Long): Goal? = dao.goal(id)?.toDomain()

    override suspend fun insertGoal(goal: Goal): Long = dao.insertGoal(goal.toEntity())

    override suspend fun updateGoal(goal: Goal) {
        dao.updateGoal(goal.toEntity())
    }

    override suspend fun deleteGoal(id: Long) {
        dao.deleteGoal(id)
    }

    override suspend fun insertEntry(entry: GoalEntry): Long = dao.insertEntry(entry.toEntity())

    override suspend fun updateEntry(entry: GoalEntry) {
        dao.updateEntry(entry.toEntity())
    }

    override suspend fun deleteEntry(id: Long) {
        dao.deleteEntry(id)
    }
}

private fun GoalEntity.toDomain() = Goal(
    id = id,
    name = name,
    targetMinor = targetMinor,
    createdAt = LocalDate.ofEpochDay(createdEpochDay),
    archived = archived
)

private fun Goal.toEntity() = GoalEntity(
    id = id,
    name = name,
    targetMinor = targetMinor,
    createdEpochDay = createdAt.toEpochDay(),
    archived = archived
)

private fun GoalEntryEntity.toDomain() = GoalEntry(
    id = id,
    goalId = goalId,
    accountId = accountId,
    amountMinor = amountMinor,
    date = LocalDate.ofEpochDay(dateEpochDay),
    operationId = operationId
)

private fun GoalEntry.toEntity() = GoalEntryEntity(
    id = id,
    goalId = goalId,
    accountId = accountId,
    amountMinor = amountMinor,
    dateEpochDay = date.toEpochDay(),
    operationId = operationId
)
