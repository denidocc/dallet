package com.dallet.app.ledger

/** Replaces all accounts, categories, operations and shopping data as one unit. */
interface LedgerDataStore {
    suspend fun replaceAll(
        accounts: List<Account>,
        categories: List<Category>,
        operations: List<Operation>,
        shopping: ShoppingData,
        goals: GoalData
    )
}

class RepositoryLedgerDataStore(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val operationRepository: OperationRepository,
    private val shoppingRepository: ShoppingRepository,
    private val goalRepository: GoalRepository
) : LedgerDataStore {
    override suspend fun replaceAll(
        accounts: List<Account>,
        categories: List<Category>,
        operations: List<Operation>,
        shopping: ShoppingData,
        goals: GoalData
    ) {
        operationRepository.replaceAll(operations)
        accountRepository.replaceAll(accounts)
        categoryRepository.replaceAll(categories)
        shoppingRepository.replaceAll(shopping)
        goalRepository.replaceAll(goals)
    }
}
