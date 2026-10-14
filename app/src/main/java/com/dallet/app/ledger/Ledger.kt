package com.dallet.app.ledger

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class Ledger(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val operationRepository: OperationRepository,
    private val shoppingRepository: ShoppingRepository = InMemoryShoppingRepository(),
    private val settings: SettingsStore = InMemorySettingsStore(),
    private val goalRepository: GoalRepository = InMemoryGoalRepository(),
    private val debtRepository: DebtRepository = InMemoryDebtRepository(),
    private val dataStore: LedgerDataStore =
        RepositoryLedgerDataStore(
            accountRepository,
            categoryRepository,
            operationRepository,
            shoppingRepository,
            goalRepository,
            debtRepository
        )
) {
    suspend fun hasCompletedOnboarding(): Boolean = accountRepository.getAll().isNotEmpty()

    suspend fun exportBackup(): String {
        val categories = categoryRepository.getAll()
        val expenseIds = categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()
        val categoryIds = categories.map { it.id }.toSet()
        val accountIds = accountRepository.getAll().map { it.id }.toSet()
        return BackupCodec.encode(
            BackupData(
                accounts = accountRepository.getAll(),
                categories = categories,
                operations = operationRepository.getAll(),
                shopping = shoppingRepository.getAll(),
                goals = goalRepository.getAll(),
                debts = debtRepository.getAll(),
                mainCurrency = settings.mainCurrency(),
                budgets = settings.budgets().filterKeys { it in expenseIds },
                recurring = settings.recurring().filter { rule ->
                    rule.categoryId in categoryIds && rule.accountId in accountIds
                }
            )
        )
    }

    suspend fun importBackup(json: String) {
        val data = BackupCodec.decode(json)
        BackupCodec.validate(data)
        // A purchase may point at an expense that was deleted by hand (older copies keep that
        // link); drop it, or a later expense reusing the id would be taken for the purchase's.
        val operationIds = data.operations.map { it.id }.toSet()
        val shopping = data.shopping.copy(
            lists = data.shopping.lists.map { list ->
                val linked = list.operationId
                if (linked != null && linked !in operationIds) list.copy(operationId = null) else list
            }
        )
        val goals = data.goals.copy(
            entries = data.goals.entries.map { entry ->
                val linked = entry.operationId
                if (linked != null && linked !in operationIds) entry.copy(operationId = null) else entry
            }
        )
        val debts = data.debts.copy(
            entries = data.debts.entries.map { entry ->
                val linked = entry.operationId
                if (linked != null && linked !in operationIds) entry.copy(operationId = null) else entry
            }
        )
        dataStore.replaceAll(data.accounts, data.categories, data.operations, shopping, goals, debts)
        settings.saveRates(emptyList())
        settings.setMainCurrency(data.mainCurrency)
        settings.saveBudgets(data.budgets)
        settings.saveRecurring(data.recurring)
    }

    suspend fun resetAll() {
        dataStore.replaceAll(emptyList(), emptyList(), emptyList(), ShoppingData.EMPTY, GoalData.EMPTY, DebtData.EMPTY)
        settings.saveRates(emptyList())
        settings.saveBudgets(emptyMap())
        settings.saveRecurring(emptyList())
        settings.setMainCurrency(Currency.DEFAULT)
    }

    suspend fun mainCurrency(): Currency = settings.mainCurrency()

    suspend fun setMainCurrency(currency: Currency) {
        if (currency != settings.mainCurrency()) settings.saveRates(emptyList())
        settings.setMainCurrency(currency)
    }

    suspend fun rates(): List<ExchangeRate> = settings.rates()

    suspend fun budgets(): Map<Long, Long> = settings.budgets()

    suspend fun setBudget(categoryId: Long, limitMinor: Long?) {
        val current = settings.budgets().toMutableMap()
        if (limitMinor == null || limitMinor <= 0L) {
            current.remove(categoryId)
        } else {
            val category = categoryRepository.getAll().firstOrNull { it.id == categoryId }
                ?: error("category not found")
            require(category.kind == CategoryKind.EXPENSE) { "budget needs an expense category" }
            current[categoryId] = limitMinor
        }
        settings.saveBudgets(current)
    }

    suspend fun budgetStatuses(today: LocalDate): List<BudgetStatus> {
        val budgets = settings.budgets()
        if (budgets.isEmpty()) return emptyList()
        val month = YearMonth.from(today)
        val spent = operationRepository.getAll()
            .filter { it.kind == OperationKind.EXPENSE && YearMonth.from(it.date) == month }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amountMinor } }
        val all = categoryRepository.getAll()
        val byId = all.associateBy { it.id }
        val childrenOf = all.groupBy { it.parentId }
        // A budget on a category covers its subcategories too.
        fun spentOn(id: Long): Long =
            (spent[id] ?: 0L) + (childrenOf[id]?.sumOf { spent[it.id] ?: 0L } ?: 0L)
        return all
            .filter { it.kind == CategoryKind.EXPENSE && budgets.containsKey(it.id) }
            .map { BudgetStatus(withLabel(it, byId), budgets.getValue(it.id), spentOn(it.id)) }
            .sortedByDescending { it.fraction }
    }

    /** The tightest budget touched by an expense on [categoryId]: its own or its parent's. */
    suspend fun budgetStatus(categoryId: Long, today: LocalDate): BudgetStatus? {
        val parentId = categoryRepository.getAll().firstOrNull { it.id == categoryId }?.parentId
        return budgetStatuses(today)
            .filter { it.category.id == categoryId || it.category.id == parentId }
            .maxByOrNull { it.fraction }
    }

    suspend fun recurringRules(): List<RecurringRule> = settings.recurring()

    private suspend fun requireRecurringTargets(
        kind: OperationKind,
        accountId: Long,
        categoryId: Long,
        amountMinor: Long
    ) {
        require(kind == OperationKind.EXPENSE || kind == OperationKind.INCOME) {
            "only expense and income can repeat"
        }
        require(amountMinor > 0) { "amount must be positive" }
        require(accountRepository.getAll().any { it.id == accountId }) { "account not found" }
        val category = categoryRepository.getAll().firstOrNull { it.id == categoryId }
            ?: error("category not found")
        val expected =
            if (kind == OperationKind.EXPENSE) CategoryKind.EXPENSE else CategoryKind.INCOME
        require(category.kind == expected) { "category kind does not match" }
    }

    suspend fun addRecurring(
        kind: OperationKind,
        accountId: Long,
        categoryId: Long,
        amountMinor: Long,
        comment: String?,
        frequency: Frequency,
        startDate: LocalDate,
        endDate: LocalDate? = null,
        lastAmountMinor: Long? = null,
        totalPayments: Int? = null
    ): Long {
        requireRecurringTargets(kind, accountId, categoryId, amountMinor)
        require(endDate == null || !endDate.isBefore(startDate)) { "end before start" }
        val rules = settings.recurring()
        val id = (rules.maxOfOrNull { it.id } ?: 0L) + 1L
        settings.saveRecurring(
            rules + RecurringRule(
                id = id,
                kind = kind,
                accountId = accountId,
                categoryId = categoryId,
                amountMinor = amountMinor,
                comment = comment?.takeIf { it.isNotBlank() },
                frequency = frequency,
                startDate = startDate,
                nextDue = startDate,
                endDate = endDate,
                lastAmountMinor = lastAmountMinor,
                totalPayments = totalPayments
            )
        )
        return id
    }

    suspend fun updateRecurring(
        id: Long,
        kind: OperationKind,
        accountId: Long,
        categoryId: Long,
        amountMinor: Long,
        comment: String?,
        frequency: Frequency,
        startDate: LocalDate,
        endDate: LocalDate? = null,
        lastAmountMinor: Long? = null,
        totalPayments: Int? = null
    ) {
        requireRecurringTargets(kind, accountId, categoryId, amountMinor)
        require(endDate == null || !endDate.isBefore(startDate)) { "end before start" }
        val rules = settings.recurring()
        val old = rules.firstOrNull { it.id == id } ?: error("rule not found")
        val scheduleChanged = old.startDate != startDate || old.frequency != frequency
        settings.saveRecurring(
            rules.map {
                if (it.id != id) {
                    it
                } else {
                    it.copy(
                        kind = kind,
                        accountId = accountId,
                        categoryId = categoryId,
                        amountMinor = amountMinor,
                        comment = comment?.takeIf { text -> text.isNotBlank() },
                        frequency = frequency,
                        startDate = startDate,
                        nextDue = if (scheduleChanged) startDate else old.nextDue,
                        endDate = endDate,
                        lastAmountMinor = lastAmountMinor,
                        totalPayments = totalPayments
                    )
                }
            }
        )
    }

    suspend fun deleteRecurring(id: Long) {
        settings.saveRecurring(settings.recurring().filterNot { it.id == id })
    }

    suspend fun dueItems(today: LocalDate): List<DueItem> =
        settings.recurring()
            .flatMap { rule ->
                RecurringSchedule.dueDates(rule, today).mapIndexed { index, date ->
                    DueItem(rule, date, isFirst = index == 0)
                }
            }
            .sortedWith(compareBy<DueItem> { it.dueDate }.thenBy { it.rule.id })

    /** Creates the operation for the earliest due date of the rule and moves the schedule on. */
    suspend fun confirmDue(ruleId: Long, dueDate: LocalDate, amountMinor: Long): Long {
        val rules = settings.recurring()
        val rule = rules.firstOrNull { it.id == ruleId } ?: error("rule not found")
        require(rule.nextDue == dueDate) { "only the earliest due date can be handled" }
        val operationId = when (rule.kind) {
            OperationKind.EXPENSE ->
                addExpense(rule.accountId, rule.categoryId, amountMinor, dueDate, rule.comment)
            OperationKind.INCOME ->
                addIncome(rule.accountId, rule.categoryId, amountMinor, dueDate, rule.comment)
            OperationKind.TRANSFER -> error("transfers cannot repeat")
        }
        advanceRecurring(rules, rule, dueDate)
        return operationId
    }

    suspend fun skipDue(ruleId: Long, dueDate: LocalDate) {
        val rules = settings.recurring()
        val rule = rules.firstOrNull { it.id == ruleId } ?: error("rule not found")
        require(rule.nextDue == dueDate) { "only the earliest due date can be handled" }
        advanceRecurring(rules, rule, dueDate)
    }

    private fun advanceRecurring(rules: List<RecurringRule>, rule: RecurringRule, handled: LocalDate) {
        val next = RecurringSchedule.occurrenceAfter(rule, handled)
        settings.saveRecurring(rules.map { if (it.id == rule.id) it.copy(nextDue = next) else it })
    }

    suspend fun saveRates(rates: Map<Currency, Long>, today: java.time.LocalDate) {
        val main = settings.mainCurrency()
        require(rates.all { it.key != main && it.value > 0 }) { "invalid rates" }
        val previous = settings.rates().associateBy { it.currency }
        settings.saveRates(
            rates.map { (currency, scaled) ->
                val old = previous[currency]
                ExchangeRate(
                    currency,
                    scaled,
                    if (old != null && old.scaled == scaled) old.updatedOn else today
                )
            }.sortedBy { it.currency.ordinal }
        )
    }

    suspend fun completeOnboarding(
        accounts: List<NewAccount>,
        currency: Currency = Currency.DEFAULT,
        categoryNames: List<String>? = null
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
        categoryRepository.replaceAll(CategorySeed.defaults(categoryNames))
        settings.saveBudgets(emptyMap())
        settings.saveRecurring(emptyList())
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
        return hierarchical(categoryRepository.getAll().filter { it.kind == kind })
    }

    /**
     * Categories ready for display: parents first, each followed by its subcategories, and a
     * subcategory named "Parent › Child". Use [categories] when the real name is needed.
     */
    suspend fun displayCategories(kind: CategoryKind? = null): List<Category> {
        ensureCategoriesSeeded()
        val all = categoryRepository.getAll()
        val byId = all.associateBy { it.id }
        return hierarchical(all.filter { kind == null || it.kind == kind }).map { withLabel(it, byId) }
    }

    /** Id to display name ("Parent › Child" for subcategories) of every category. */
    suspend fun categoryLabels(): Map<Long, String> =
        displayCategories().associate { it.id to it.name }

    /** Subcategory id to the id of its parent. */
    private suspend fun categoryParentMap(): Map<Long, Long> =
        categoryRepository.getAll().filter { it.parentId != null }.associate { it.id to it.parentId!! }

    private fun withLabel(category: Category, byId: Map<Long, Category>): Category {
        val parent = category.parentId?.let { byId[it] } ?: return category
        return category.copy(name = parent.name + " › " + category.name)
    }

    private fun hierarchical(list: List<Category>): List<Category> {
        val ids = list.map { it.id }.toSet()
        val roots = list.filter { it.parentId == null || it.parentId !in ids }
        val children = list.filter { it.parentId != null && it.parentId in ids }.groupBy { it.parentId }
        return roots.flatMap { root -> listOf(root) + (children[root.id] ?: emptyList()) }
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

    /** Operations that pass [filter], grouped by day, newest first. */
    suspend fun history(
        filter: OperationFilter = OperationFilter(),
        today: LocalDate = LocalDate.now()
    ): HistoryResult {
        val operations = allOperations()
        if (filter.isEmpty) return groupHistory(operations)
        val accountNames = accountRepository.getAll().associate { it.id to it.name }
        val categoryNames = categoryLabels()
        val categoryParents = categoryParentMap()
        val range = filter.dateRange(today)
        return groupHistory(
            operations.filter { filter.matches(it, accountNames, categoryNames, range, categoryParents) }
        )
    }

    /** Side [a] measured against side [b]; each side is a filter with its own period. */
    suspend fun compare(
        a: OperationFilter,
        b: OperationFilter,
        today: LocalDate = LocalDate.now()
    ): Comparison {
        ensureCategoriesSeeded()
        return buildComparison(
            a = a,
            b = b,
            operations = operationRepository.getAll(),
            accountNames = accountRepository.getAll().associate { it.id to it.name },
            categories = categoryRepository.getAll(),
            today = today
        )
    }

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
        // The amount of a purchase's expense is the sum of its list; it only changes by reopening.
        if (amountMinor != existing.amountMinor && purchaseOf(operationId) != null) {
            throw PurchaseExpenseLockedException()
        }
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
        // A purchase that pointed at this expense stays closed, only without the link.
        purchaseOf(operationId)?.let { shoppingRepository.updateList(it.copy(operationId = null)) }
        // A goal deposit made "as income" keeps its money in the goal, now taken from the account.
        goalRepository.getAll().entries.filter { it.operationId == operationId }
            .forEach { goalRepository.updateEntry(it.copy(operationId = null)) }
        // A write-off keeps its effect on the debt; only the link to the deleted operation goes.
        val debtData = debtRepository.getAll()
        debtData.entries.filter { it.operationId == operationId }
            .forEach { debtRepository.updateEntry(it.copy(operationId = null)) }
    }

    /** The purchase whose closing created this expense, or null for an ordinary operation. */
    suspend fun purchaseOf(operationId: Long): ShoppingList? =
        shoppingRepository.lists().firstOrNull { it.operationId == operationId }

    suspend fun purchaseItems(listId: Long): List<ShoppingItem> = shoppingRepository.items(listId)

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
        val usedByGoal = goalRepository.getAll().entries.any { it.accountId == accountId }
        val usedByDebt = debtRepository.getAll().entries.any { it.accountId == accountId }
        if (used || usedByGoal || usedByDebt) {
            throw AccountHasOperationsException()
        }
        accountRepository.delete(accountId)
    }

    suspend fun createCategory(
        name: String,
        kind: CategoryKind,
        colorArgb: Int,
        iconKey: String,
        parentId: Long? = null
    ): Long {
        ensureCategoriesSeeded()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name required" }
        require(iconKey.isNotBlank()) { "icon required" }
        requireValidParent(parentId, kind, selfId = null)
        val nextId = (categoryRepository.getAll().maxOfOrNull { it.id } ?: 0L) + 1L
        return categoryRepository.insert(
            Category(
                id = nextId,
                name = trimmed,
                kind = kind,
                colorArgb = colorArgb,
                iconKey = iconKey.trim(),
                parentId = parentId
            )
        )
    }

    /** A parent must be a top-level category of the same kind, and not the category itself. */
    private suspend fun requireValidParent(parentId: Long?, kind: CategoryKind, selfId: Long?) {
        if (parentId == null) return
        val all = categoryRepository.getAll()
        val parent = all.firstOrNull { it.id == parentId } ?: error("parent not found")
        require(parentId != selfId) { "a category cannot be its own parent" }
        require(parent.kind == kind) { "parent must be of the same kind" }
        require(parent.parentId == null) { "subcategories cannot have subcategories" }
        if (selfId != null) {
            require(all.none { it.parentId == selfId }) { "a category with subcategories stays top-level" }
        }
    }

    suspend fun updateCategory(
        categoryId: Long,
        name: String,
        colorArgb: Int,
        iconKey: String,
        parentId: Long? = null
    ) {
        ensureCategoriesSeeded()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name required" }
        require(iconKey.isNotBlank()) { "icon required" }
        val existing = categoryRepository.getAll().firstOrNull { it.id == categoryId }
            ?: error("category not found")
        requireValidParent(parentId, existing.kind, selfId = categoryId)
        categoryRepository.update(
            existing.copy(
                name = trimmed,
                colorArgb = colorArgb,
                iconKey = iconKey.trim(),
                parentId = parentId
            )
        )
    }

    suspend fun deleteCategory(categoryId: Long) {
        ensureCategoriesSeeded()
        require(categoryRepository.getAll().any { it.id == categoryId }) { "category not found" }
        if (categoryRepository.getAll().any { it.parentId == categoryId }) {
            throw CategoryHasChildrenException()
        }
        val used = operationRepository.getAll().any { it.categoryId == categoryId }
        if (used) {
            throw CategoryHasOperationsException()
        }
        categoryRepository.delete(categoryId)
    }

    /** [window] is required for [AnalyticsPeriod.CUSTOM] and ignored for the calendar periods. */
    suspend fun analytics(
        period: AnalyticsPeriod,
        asOf: LocalDate,
        window: DateWindow? = null
    ): AnalyticsSummary {
        ensureCategoriesSeeded()
        val (fromInclusive, toInclusive) = periodBounds(period, asOf, window)
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
        today: LocalDate,
        window: DateWindow? = null
    ): AnalyticsComparison {
        val current = analytics(period, asOf, window)
        val previous = analytics(period, shiftAsOf(period, asOf, -1), window?.shifted(-1))
        return AnalyticsComparison(
            current = current,
            previous = previous,
            currentIsComplete = !current.toInclusive.isAfter(today)
        )
    }

    suspend fun periodReport(
        period: AnalyticsPeriod,
        asOf: LocalDate,
        today: LocalDate,
        window: DateWindow? = null
    ): PeriodReport {
        val comparison = analyticsComparison(period, asOf, today, window)
        val from = comparison.current.fromInclusive
        val to = comparison.current.toInclusive
        val accountNames = accountRepository.getAll().associate { it.id to it.name }
        val categoryNames = categoryLabels()
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
        return PeriodReport(
            comparison = comparison,
            rows = rows,
            goals = goals().filter { !it.goal.archived },
            debts = debts().filter { !it.settled }
        )
    }

    /** Totals per top-level category: a subcategory counts toward its parent and shows as a part of it. */
    private fun categoryTotals(
        operations: List<Operation>,
        categoriesById: Map<Long, Category>
    ): List<CategoryExpenseTotal> {
        fun rootOf(category: Category): Category =
            category.parentId?.let { categoriesById[it] } ?: category
        return operations
            .mapNotNull { operation ->
                val category = operation.categoryId?.let { categoriesById[it] } ?: return@mapNotNull null
                operation to category
            }
            .groupBy { (_, category) -> rootOf(category).id }
            .map { (rootId, pairs) ->
                val root = categoriesById.getValue(rootId)
                val parts = pairs.filter { it.second.id != rootId }
                    .groupBy { it.second.id }
                    .map { (childId, items) ->
                        NamedAmount(
                            id = childId,
                            name = categoriesById.getValue(childId).name,
                            amountMinor = items.sumOf { it.first.amountMinor }
                        )
                    }
                    .sortedByDescending { it.amountMinor }
                CategoryExpenseTotal(
                    categoryId = rootId,
                    categoryName = root.name,
                    colorArgb = root.colorArgb,
                    amountMinor = pairs.sumOf { it.first.amountMinor },
                    directMinor = pairs.filter { it.second.id == rootId }.sumOf { it.first.amountMinor },
                    children = parts
                )
            }
            .sortedByDescending { it.amountMinor }
    }

    private fun expenseFlowSeries(
        period: AnalyticsPeriod,
        fromInclusive: LocalDate,
        toInclusive: LocalDate,
        expenses: List<Operation>
    ): List<ExpenseFlowPoint> {
        return when (period) {
            AnalyticsPeriod.WEEK, AnalyticsPeriod.MONTH ->
                dailyFlow(fromInclusive, toInclusive, expenses)
            AnalyticsPeriod.CUSTOM -> {
                val days = ChronoUnit.DAYS.between(fromInclusive, toInclusive) + 1
                if (days <= CUSTOM_DAILY_FLOW_MAX_DAYS) {
                    dailyFlow(fromInclusive, toInclusive, expenses)
                } else {
                    blockFlow(fromInclusive, toInclusive, expenses)
                }
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

    private fun dailyFlow(
        fromInclusive: LocalDate,
        toInclusive: LocalDate,
        operations: List<Operation>
    ): List<ExpenseFlowPoint> {
        val byDay = operations.groupBy { it.date }.mapValues { (_, ops) ->
            ops.sumOf { it.amountMinor }
        }
        return generateSequence(fromInclusive) { date ->
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

    /**
     * One point per block of [CUSTOM_BLOCK_DAYS] days counted from the start of the range (the
     * last block may be shorter). Windows of equal length get equal numbers of blocks, so the
     * blocks of a window and of the one before it pair up one to one.
     */
    private fun blockFlow(
        fromInclusive: LocalDate,
        toInclusive: LocalDate,
        operations: List<Operation>
    ): List<ExpenseFlowPoint> {
        val totalDays = ChronoUnit.DAYS.between(fromInclusive, toInclusive) + 1
        val blockCount = ((totalDays + CUSTOM_BLOCK_DAYS - 1) / CUSTOM_BLOCK_DAYS).toInt()
        val amounts = LongArray(blockCount)
        operations.forEach { operation ->
            val block = (ChronoUnit.DAYS.between(fromInclusive, operation.date) / CUSTOM_BLOCK_DAYS).toInt()
            if (block in 0 until blockCount) amounts[block] += operation.amountMinor
        }
        return (0 until blockCount).map { block ->
            val start = fromInclusive.plusDays(block * CUSTOM_BLOCK_DAYS)
            ExpenseFlowPoint(
                bucketStart = start,
                label = start.format(DAY_FLOW_LABEL),
                amountMinor = amounts[block]
            )
        }
    }

    private fun periodBounds(
        period: AnalyticsPeriod,
        asOf: LocalDate,
        window: DateWindow?
    ): Pair<LocalDate, LocalDate> {
        return when (period) {
            AnalyticsPeriod.CUSTOM -> {
                require(window != null) { "a custom period needs a window" }
                window.from to window.to
            }
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

    /** Goals and debts for the analytics: movement inside the period and the balances now. */
    suspend fun savingsAndDebts(
        period: AnalyticsPeriod,
        asOf: LocalDate,
        window: DateWindow? = null
    ): SavingsDebtsSummary {
        val (from, to) = periodBounds(period, asOf, window)
        fun inPeriod(date: LocalDate) = !date.isBefore(from) && !date.isAfter(to)

        val goalData = goalRepository.getAll()
        val goalEntries = goalData.entries.filter { inPeriod(it.date) }
        val deposits = goalEntries.filter { it.amountMinor > 0 }
        val goalNames = goalData.goals.associate { it.id to it.name }
        val fakeOperations = deposits.map {
            Operation(
                id = it.id,
                kind = OperationKind.EXPENSE,
                amountMinor = it.amountMinor,
                date = it.date,
                comment = null,
                accountId = it.accountId,
                categoryId = null
            )
        }

        val debtData = debtRepository.getAll()
        val debtById = debtData.debts.associateBy { it.id }
        val entries = debtData.entries.filter { inPeriod(it.date) }
        fun sum(direction: DebtDirection, kind: DebtEntryKind) = entries
            .filter { it.kind == kind && debtById[it.debtId]?.direction == direction }
            .sumOf { it.amountMinor }
        val summaries = debts()
        fun byPerson(direction: DebtDirection) = summaries
            .filter { it.debt.direction == direction && it.remainingMinor > 0 }
            .map { NamedAmount(it.debt.id, it.debt.person, it.remainingMinor) }
            .sortedByDescending { it.amountMinor }

        return SavingsDebtsSummary(
            fromInclusive = from,
            toInclusive = to,
            goalsDepositedMinor = deposits.sumOf { it.amountMinor },
            goalsWithdrawnMinor = goalEntries.filter { it.amountMinor < 0 }.sumOf { -it.amountMinor },
            depositedByGoal = deposits.groupBy { it.goalId }
                .map { (id, list) -> NamedAmount(id, goalNames[id].orEmpty(), list.sumOf { it.amountMinor }) }
                .sortedByDescending { it.amountMinor },
            goalFlow = expenseFlowSeries(period, from, to, fakeOperations),
            goalProgress = goals().filter { !it.goal.archived },
            lentMinor = sum(DebtDirection.LENT, DebtEntryKind.ISSUE),
            returnedToMeMinor = sum(DebtDirection.LENT, DebtEntryKind.REPAYMENT),
            borrowedMinor = sum(DebtDirection.BORROWED, DebtEntryKind.ISSUE),
            iRepaidMinor = sum(DebtDirection.BORROWED, DebtEntryKind.REPAYMENT),
            writtenOffLentMinor = sum(DebtDirection.LENT, DebtEntryKind.WRITE_OFF),
            forgivenMinor = sum(DebtDirection.BORROWED, DebtEntryKind.WRITE_OFF),
            owedToMeMinor = summaries.filter { it.debt.direction == DebtDirection.LENT }.sumOf { it.remainingMinor },
            iOweMinor = summaries.filter { it.debt.direction == DebtDirection.BORROWED }.sumOf { it.remainingMinor },
            owedToMeByPerson = byPerson(DebtDirection.LENT),
            iOweByPerson = byPerson(DebtDirection.BORROWED)
        )
    }

    // ---- Debts

    suspend fun debts(): List<DebtSummary> {
        val data = debtRepository.getAll()
        return data.debts.map { debt -> summarize(debt, data.entries.filter { it.debtId == debt.id }) }
    }

    suspend fun debtSummary(debtId: Long): DebtSummary? = debts().firstOrNull { it.debt.id == debtId }

    /** Entries of a debt, oldest first (the issue comes first). */
    suspend fun debtEntries(debtId: Long): List<DebtEntry> =
        debtRepository.getAll().entries.filter { it.debtId == debtId }
            .sortedWith(compareBy<DebtEntry> { it.date }.thenBy { it.id })

    /**
     * Starts a debt. With an [accountId] the money moves: lending takes it from the account,
     * borrowing puts it there. Without one the balances stay as they are.
     */
    suspend fun createDebt(
        person: String,
        direction: DebtDirection,
        amountMinor: Long,
        accountId: Long?,
        date: LocalDate,
        note: String?
    ): Long {
        val name = person.trim()
        require(name.isNotEmpty()) { "person required" }
        require(amountMinor > 0) { "amount must be positive" }
        requireAccountOrNull(accountId)
        val id = debtRepository.insertDebt(
            Debt(
                id = 0L,
                person = name,
                direction = direction,
                note = note?.trim().orEmpty(),
                createdAt = date
            )
        )
        debtRepository.insertEntry(
            DebtEntry(
                id = 0L,
                debtId = id,
                kind = DebtEntryKind.ISSUE,
                amountMinor = amountMinor,
                date = date,
                accountId = accountId
            )
        )
        return id
    }

    suspend fun updateDebt(debtId: Long, person: String, note: String?) {
        val debt = debtRepository.debt(debtId) ?: error("debt not found")
        val name = person.trim()
        require(name.isNotEmpty()) { "person required" }
        debtRepository.updateDebt(debt.copy(person = name, note = note?.trim().orEmpty()))
    }

    /** Records a payment toward the debt; at most what is still owed. */
    suspend fun repayDebt(debtId: Long, amountMinor: Long, accountId: Long?, date: LocalDate): Long {
        require(amountMinor > 0) { "amount must be positive" }
        requireAccountOrNull(accountId)
        val summary = debtSummary(debtId) ?: error("debt not found")
        if (amountMinor > summary.remainingMinor) throw DebtOverpayException()
        return debtRepository.insertEntry(
            DebtEntry(
                id = 0L,
                debtId = debtId,
                kind = DebtEntryKind.REPAYMENT,
                amountMinor = amountMinor,
                date = date,
                accountId = accountId
            )
        )
    }

    /**
     * Gives up the rest of the debt: money that was lent will not come back (an expense), or a
     * debt of the user was forgiven (an income). The account balances do not change, because the
     * money moved when the debt was made; the operation only shows the loss or the gain in the
     * analytics. The category is found by name and created when missing.
     */
    suspend fun writeOffDebt(
        debtId: Long,
        date: LocalDate,
        expenseCategoryName: String,
        incomeCategoryName: String
    ): Long {
        val summary = debtSummary(debtId) ?: error("debt not found")
        val remaining = summary.remainingMinor
        require(remaining > 0) { "nothing to write off" }
        val accounts = accountRepository.getAll()
        val issueAccount = debtEntries(debtId).firstOrNull { it.kind == DebtEntryKind.ISSUE }?.accountId
        val accountId = issueAccount?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull()?.id
            ?: error("no account")
        val lent = summary.debt.direction == DebtDirection.LENT
        val kind = if (lent) CategoryKind.EXPENSE else CategoryKind.INCOME
        val name = (if (lent) expenseCategoryName else incomeCategoryName).trim()
        ensureCategoriesSeeded()
        val categoryId = categoryRepository.getAll().firstOrNull { it.kind == kind && it.name == name }?.id
            ?: createCategory(
                name = name,
                kind = kind,
                colorArgb = if (lent) 0xFF6D4C41.toInt() else 0xFF2E7D32.toInt(),
                iconKey = if (lent) "bank" else "charity"
            )
        val comment = summary.debt.person
        val operationId = if (lent) {
            addExpense(accountId, categoryId, remaining, date, comment)
        } else {
            addIncome(accountId, categoryId, remaining, date, comment)
        }
        return debtRepository.insertEntry(
            DebtEntry(
                id = 0L,
                debtId = debtId,
                kind = DebtEntryKind.WRITE_OFF,
                amountMinor = remaining,
                date = date,
                accountId = accountId,
                operationId = operationId
            )
        )
    }

    /** Undoes a payment or a write-off. The issue itself cannot be undone; delete the debt instead. */
    suspend fun deleteDebtPayment(entryId: Long) {
        val entry = debtRepository.getAll().entries.firstOrNull { it.id == entryId } ?: error("entry not found")
        require(entry.kind != DebtEntryKind.ISSUE) { "only payments can be undone" }
        debtRepository.deleteEntry(entryId)
        removeOperationIfPresent(entry.operationId)
    }

    /** Removes the debt; the money it moved goes back as if nothing had happened. */
    suspend fun deleteDebt(debtId: Long) {
        require(debtRepository.debt(debtId) != null) { "debt not found" }
        val operationIds = debtRepository.getAll().entries
            .filter { it.debtId == debtId }.mapNotNull { it.operationId }
        debtRepository.deleteDebt(debtId)
        operationIds.forEach { removeOperationIfPresent(it) }
    }

    private suspend fun removeOperationIfPresent(operationId: Long?) {
        if (operationId != null && operationRepository.getAll().any { it.id == operationId }) {
            operationRepository.delete(operationId)
        }
    }

    private fun summarize(debt: Debt, entries: List<DebtEntry>) = DebtSummary(
        debt = debt,
        issuedMinor = entries.filter { it.kind == DebtEntryKind.ISSUE }.sumOf { it.amountMinor },
        repaidMinor = entries.filter { it.kind == DebtEntryKind.REPAYMENT }.sumOf { it.amountMinor },
        writtenOffMinor = entries.filter { it.kind == DebtEntryKind.WRITE_OFF }.sumOf { it.amountMinor }
    )

    private suspend fun requireAccountOrNull(accountId: Long?) {
        if (accountId != null) {
            require(accountRepository.getAll().any { it.id == accountId }) { "account not found" }
        }
    }

    // ---- Goals

    suspend fun goals(): List<GoalSummary> {
        val data = goalRepository.getAll()
        val saved = data.entries.groupBy { it.goalId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        return data.goals.map { GoalSummary(it, saved[it.id] ?: 0L) }
    }

    suspend fun goalSummary(goalId: Long): GoalSummary? = goals().firstOrNull { it.goal.id == goalId }

    /** Entries of a goal, newest first. */
    suspend fun goalEntries(goalId: Long): List<GoalEntry> =
        goalRepository.getAll().entries.filter { it.goalId == goalId }
            .sortedWith(compareByDescending<GoalEntry> { it.date }.thenByDescending { it.id })

    suspend fun createGoal(name: String, targetMinor: Long, today: LocalDate): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name required" }
        require(targetMinor > 0) { "target must be positive" }
        return goalRepository.insertGoal(
            Goal(id = 0L, name = trimmed, targetMinor = targetMinor, createdAt = today)
        )
    }

    suspend fun updateGoal(goalId: Long, name: String, targetMinor: Long) {
        val goal = goalRepository.goal(goalId) ?: error("goal not found")
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name required" }
        require(targetMinor > 0) { "target must be positive" }
        goalRepository.updateGoal(goal.copy(name = trimmed, targetMinor = targetMinor))
    }

    suspend fun setGoalArchived(goalId: Long, archived: Boolean) {
        val goal = goalRepository.goal(goalId) ?: error("goal not found")
        goalRepository.updateGoal(goal.copy(archived = archived))
    }

    /** Removes the goal; what was saved in it goes back to the accounts it came from. */
    suspend fun deleteGoal(goalId: Long) {
        require(goalRepository.goal(goalId) != null) { "goal not found" }
        goalRepository.deleteGoal(goalId)
    }

    /** Moves money from an account into the goal; the account balance drops. */
    suspend fun depositToGoal(goalId: Long, accountId: Long, amountMinor: Long, date: LocalDate): Long {
        requireOpenGoal(goalId)
        require(amountMinor > 0) { "amount must be positive" }
        require(accountRepository.getAll().any { it.id == accountId }) { "account not found" }
        return goalRepository.insertEntry(
            GoalEntry(id = 0L, goalId = goalId, accountId = accountId, amountMinor = amountMinor, date = date)
        )
    }

    /**
     * Records [amountMinor] as income and puts it straight into the goal: the income shows in
     * the analytics, while the account balance stays as it was.
     */
    suspend fun depositToGoalAsIncome(
        goalId: Long,
        accountId: Long,
        categoryId: Long,
        amountMinor: Long,
        date: LocalDate,
        comment: String?
    ): Long {
        val goal = requireOpenGoal(goalId)
        val operationId = addIncome(
            accountId = accountId,
            categoryId = categoryId,
            amountMinor = amountMinor,
            date = date,
            comment = comment?.takeIf { it.isNotBlank() } ?: goal.name
        )
        return goalRepository.insertEntry(
            GoalEntry(
                id = 0L,
                goalId = goalId,
                accountId = accountId,
                amountMinor = amountMinor,
                date = date,
                operationId = operationId
            )
        )
    }

    /** Takes money out of the goal back to an account; at most what is saved. */
    suspend fun withdrawFromGoal(goalId: Long, accountId: Long, amountMinor: Long, date: LocalDate): Long {
        requireOpenGoal(goalId)
        require(amountMinor > 0) { "amount must be positive" }
        require(accountRepository.getAll().any { it.id == accountId }) { "account not found" }
        val saved = goalSummary(goalId)?.savedMinor ?: 0L
        if (amountMinor > saved) throw GoalFundsException()
        return goalRepository.insertEntry(
            GoalEntry(id = 0L, goalId = goalId, accountId = accountId, amountMinor = -amountMinor, date = date)
        )
    }

    /** Undoes one entry (and the income created with it). Refused if the goal would go negative. */
    suspend fun deleteGoalEntry(entryId: Long) {
        val entry = goalRepository.getAll().entries.firstOrNull { it.id == entryId } ?: error("entry not found")
        val saved = goalSummary(entry.goalId)?.savedMinor ?: 0L
        if (saved - entry.amountMinor < 0L) throw GoalFundsException()
        goalRepository.deleteEntry(entryId)
        entry.operationId?.let { id ->
            if (operationRepository.getAll().any { it.id == id }) operationRepository.delete(id)
        }
    }

    private suspend fun requireOpenGoal(goalId: Long): Goal {
        val goal = goalRepository.goal(goalId) ?: error("goal not found")
        require(!goal.archived) { "goal is archived" }
        return goal
    }

    private suspend fun balanceFor(account: Account): Long {
        var balance = account.openingBalanceMinor
        // Money put into a goal has left the account; money taken back (negative) returns.
        for (entry in goalRepository.getAll().entries) {
            if (entry.accountId == account.id) balance -= entry.amountMinor
        }
        // Loans: giving money out lowers the balance, getting it back raises it, and the reverse.
        val debtData = debtRepository.getAll()
        val directions = debtData.debts.associate { it.id to it.direction }
        for (entry in debtData.entries) {
            if (entry.accountId == account.id) {
                val direction = directions[entry.debtId] ?: continue
                balance += accountDelta(direction, entry)
            }
        }
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
        /** A custom window up to this long is charted per day, a longer one in blocks of days. */
        private const val CUSTOM_DAILY_FLOW_MAX_DAYS = 62L
        private const val CUSTOM_BLOCK_DAYS = 30L
    }
}
