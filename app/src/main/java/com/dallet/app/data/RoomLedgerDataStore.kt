package com.dallet.app.data

import androidx.room.withTransaction
import com.dallet.app.ledger.Account
import com.dallet.app.ledger.AccountRepository
import com.dallet.app.ledger.Category
import com.dallet.app.ledger.CategoryRepository
import com.dallet.app.ledger.DebtData
import com.dallet.app.ledger.DebtRepository
import com.dallet.app.ledger.GoalData
import com.dallet.app.ledger.GoalRepository
import com.dallet.app.ledger.LedgerDataStore
import com.dallet.app.ledger.Operation
import com.dallet.app.ledger.OperationRepository
import com.dallet.app.ledger.ShoppingData
import com.dallet.app.ledger.ShoppingRepository

class RoomLedgerDataStore(
    private val database: DalletDatabase,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val operationRepository: OperationRepository,
    private val shoppingRepository: ShoppingRepository,
    private val goalRepository: GoalRepository,
    private val debtRepository: DebtRepository
) : LedgerDataStore {
    override suspend fun replaceAll(
        accounts: List<Account>,
        categories: List<Category>,
        operations: List<Operation>,
        shopping: ShoppingData,
        goals: GoalData,
        debts: DebtData
    ) {
        database.withTransaction {
            operationRepository.replaceAll(operations)
            accountRepository.replaceAll(accounts)
            categoryRepository.replaceAll(categories)
            shoppingRepository.replaceAll(shopping)
            goalRepository.replaceAll(goals)
            debtRepository.replaceAll(debts)
        }
    }
}
