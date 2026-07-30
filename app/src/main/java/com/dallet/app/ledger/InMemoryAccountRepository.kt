package com.dallet.app.ledger

class InMemoryAccountRepository : AccountRepository {
    private val accounts = mutableListOf<Account>()
    private var nextId = 1L

    override suspend fun getAll(): List<Account> = accounts.toList()

    override suspend fun replaceAll(accounts: List<Account>) {
        this.accounts.clear()
        this.accounts.addAll(accounts)
        nextId = (accounts.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    override suspend fun insert(account: Account): Long {
        val id = if (account.id == 0L) nextId++ else account.id
        accounts.add(account.copy(id = id))
        return id
    }

    override suspend fun update(account: Account) {
        val index = accounts.indexOfFirst { it.id == account.id }
        require(index >= 0) { "account not found" }
        accounts[index] = account
    }

    override suspend fun delete(accountId: Long) {
        accounts.removeAll { it.id == accountId }
    }
}
