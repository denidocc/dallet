package com.dallet.app.data

import androidx.room.withTransaction
import com.dallet.app.ledger.Account
import com.dallet.app.ledger.AccountRepository
import com.dallet.app.ledger.Category
import com.dallet.app.ledger.CategoryRepository
import com.dallet.app.ledger.LedgerDataStore
import com.dallet.app.ledger.Operation
import com.dallet.app.ledger.OperationRepository

class RoomLedgerDataStore(
    private val database: DalletDatabase,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val operationRepository: OperationRepository
) : LedgerDataStore {
    override suspend fun replaceAll(
        accounts: List<Account>,
        categories: List<Category>,
        operations: List<Operation>
    ) {
        database.withTransaction {
            operationRepository.replaceAll(operations)
            accountRepository.replaceAll(accounts)
            categoryRepository.replaceAll(categories)
        }
    }
}
