package com.dallet.app.ledger

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class Ledger(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val operationRepository: OperationRepository,
    private val shoppingRepository: ShoppingRepository = InMemoryShoppingRepository(),
    private val settings: SettingsStore = InMemorySettingsStore(),
    private val dataStore: LedgerDataStore =
        RepositoryLedgerDataStore(
            accountRepository,
            categoryRepository,
            operationRepository,
            shoppingRepository
        )
) {
    suspend fun hasCompletedOnboarding(): Boolean = accountRepository.getAll().isNotEmpty()

    suspend fun exportBackup(): String =
        BackupCodec.encode(
            BackupData(
                accounts = accountRepository.getAll(),
                categories = categoryRepository.getAll(),
                operations = operationRepository.getAll(),
                shopping = shoppingRepository.getAll(),
                mainCurrency = settings.mainCurrency()
            )
        )

    suspend fun importBackup(json: String) {
        val data = BackupCodec.decode(json)
        BackupCodec.validate(data)
        dataStore.replaceAll(data.accounts, data.categories, data.operations, data.shopping)
        settings.setMainCurrency(data.mainCurrency)
    }

    suspend fun resetAll() {
        dataStore.replaceAll(emptyList(), emptyList(), emptyList(), ShoppingData.EMPTY)
        settings.setMainCurrency(Currency.DEFAULT)
    }

    suspend fun mainCurrency(): Currency = settings.mainCurrency()

    suspend fun completeOnboarding(
        accounts: List<NewAccount>,
        currency: Currency = Currency.DEFAULT
    ) {
        require(accounts.isNotEmpty()) { "at least one account is required" }
        val cleaned = accounts.map { it.copy(name = it.name.trim()) }
        require(cleaned.all { it.name.isNotEmpty() }) { "account name must not be blank" }
        require(cleaned.all { it.openingBalanceMinor >= 0 }) {
            "opening balances must be non-negative"
        }
        require(cleaned.map { it.name.lowercase() }.toSet().size == cleaned.size) {
            "account names must be unique"
        }
        accountRepository.replaceAll(
            cleaned.mapIndexed { index, item ->
                Account(
                    id = index + 1L,
                    name = item.name,
                    openingBalanceMinor = item.openingBalanceMinor
                )
            }
        )
        categoryRepository.replaceAll(CategorySeed.defaults())
        settings.setMainCurrency(currency)
    }

    suspend fun completeOnboarding(
        cashOpeningMinor: Long,
        cardOpeningMinor: Long
    ) = completeOnboarding(
        listOf(
            NewAccount(CASH_NAME, cashOpeningMinor),
            NewAccount(CARD_NAME, cardOpeningMinor)
        ),
        Currency.DEFAULT
    )

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
        comment: String?,
        time: LocalTime? = null
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
                time = time,
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
        comment: String?,
        time: LocalTime? = null
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
                time = time,
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
        comment: String?,
        time: LocalTime? = null
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
                time = time,
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
            .sortedWith(compareByDescending<Operation> { it.date }.thenByDescending { it.time ?: LocalTime.MIN }.thenByDescending { it.id })
            .take(limit)
    }

    suspend fun allOperations(): List<Operation> =
        operationRepository.getAll()
            .sortedWith(compareByDescending<Operation> { it.date }.thenByDescending { it.time ?: LocalTime.MIN }.thenByDescending { it.id })

    suspend fun operation(operationId: Long): Operation? =
        operationRepository.getAll().firstOrNull { it.id == operationId }

    suspend fun updateExpense(
        operationId: Long,
        accountId: Long,
        categoryId: Long,
        amountMinor: Long,
        date: LocalDate,
        comment: String?,
        time: LocalTime? = null
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
                time = time,
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
        comment: String?,
        time: LocalTime? = null
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
                time = time,
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
        comment: String?,
        time: LocalTime? = null
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
                time = time,
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

    suspend fun createCategory(
        name: String,
        kind: CategoryKind,
        colorArgb: Int,
        iconKey: String
    ): Long {
        ensureCategoriesSeeded()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name required" }
        require(iconKey.isNotBlank()) { "icon required" }
        val nextId = (categoryRepository.getAll().maxOfOrNull { it.id } ?: 0L) + 1L
        return categoryRepository.insert(
            Category(
                id = nextId,
                name = trimmed,
                kind = kind,
                colorArgb = colorArgb,
                iconKey = iconKey.trim()
            )
        )
    }

    suspend fun updateCategory(
        categoryId: Long,
        name: String,
        colorArgb: Int,
        iconKey: String
    ) {
        ensureCategoriesSeeded()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name required" }
        require(iconKey.isNotBlank()) { "icon required" }
        val existing = categoryRepository.getAll().firstOrNull { it.id == categoryId }
            ?: error("category not found")
        categoryRepository.update(
            existing.copy(
                name = trimmed,
                colorArgb = colorArgb,
                iconKey = iconKey.trim()
            )
        )
    }

    suspend fun deleteCategory(categoryId: Long) {
        ensureCategoriesSeeded()
        require(categoryRepository.getAll().any { it.id == categoryId }) { "category not found" }
        val used = operationRepository.getAll().any { it.categoryId == categoryId }
        if (used) {
            throw CategoryHasOperationsException()
        }
        categoryRepository.delete(categoryId)
    }

    suspend fun analytics(period: AnalyticsPeriod, asOf: LocalDate): AnalyticsSummary {
        ensureCategoriesSeeded()
        val (fromInclusive, toInclusive) = periodBounds(period, asOf)
        val inPeriod = operationRepository.getAll().filter { operation ->
            !operation.date.isBefore(fromInclusive) && !operation.date.isAfter(toInclusive)
        }
        val incomes = inPeriod.filter { it.kind == OperationKind.INCOME }
        val incomeTotalMinor = incomes.sumOf { it.amountMinor }
        val expenses = inPeriod.filter { it.kind == OperationKind.EXPENSE }
        val expenseTotalMinor = expenses.sumOf { it.amountMinor }
        val categoriesById = categoryRepository.getAll().associateBy { it.id }
        return AnalyticsSummary(
            period = period,
            fromInclusive = fromInclusive,
            toInclusive = toInclusive,
            incomeTotalMinor = incomeTotalMinor,
            expenseTotalMinor = expenseTotalMinor,
            expensesByCategory = categoryTotals(expenses, categoriesById),
            expenseFlow = expenseFlowSeries(period, fromInclusive, toInclusive, expenses),
            incomesByCategory = categoryTotals(incomes, categoriesById),
            incomeFlow = expenseFlowSeries(period, fromInclusive, toInclusive, incomes),
            byAccount = accountRepository.getAll().map { account ->
                AccountPeriodTotal(
                    accountId = account.id,
                    accountName = account.name,
                    incomeMinor = incomes.filter { it.accountId == account.id }
                        .sumOf { it.amountMinor },
                    expenseMinor = expenses.filter { it.accountId == account.id }
                        .sumOf { it.amountMinor }
                )
            }
        )
    }

    suspend fun analyticsComparison(
        period: AnalyticsPeriod,
        asOf: LocalDate,
        today: LocalDate
    ): AnalyticsComparison {
        val current = analytics(period, asOf)
        val previous = analytics(period, shiftAsOf(period, asOf, -1))
        return AnalyticsComparison(
            current = current,
            previous = previous,
            currentIsComplete = !current.toInclusive.isAfter(today)
        )
    }

    suspend fun periodReport(
        period: AnalyticsPeriod,
        asOf: LocalDate,
        today: LocalDate
    ): PeriodReport {
        val comparison = analyticsComparison(period, asOf, today)
        val from = comparison.current.fromInclusive
        val to = comparison.current.toInclusive
        val accountNames = accountRepository.getAll().associate { it.id to it.name }
        val categoryNames = categoryRepository.getAll().associate { it.id to it.name }
        val rows = operationRepository.getAll()
            .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .sortedWith(compareBy<Operation>({ it.date }, { it.time ?: LocalTime.MIN }, { it.id }))
            .map { operation ->
                val account = when (operation.kind) {
                    OperationKind.TRANSFER ->
                        "${accountNames[operation.fromAccountId].orEmpty()} → " +
                            accountNames[operation.toAccountId].orEmpty()
                    else -> accountNames[operation.accountId].orEmpty()
                }
                ReportRow(
                    date = operation.date,
                    time = operation.time,
                    kind = operation.kind,
                    accountName = account,
                    categoryName = categoryNames[operation.categoryId].orEmpty(),
                    amountMinor = operation.amountMinor,
                    comment = operation.comment
                )
            }
        return PeriodReport(comparison = comparison, rows = rows)
    }

    private fun categoryTotals(
        operations: List<Operation>,
        categoriesById: Map<Long, Category>
    ): List<CategoryExpenseTotal> =
        operations
            .groupBy { it.categoryId }
            .mapNotNull { (categoryId, ops) ->
                val id = categoryId ?: return@mapNotNull null
                val category = categoriesById[id] ?: return@mapNotNull null
                CategoryExpenseTotal(
                    categoryId = id,
                    categoryName = category.name,
                    colorArgb = category.colorArgb,
                    amountMinor = ops.sumOf { it.amountMinor }
                )
            }
            .sortedByDescending { it.amountMinor }

    private fun expenseFlowSeries(
        period: AnalyticsPeriod,
        fromInclusive: LocalDate,
        toInclusive: LocalDate,
        expenses: List<Operation>
    ): List<ExpenseFlowPoint> {
        return when (period) {
            AnalyticsPeriod.WEEK, AnalyticsPeriod.MONTH -> {
                val byDay = expenses.groupBy { it.date }.mapValues { (_, ops) ->
                    ops.sumOf { it.amountMinor }
                }
                generateSequence(fromInclusive) { date ->
                    val next = date.plusDays(1)
                    if (next.isAfter(toInclusive)) null else next
                }.map { day ->
                    ExpenseFlowPoint(
                        bucketStart = day,
                        label = day.format(DAY_FLOW_LABEL),
                        amountMinor = byDay[day] ?: 0L
                    )
                }.toList()
            }
            AnalyticsPeriod.YEAR -> {
                val byMonth = expenses.groupBy { YearMonth.from(it.date) }
                    .mapValues { (_, ops) -> ops.sumOf { it.amountMinor } }
                val year = fromInclusive.year
                (1..12).map { month ->
                    val ym = YearMonth.of(year, month)
                    ExpenseFlowPoint(
                        bucketStart = ym.atDay(1),
                        label = ym.format(MONTH_FLOW_LABEL),
                        amountMinor = byMonth[ym] ?: 0L
                    )
                }
            }
        }
    }

    private fun periodBounds(period: AnalyticsPeriod, asOf: LocalDate): Pair<LocalDate, LocalDate> {
        return when (period) {
            AnalyticsPeriod.WEEK -> {
                val from = asOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val to = asOf.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                from to to
            }
            AnalyticsPeriod.MONTH -> {
                asOf.withDayOfMonth(1) to asOf.with(TemporalAdjusters.lastDayOfMonth())
            }
            AnalyticsPeriod.YEAR -> {
                LocalDate.of(asOf.year, 1, 1) to LocalDate.of(asOf.year, 12, 31)
            }
        }
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
        private val DAY_FLOW_LABEL: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d.M")
        private val MONTH_FLOW_LABEL: DateTimeFormatter =
            DateTimeFormatter.ofPattern("LLL", Locale.forLanguageTag("ru"))
    }
}
