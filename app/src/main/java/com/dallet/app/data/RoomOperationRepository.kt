package com.dallet.app.data

import com.dallet.app.ledger.Operation
import com.dallet.app.ledger.OperationKind
import com.dallet.app.ledger.OperationRepository
import java.time.LocalDate

class RoomOperationRepository(
    private val dao: OperationDao
) : OperationRepository {
    override suspend fun getAll(): List<Operation> =
        dao.getAll().map { it.toDomain() }

    override suspend fun insert(operation: Operation): Long =
        dao.insert(operation.toEntity())
}

private fun OperationEntity.toDomain(): Operation =
    Operation(
        id = id,
        kind = OperationKind.valueOf(kind),
        amountMinor = amountMinor,
        date = LocalDate.ofEpochDay(dateEpochDay),
        comment = comment,
        accountId = accountId,
        categoryId = categoryId,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId
    )

private fun Operation.toEntity(): OperationEntity =
    OperationEntity(
        id = if (id == 0L) 0 else id,
        kind = kind.name,
        amountMinor = amountMinor,
        dateEpochDay = date.toEpochDay(),
        comment = comment,
        accountId = accountId,
        categoryId = categoryId,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId
    )
