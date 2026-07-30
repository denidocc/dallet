package com.dallet.app.ledger

import java.time.LocalDate

class Ledger(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val operationRepository: OperationRepository
) {
    suspend fun hasCompletedOnboarding(): Boolean = accountRepository.getAll().isNotEmpty()

    suspend fun completeOnboarding(
        cashOpeningMinor: Long,
        cardOpeningMinor: Long
    ) {
        require(cashOpeningMinor >= 0 && cardOpeningMinor >= 0) {
            "opening balances must be non-negative"
        }
        accountRepository.replaceAll(
            listOf(
                Account(id = 1L, name = CASH_NAME, openingBalanceMinor = cashOpeningMinor),
                Account(id = 2L, name = CARD_NAME, openingBalanceMinor = cardOpeningMinor)
            )
        )
        categoryRepository.replaceAll(CategorySeed.defaults())
    }

    suspend fun categories(kind: CategoryKind): List<Category> {
        ensureCategoriesSeeded()
        return categoryRepository.getAll().filter { it.kind == kind }
    }

    suspend fun accountBalances(): List<AccountBalance> =
        accountRepository.getAll().map { account ->
            AccountBalance(
                account = account,
                balanceMinor = balanceFor(account)
            )
        }

    suspend fun totalBalanceMinor(): Long =
        accountBalances().sumOf { it.balanceMinor }

    suspend fun addExpense(
        accountId: Long,
        categoryId: Long,
        amountMinor: Long,
        date: LocalDate,
        comment: String?
    ): Long {
        require(amountMinor > 0) { "amount must be positive" }
        ensureCategoriesSeeded()
        require(accountRepository.getAll().any { it.id == accountId }) { "account not found" }
        val category = categoryRepository.getAll().firstOrNull { it.id == categoryId }
            ?: error("category not found")
        require(category.kind == CategoryKind.EXPENSE) { "category must be expense" }

        return operationRepository.insert(
            Operation(
                id = 0L,
                kind = OperationKind.EXPENSE,
                amountMinor = amountMinor,
                date = date,
                comment = comment?.takeIf { it.isNotBlank() },
                accountId = accountId,
                categoryId = categoryId
            )
        )
    }

    suspend fun recentExpenses(limit: Int): List<Operation> {
        require(limit > 0)
        return operationRepository.getAll()
            .filter { it.kind == OperationKind.EXPENSE }
            .sortedWith(compareByDescending<Operation> { it.date }.thenByDescending { it.id })
            .take(limit)
    }

    private suspend fun balanceFor(account: Account): Long {
        var balance = account.openingBalanceMinor
        for (operation in operationRepository.getAll()) {
            when (operation.kind) {
                OperationKind.EXPENSE -> {
                    if (operation.accountId == account.id) {
                        balance -= operation.amountMinor
                    }
                }
                OperationKind.INCOME -> {
                    if (operation.accountId == account.id) {
                        balance += operation.amountMinor
                    }
                }
                OperationKind.TRANSFER -> {
                    if (operation.fromAccountId == account.id) {
                        balance -= operation.amountMinor
                    }
                    if (operation.toAccountId == account.id) {
                        balance += operation.amountMinor
                    }
                }
            }
        }
        return balance
    }

    private suspend fun ensureCategoriesSeeded() {
        if (categoryRepository.getAll().isEmpty() && accountRepository.getAll().isNotEmpty()) {
            categoryRepository.replaceAll(CategorySeed.defaults())
        }
    }

    companion object {
        const val CASH_NAME = "Наличные"
        const val CARD_NAME = "ЗП карта"
    }
}
