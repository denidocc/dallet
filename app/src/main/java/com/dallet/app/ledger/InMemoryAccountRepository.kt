package com.dallet.app.ledger

class InMemoryAccountRepository : AccountRepository {
    private val accounts = mutableListOf<Account>()

    override suspend fun getAll(): List<Account> = accounts.toList()

    override suspend fun replaceAll(accounts: List<Account>) {
        this.accounts.clear()
        this.accounts.addAll(accounts)
    }
}
