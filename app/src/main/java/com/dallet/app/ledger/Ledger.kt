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

    suspend fun addIncome(
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
        require(category.kind == CategoryKind.INCOME) { "category must be income" }

        return operationRepository.insert(
            Operation(
                id = 0L,
                kind = OperationKind.INCOME,
                amountMinor = amountMinor,
                date = date,
                comment = comment?.takeIf { it.isNotBlank() },
                accountId = accountId,
                categoryId = categoryId
            )
        )
    }

    suspend fun addTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amountMinor: Long,
        date: LocalDate,
        comment: String?
    ): Long {
        require(amountMinor > 0) { "amount must be positive" }
        if (fromAccountId == toAccountId) {
            throw SameAccountTransferException()
        }
        val accounts = accountRepository.getAll()
        require(accounts.any { it.id == fromAccountId }) { "from account not found" }
        require(accounts.any { it.id == toAccountId }) { "to account not found" }

        return operationRepository.insert(
            Operation(
                id = 0L,
                kind = OperationKind.TRANSFER,
                amountMinor = amountMinor,
                date = date,
                comment = comment?.takeIf { it.isNotBlank() },
                accountId = null,
                categoryId = null,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId
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

    suspend fun allOperations(): List<Operation> =
        operationRepository.getAll()
            .sortedWith(compareByDescending<Operation> { it.date }.thenByDescending { it.id })

    suspend fun operation(operationId: Long): Operation? =
        operationRepository.getAll().firstOrNull { it.id == operationId }

    suspend fun updateExpense(
        operationId: Long,
        accountId: Long,
        categoryId: Long,
        amountMinor: Long,
        date: LocalDate,
        comment: String?
    ) {
        require(amountMinor > 0) { "amount must be positive" }
        val existing = operation(operationId) ?: error("operation not found")
        require(existing.kind == OperationKind.EXPENSE) { "not an expense" }
        ensureCategoriesSeeded()
        require(accountRepository.getAll().any { it.id == accountId }) { "account not found" }
        val category = categoryRepository.getAll().firstOrNull { it.id == categoryId }
            ?: error("category not found")
        require(category.kind == CategoryKind.EXPENSE) { "category must be expense" }

        operationRepository.update(
            existing.copy(
                amountMinor = amountMinor,
                date = date,
                comment = comment?.takeIf { it.isNotBlank() },
                accountId = accountId,
                categoryId = categoryId
            )
        )
    }

    suspend fun updateIncome(
        operationId: Long,
        accountId: Long,
        categoryId: Long,
        amountMinor: Long,
        date: LocalDate,
        comment: String?
    ) {
        require(amountMinor > 0) { "amount must be positive" }
        val existing = operation(operationId) ?: error("operation not found")
        require(existing.kind == OperationKind.INCOME) { "not an income" }
        ensureCategoriesSeeded()
        require(accountRepository.getAll().any { it.id == accountId }) { "account not found" }
        val category = categoryRepository.getAll().firstOrNull { it.id == categoryId }
            ?: error("category not found")
        require(category.kind == CategoryKind.INCOME) { "category must be income" }

        operationRepository.update(
            existing.copy(
                amountMinor = amountMinor,
                date = date,
                comment = comment?.takeIf { it.isNotBlank() },
                accountId = accountId,
                categoryId = categoryId
            )
        )
    }

    suspend fun updateTransfer(
        operationId: Long,
        fromAccountId: Long,
        toAccountId: Long,
        amountMinor: Long,
        date: LocalDate,
        comment: String?
    ) {
        require(amountMinor > 0) { "amount must be positive" }
        val existing = operation(operationId) ?: error("operation not found")
        require(existing.kind == OperationKind.TRANSFER) { "not a transfer" }
        if (fromAccountId == toAccountId) {
            throw SameAccountTransferException()
        }
        val accounts = accountRepository.getAll()
        require(accounts.any { it.id == fromAccountId }) { "from account not found" }
        require(accounts.any { it.id == toAccountId }) { "to account not found" }

        operationRepository.update(
            existing.copy(
                amountMinor = amountMinor,
                date = date,
                comment = comment?.takeIf { it.isNotBlank() },
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                accountId = null,
                categoryId = null
            )
        )
    }

    suspend fun deleteOperation(operationId: Long) {
        require(operation(operationId) != null) { "operation not found" }
        operationRepository.delete(operationId)
    }

    suspend fun createAccount(name: String, openingBalanceMinor: Long): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name required" }
        require(openingBalanceMinor >= 0) { "opening balance must be non-negative" }
        val nextId = (accountRepository.getAll().maxOfOrNull { it.id } ?: 0L) + 1L
        return accountRepository.insert(
            Account(id = nextId, name = trimmed, openingBalanceMinor = openingBalanceMinor)
        )
    }

    suspend fun updateAccount(
        accountId: Long,
        name: String,
        openingBalanceMinor: Long
    ) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name required" }
        require(openingBalanceMinor >= 0) { "opening balance must be non-negative" }
        val existing = accountRepository.getAll().firstOrNull { it.id == accountId }
            ?: error("account not found")
        accountRepository.update(
            existing.copy(name = trimmed, openingBalanceMinor = openingBalanceMinor)
        )
    }

    suspend fun deleteAccount(accountId: Long) {
        require(accountRepository.getAll().any { it.id == accountId }) { "account not found" }
        val used = operationRepository.getAll().any { operation ->
            operation.accountId == accountId ||
                operation.fromAccountId == accountId ||
                operation.toAccountId == accountId
        }
        if (used) {
            throw AccountHasOperationsException()
        }
        accountRepository.delete(accountId)
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
