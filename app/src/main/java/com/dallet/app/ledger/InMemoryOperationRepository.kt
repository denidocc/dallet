package com.dallet.app.ledger

class InMemoryOperationRepository : OperationRepository {
    private val operations = mutableListOf<Operation>()
    private var nextId = 1L

    override suspend fun getAll(): List<Operation> = operations.toList()

    override suspend fun replaceAll(operations: List<Operation>) {
        this.operations.clear()
        this.operations.addAll(operations)
        nextId = (operations.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    override suspend fun insert(operation: Operation): Long {
        val id = if (operation.id == 0L) nextId++ else operation.id
        operations.add(operation.copy(id = id))
        return id
    }

    override suspend fun update(operation: Operation) {
        val index = operations.indexOfFirst { it.id == operation.id }
        require(index >= 0) { "operation not found" }
        operations[index] = operation
    }

    override suspend fun delete(operationId: Long) {
        operations.removeAll { it.id == operationId }
    }
}
