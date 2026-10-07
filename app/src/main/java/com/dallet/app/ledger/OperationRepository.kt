package com.dallet.app.ledger

interface OperationRepository {
    suspend fun getAll(): List<Operation>
    suspend fun replaceAll(operations: List<Operation>)
    suspend fun insert(operation: Operation): Long
    suspend fun update(operation: Operation)
    suspend fun delete(operationId: Long)
}
