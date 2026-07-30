package com.dallet.app.ledger

class InMemoryOperationRepository : OperationRepository {
    private val operations = mutableListOf<Operation>()
    private var nextId = 1L

    override suspend fun getAll(): List<Operation> = operations.toList()

    override suspend fun insert(operation: Operation): Long {
        val id = if (operation.id == 0L) nextId++ else operation.id
        operations.add(operation.copy(id = id))
        return id
    }
}
