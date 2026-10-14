package com.dallet.app.data

import com.dallet.app.ledger.Debt
import com.dallet.app.ledger.DebtData
import com.dallet.app.ledger.DebtDirection
import com.dallet.app.ledger.DebtEntry
import com.dallet.app.ledger.DebtEntryKind
import com.dallet.app.ledger.DebtRepository
import java.time.LocalDate

class RoomDebtRepository(
    private val dao: DebtDao
) : DebtRepository {
    override suspend fun getAll(): DebtData =
        DebtData(
            debts = dao.debts().map { it.toDomain() },
            entries = dao.entries().map { it.toDomain() }
        )

    override suspend fun replaceAll(data: DebtData) {
        dao.replaceAll(data.debts.map { it.toEntity() }, data.entries.map { it.toEntity() })
    }

    override suspend fun debt(id: Long): Debt? = dao.debt(id)?.toDomain()

    override suspend fun insertDebt(debt: Debt): Long = dao.insertDebt(debt.toEntity())

    override suspend fun updateDebt(debt: Debt) {
        dao.updateDebt(debt.toEntity())
    }

    override suspend fun deleteDebt(id: Long) {
        dao.deleteDebt(id)
    }

    override suspend fun insertEntry(entry: DebtEntry): Long = dao.insertEntry(entry.toEntity())

    override suspend fun updateEntry(entry: DebtEntry) {
        dao.updateEntry(entry.toEntity())
    }

    override suspend fun deleteEntry(id: Long) {
        dao.deleteEntry(id)
    }
}

private fun DebtEntity.toDomain() = Debt(
    id = id,
    person = person,
    direction = DebtDirection.valueOf(direction),
    note = note,
    createdAt = LocalDate.ofEpochDay(createdEpochDay)
)

private fun Debt.toEntity() = DebtEntity(
    id = id,
    person = person,
    direction = direction.name,
    note = note,
    createdEpochDay = createdAt.toEpochDay()
)

private fun DebtEntryEntity.toDomain() = DebtEntry(
    id = id,
    debtId = debtId,
    kind = DebtEntryKind.valueOf(kind),
    amountMinor = amountMinor,
    date = LocalDate.ofEpochDay(dateEpochDay),
    accountId = accountId,
    operationId = operationId
)

private fun DebtEntry.toEntity() = DebtEntryEntity(
    id = id,
    debtId = debtId,
    kind = kind.name,
    amountMinor = amountMinor,
    dateEpochDay = date.toEpochDay(),
    accountId = accountId,
    operationId = operationId
)
