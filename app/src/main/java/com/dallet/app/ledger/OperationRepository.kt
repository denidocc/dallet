package com.dallet.app.ledger

interface OperationRepository {
    suspend fun getAll(): List<Operation>
    suspend fun insert(operation: Operation): Long
}
