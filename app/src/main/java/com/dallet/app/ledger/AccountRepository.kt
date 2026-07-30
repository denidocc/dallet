package com.dallet.app.ledger

interface AccountRepository {
    suspend fun getAll(): List<Account>
    suspend fun replaceAll(accounts: List<Account>)
}
