package com.dallet.app.data

import com.dallet.app.ledger.Account
import com.dallet.app.ledger.AccountRepository

class RoomAccountRepository(
    private val dao: AccountDao
) : AccountRepository {
    override suspend fun getAll(): List<Account> =
        dao.getAll().map { it.toDomain() }

    override suspend fun replaceAll(accounts: List<Account>) {
        dao.replaceAll(accounts.map { it.toEntity() })
    }
}

private fun AccountEntity.toDomain(): Account =
    Account(id = id, name = name, openingBalanceMinor = openingBalanceMinor)

private fun Account.toEntity(): AccountEntity =
    AccountEntity(id = id, name = name, openingBalanceMinor = openingBalanceMinor)
