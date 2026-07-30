package com.dallet.app.ledger

interface AccountRepository {
    suspend fun getAll(): List<Account>
    suspend fun replaceAll(accounts: List<Account>)
    suspend fun insert(account: Account): Long
    suspend fun update(account: Account)
    suspend fun delete(accountId: Long)
}
