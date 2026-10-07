package com.dallet.app.ledger

/** Replaces all accounts, categories and operations as one unit. */
interface LedgerDataStore {
    suspend fun replaceAll(
        accounts: List<Account>,
        categories: List<Category>,
        operations: List<Operation>
    )
}

class RepositoryLedgerDataStore(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val operationRepository: OperationRepository
) : LedgerDataStore {
    override suspend fun replaceAll(
        accounts: List<Account>,
        categories: List<Category>,
        operations: List<Operation>
    ) {
        operationRepository.replaceAll(operations)
        accountRepository.replaceAll(accounts)
        categoryRepository.replaceAll(categories)
    }
}
