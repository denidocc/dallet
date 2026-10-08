package com.dallet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dallet.app.ledger.AccountHasOperationsException
import com.dallet.app.ledger.CategoryHasOperationsException
import com.dallet.app.ledger.AccountBalance
import com.dallet.app.ui.categories.CategoryStyleOptions
import com.dallet.app.ledger.AnalyticsPeriod
import com.dallet.app.ledger.Category
import com.dallet.app.ledger.CategoryKind
import com.dallet.app.ledger.InvalidBackupException
import com.dallet.app.ledger.Ledger
import com.dallet.app.ledger.Money
import com.dallet.app.ledger.Operation
import com.dallet.app.ledger.OperationKind
import com.dallet.app.ledger.SameAccountTransferException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class AddOperationKind {
    EXPENSE,
    INCOME,
    TRANSFER
}

enum class HistoryKindUi {
    EXPENSE,
    INCOME,
    TRANSFER
}

data class HomeUiState(
    val loading: Boolean = true,
    val needsOnboarding: Boolean = false,
    val totalBalanceLabel: String = "0.00",
    val monthIncomeLabel: String = "0.00",
    val monthExpenseLabel: String = "0.00",
    val accountRows: List<AccountRowUi> = emptyList(),
    val recentExpenses: List<ExpenseRowUi> = emptyList(),
    val errorMessage: String? = null
)

data class AccountRowUi(
    val name: String,
    val balanceLabel: String
)

data class ExpenseRowUi(
    val id: Long,
    val title: String,
    val subtitle: String,
    val amountLabel: String,
    val iconKey: String = "other",
    val colorArgb: Int = 0xFF546E7A.toInt()
)

data class HistoryUiState(
    val rows: List<HistoryRowUi> = emptyList()
)

data class HistoryRowUi(
    val id: Long,
    val kind: HistoryKindUi,
    val kindLabel: String,
    val title: String,
    val subtitle: String,
    val amountLabel: String
)

data class OnboardingUiState(
    val cashInput: String = "",
    val cardInput: String = "",
    val submitting: Boolean = false,
    val errorMessage: String? = null
)

data class DataUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

data class SelectableOption(
    val id: Long,
    val label: String
)

data class AddExpenseUiState(
    val kind: AddOperationKind = AddOperationKind.EXPENSE,
    val accounts: List<SelectableOption> = emptyList(),
    val categories: List<SelectableOption> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedFromAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val amountInput: String = "",
    val dateInput: String = LocalDate.now().format(DATE_FORMAT),
    val commentInput: String = "",
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false
)

data class EditOperationUiState(
    val loading: Boolean = true,
    val operationId: Long = 0L,
    val kind: OperationKind = OperationKind.EXPENSE,
    val accounts: List<SelectableOption> = emptyList(),
    val categories: List<SelectableOption> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedFromAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val amountInput: String = "",
    val dateInput: String = "",
    val commentInput: String = "",
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false
)

data class AccountsUiState(
    val rows: List<AccountManageRowUi> = emptyList()
)

data class AccountManageRowUi(
    val id: Long,
    val name: String,
    val openingBalanceLabel: String,
    val balanceLabel: String
)

data class AccountEditorUiState(
    val isNew: Boolean = true,
    val accountId: Long? = null,
    val nameInput: String = "",
    val openingInput: String = "0.00",
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false
)

data class CategoriesUiState(
    val selectedKind: CategoryKind = CategoryKind.EXPENSE,
    val rows: List<CategoryManageRowUi> = emptyList()
)

data class CategoryManageRowUi(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val iconKey: String
)

data class CategoryEditorUiState(
    val isNew: Boolean = true,
    val categoryId: Long? = null,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val nameInput: String = "",
    val colorArgb: Int = 0xFFE65100.toInt(),
    val iconKey: String = "other",
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false
)

data class AnalyticsUiState(
    val period: AnalyticsPeriod = AnalyticsPeriod.MONTH,
    val incomeTotalLabel: String = "0.00",
    val expenseTotalLabel: String = "0.00",
    val rangeLabel: String = "",
    val categorySlices: List<AnalyticsCategorySliceUi> = emptyList(),
    val flowPoints: List<AnalyticsFlowPointUi> = emptyList()
)

data class AnalyticsCategorySliceUi(
    val categoryId: Long,
    val name: String,
    val colorArgb: Int,
    val amountLabel: String,
    val fraction: Float
)

data class AnalyticsFlowPointUi(
    val label: String,
    val amountLabel: String,
    val fraction: Float
)

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

class DalletViewModel(
    private val ledger: Ledger
) : ViewModel() {

    private val _home = MutableStateFlow(HomeUiState())
    val home: StateFlow<HomeUiState> = _home.asStateFlow()

    private val _onboarding = MutableStateFlow(OnboardingUiState())
    val onboarding: StateFlow<OnboardingUiState> = _onboarding.asStateFlow()

    private val _addExpense = MutableStateFlow(AddExpenseUiState())
    val addExpense: StateFlow<AddExpenseUiState> = _addExpense.asStateFlow()

    private val _history = MutableStateFlow(HistoryUiState())
    val history: StateFlow<HistoryUiState> = _history.asStateFlow()

    private val _edit = MutableStateFlow(EditOperationUiState())
    val edit: StateFlow<EditOperationUiState> = _edit.asStateFlow()

    private val _accounts = MutableStateFlow(AccountsUiState())
    val accounts: StateFlow<AccountsUiState> = _accounts.asStateFlow()

    private val _accountEditor = MutableStateFlow(AccountEditorUiState())
    val accountEditor: StateFlow<AccountEditorUiState> = _accountEditor.asStateFlow()

    private val _categories = MutableStateFlow(CategoriesUiState())
    val categories: StateFlow<CategoriesUiState> = _categories.asStateFlow()

    private val _categoryEditor = MutableStateFlow(CategoryEditorUiState())
    val categoryEditor: StateFlow<CategoryEditorUiState> = _categoryEditor.asStateFlow()

    private val _analytics = MutableStateFlow(AnalyticsUiState())
    val analytics: StateFlow<AnalyticsUiState> = _analytics.asStateFlow()

    private val _data = MutableStateFlow(DataUiState())
    val data: StateFlow<DataUiState> = _data.asStateFlow()

    init {
        refreshHome()
    }

    fun consumeDataMessage() {
        _data.update { it.copy(message = null, isError = false) }
    }

    fun exportBackup(write: suspend (String) -> Unit) {
        viewModelScope.launch {
            _data.value = DataUiState(busy = true)
            try {
                write(ledger.exportBackup())
                _data.value = DataUiState(message = "Резервная копия сохранена")
            } catch (error: Exception) {
                _data.value = DataUiState(message = "Не удалось сохранить файл", isError = true)
            }
        }
    }

    fun importBackup(read: suspend () -> String) {
        viewModelScope.launch {
            _data.value = DataUiState(busy = true)
            try {
                ledger.importBackup(read())
                resetTransientStates()
                _data.value = DataUiState(message = "Данные восстановлены")
                refreshHome()
            } catch (error: InvalidBackupException) {
                _data.value = DataUiState(
                    message = error.message ?: "Файл не похож на резервную копию Dallet",
                    isError = true
                )
            } catch (error: Exception) {
                _data.value = DataUiState(message = "Не удалось прочитать файл", isError = true)
            }
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            _data.value = DataUiState(busy = true)
            try {
                ledger.resetAll()
                resetTransientStates()
                _data.value = DataUiState()
                refreshHome()
            } catch (error: Exception) {
                _data.value = DataUiState(message = "Не удалось сбросить данные", isError = true)
            }
        }
    }

    private fun resetTransientStates() {
        _onboarding.value = OnboardingUiState()
        _addExpense.value = AddExpenseUiState()
        _edit.value = EditOperationUiState()
        _accountEditor.value = AccountEditorUiState()
        _categoryEditor.value = CategoryEditorUiState()
    }

    fun refreshHome() {
        viewModelScope.launch {
            _home.update { it.copy(loading = true, errorMessage = null) }
            try {
                val needsOnboarding = !ledger.hasCompletedOnboarding()
                if (needsOnboarding) {
                    _home.value = HomeUiState(loading = false, needsOnboarding = true)
                } else {
                    val categoriesById = ledger.categories(CategoryKind.EXPENSE)
                        .associateBy { it.id }
                    val accountNames = ledger.accountBalances()
                        .associate { it.account.id to it.account.name }
                    val month = ledger.analytics(AnalyticsPeriod.MONTH, LocalDate.now())
                    _home.value = HomeUiState(
                        loading = false,
                        needsOnboarding = false,
                        totalBalanceLabel = Money.formatManat(ledger.totalBalanceMinor()),
                        monthIncomeLabel = Money.formatManat(month.incomeTotalMinor),
                        monthExpenseLabel = Money.formatManat(month.expenseTotalMinor),
                        accountRows = ledger.accountBalances().map { it.toRow() },
                        recentExpenses = ledger.recentExpenses(limit = 10).map { operation ->
                            operation.toExpenseRow(categoriesById, accountNames)
                        }
                    )
                    refreshHistoryInternal()
                    refreshAccountsInternal()
                    refreshCategoriesInternal(_categories.value.selectedKind)
                }
            } catch (error: Exception) {
                _home.update {
                    it.copy(loading = false, errorMessage = error.message ?: "Ошибка")
                }
            }
        }
    }

    fun refreshAccounts() {
        viewModelScope.launch {
            try {
                refreshAccountsInternal()
            } catch (_: Exception) {
                _accounts.value = AccountsUiState()
            }
        }
    }

    private suspend fun refreshAccountsInternal() {
        _accounts.value = AccountsUiState(
            rows = ledger.accountBalances().map { balance ->
                AccountManageRowUi(
                    id = balance.account.id,
                    name = balance.account.name,
                    openingBalanceLabel = Money.formatManat(balance.account.openingBalanceMinor),
                    balanceLabel = Money.formatManat(balance.balanceMinor)
                )
            }
        )
    }

    fun prepareNewAccount() {
        _accountEditor.value = AccountEditorUiState(isNew = true)
    }

    fun prepareEditAccount(accountId: Long) {
        viewModelScope.launch {
            val account = ledger.accountBalances().firstOrNull { it.account.id == accountId }?.account
            if (account == null) {
                _accountEditor.value = AccountEditorUiState(errorMessage = "Счёт не найден")
                return@launch
            }
            _accountEditor.value = AccountEditorUiState(
                isNew = false,
                accountId = account.id,
                nameInput = account.name,
                openingInput = Money.formatManat(account.openingBalanceMinor)
            )
        }
    }

    fun onAccountNameChange(value: String) {
        _accountEditor.update { it.copy(nameInput = value, errorMessage = null) }
    }

    fun onAccountOpeningChange(value: String) {
        _accountEditor.update { it.copy(openingInput = value, errorMessage = null) }
    }

    fun consumeAccountEditorDone() {
        _accountEditor.update { it.copy(done = false) }
    }

    fun saveAccount() {
        viewModelScope.launch {
            val state = _accountEditor.value
            _accountEditor.update { it.copy(submitting = true, errorMessage = null, done = false) }
            try {
                val opening = Money.parseManat(state.openingInput.ifBlank { "0" })
                if (state.isNew) {
                    ledger.createAccount(name = state.nameInput, openingBalanceMinor = opening)
                } else {
                    ledger.updateAccount(
                        accountId = state.accountId ?: error("id"),
                        name = state.nameInput,
                        openingBalanceMinor = opening
                    )
                }
                _accountEditor.update { it.copy(submitting = false, done = true) }
                refreshHome()
            } catch (error: Exception) {
                _accountEditor.update {
                    it.copy(submitting = false, errorMessage = "Проверьте название и сумму")
                }
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            val state = _accountEditor.value
            val accountId = state.accountId ?: return@launch
            _accountEditor.update { it.copy(submitting = true, errorMessage = null, done = false) }
            try {
                ledger.deleteAccount(accountId)
                _accountEditor.update { it.copy(submitting = false, done = true) }
                refreshHome()
            } catch (error: AccountHasOperationsException) {
                _accountEditor.update {
                    it.copy(submitting = false, errorMessage = error.message)
                }
            } catch (error: Exception) {
                _accountEditor.update {
                    it.copy(submitting = false, errorMessage = "Не удалось удалить")
                }
            }
        }
    }

    fun refreshCategories() {
        viewModelScope.launch {
            try {
                refreshCategoriesInternal(_categories.value.selectedKind)
            } catch (_: Exception) {
                _categories.value = CategoriesUiState()
            }
        }
    }

    fun onCategoriesKindSelected(kind: CategoryKind) {
        viewModelScope.launch {
            refreshCategoriesInternal(kind)
        }
    }

    private suspend fun refreshCategoriesInternal(kind: CategoryKind) {
        _categories.value = CategoriesUiState(
            selectedKind = kind,
            rows = ledger.categories(kind).map { category ->
                CategoryManageRowUi(
                    id = category.id,
                    name = category.name,
                    colorArgb = category.colorArgb,
                    iconKey = category.iconKey
                )
            }
        )
    }

    fun prepareNewCategory() {
        val kind = _categories.value.selectedKind
        _categoryEditor.value = CategoryEditorUiState(
            isNew = true,
            kind = kind,
            colorArgb = CategoryStyleOptions.colors.first(),
            iconKey = CategoryStyleOptions.icons.first()
        )
    }

    fun prepareEditCategory(categoryId: Long) {
        viewModelScope.launch {
            val category = ledger.categories(CategoryKind.EXPENSE)
                .plus(ledger.categories(CategoryKind.INCOME))
                .firstOrNull { it.id == categoryId }
            if (category == null) {
                _categoryEditor.value = CategoryEditorUiState(errorMessage = "Категория не найдена")
                return@launch
            }
            _categoryEditor.value = CategoryEditorUiState(
                isNew = false,
                categoryId = category.id,
                kind = category.kind,
                nameInput = category.name,
                colorArgb = category.colorArgb,
                iconKey = category.iconKey
            )
        }
    }

    fun onCategoryNameChange(value: String) {
        _categoryEditor.update { it.copy(nameInput = value, errorMessage = null) }
    }

    fun onCategoryColorSelected(color: Int) {
        _categoryEditor.update { it.copy(colorArgb = color, errorMessage = null) }
    }

    fun onCategoryIconSelected(icon: String) {
        _categoryEditor.update { it.copy(iconKey = icon, errorMessage = null) }
    }

    fun consumeCategoryEditorDone() {
        _categoryEditor.update { it.copy(done = false) }
    }

    fun saveCategory() {
        viewModelScope.launch {
            val state = _categoryEditor.value
            _categoryEditor.update { it.copy(submitting = true, errorMessage = null, done = false) }
            try {
                if (state.isNew) {
                    ledger.createCategory(
                        name = state.nameInput,
                        kind = state.kind,
                        colorArgb = state.colorArgb,
                        iconKey = state.iconKey
                    )
                } else {
                    ledger.updateCategory(
                        categoryId = state.categoryId ?: error("id"),
                        name = state.nameInput,
                        colorArgb = state.colorArgb,
                        iconKey = state.iconKey
                    )
                }
                _categoryEditor.update { it.copy(submitting = false, done = true) }
                refreshHome()
            } catch (error: Exception) {
                _categoryEditor.update {
                    it.copy(submitting = false, errorMessage = "Проверьте название")
                }
            }
        }
    }

    fun deleteCategory() {
        viewModelScope.launch {
            val state = _categoryEditor.value
            val categoryId = state.categoryId ?: return@launch
            _categoryEditor.update { it.copy(submitting = true, errorMessage = null, done = false) }
            try {
                ledger.deleteCategory(categoryId)
                _categoryEditor.update { it.copy(submitting = false, done = true) }
                refreshHome()
            } catch (error: CategoryHasOperationsException) {
                _categoryEditor.update {
                    it.copy(submitting = false, errorMessage = error.message)
                }
            } catch (error: Exception) {
                _categoryEditor.update {
                    it.copy(submitting = false, errorMessage = "Не удалось удалить")
                }
            }
        }
    }

    fun refreshAnalytics() {
        loadAnalytics(_analytics.value.period)
    }

    fun onAnalyticsPeriodSelected(period: AnalyticsPeriod) {
        loadAnalytics(period)
    }

    private fun loadAnalytics(period: AnalyticsPeriod) {
        viewModelScope.launch {
            try {
                refreshAnalyticsInternal(period)
            } catch (_: Exception) {
                _analytics.value = AnalyticsUiState(period = period)
            }
        }
    }

    private suspend fun refreshAnalyticsInternal(period: AnalyticsPeriod) {
        val summary = ledger.analytics(period, LocalDate.now())
        val expenseTotal = summary.expenseTotalMinor
        val maxFlow = summary.expenseFlow.maxOfOrNull { it.amountMinor } ?: 0L
        _analytics.value = AnalyticsUiState(
            period = period,
            incomeTotalLabel = Money.formatManat(summary.incomeTotalMinor),
            expenseTotalLabel = Money.formatManat(summary.expenseTotalMinor),
            rangeLabel = "${summary.fromInclusive} — ${summary.toInclusive}",
            categorySlices = summary.expensesByCategory.map { row ->
                AnalyticsCategorySliceUi(
                    categoryId = row.categoryId,
                    name = row.categoryName,
                    colorArgb = row.colorArgb,
                    amountLabel = Money.formatManat(row.amountMinor),
                    fraction = if (expenseTotal > 0L) {
                        row.amountMinor.toFloat() / expenseTotal.toFloat()
                    } else {
                        0f
                    }
                )
            },
            flowPoints = summary.expenseFlow.map { point ->
                AnalyticsFlowPointUi(
                    label = point.label,
                    amountLabel = Money.formatManat(point.amountMinor),
                    fraction = if (maxFlow > 0L) {
                        point.amountMinor.toFloat() / maxFlow.toFloat()
                    } else {
                        0f
                    }
                )
            }
        )
    }

    fun refreshHistory() {
        viewModelScope.launch {
            try {
                refreshHistoryInternal()
            } catch (_: Exception) {
                _history.value = HistoryUiState()
            }
        }
    }

    private suspend fun refreshHistoryInternal() {
        val expenseCategories = ledger.categories(CategoryKind.EXPENSE).associate { it.id to it.name }
        val incomeCategories = ledger.categories(CategoryKind.INCOME).associate { it.id to it.name }
        val accountNames = ledger.accountBalances().associate { it.account.id to it.account.name }
        _history.value = HistoryUiState(
            rows = ledger.allOperations().map { op ->
                op.toHistoryRow(expenseCategories + incomeCategories, accountNames)
            }
        )
    }

    fun prepareAddExpense() {
        viewModelScope.launch { loadAddForm(kind = AddOperationKind.EXPENSE) }
    }

    fun onAddKindSelected(kind: AddOperationKind) {
        viewModelScope.launch { loadAddForm(kind = kind, keepSharedInputs = true) }
    }

    private suspend fun loadAddForm(
        kind: AddOperationKind,
        keepSharedInputs: Boolean = false
    ) {
        try {
            val previous = _addExpense.value
            val accounts = ledger.accountBalances().map {
                SelectableOption(it.account.id, it.account.name)
            }
            val categoryKind = when (kind) {
                AddOperationKind.EXPENSE -> CategoryKind.EXPENSE
                AddOperationKind.INCOME -> CategoryKind.INCOME
                AddOperationKind.TRANSFER -> null
            }
            val categories = categoryKind?.let { ledger.categories(it) }
                ?.map { SelectableOption(it.id, it.name) }
                .orEmpty()
            _addExpense.value = AddExpenseUiState(
                kind = kind,
                accounts = accounts,
                categories = categories,
                selectedAccountId = accounts.firstOrNull()?.id,
                selectedFromAccountId = accounts.firstOrNull()?.id,
                selectedToAccountId = accounts.drop(1).firstOrNull()?.id ?: accounts.firstOrNull()?.id,
                selectedCategoryId = categories.firstOrNull()?.id,
                amountInput = if (keepSharedInputs) previous.amountInput else "",
                dateInput = if (keepSharedInputs) previous.dateInput else LocalDate.now().format(DATE_FORMAT),
                commentInput = if (keepSharedInputs) previous.commentInput else ""
            )
        } catch (error: Exception) {
            _addExpense.update { it.copy(errorMessage = error.message ?: "Ошибка") }
        }
    }

    fun loadEditOperation(operationId: Long) {
        viewModelScope.launch {
            _edit.value = EditOperationUiState(loading = true, operationId = operationId)
            try {
                val operation = ledger.operation(operationId) ?: error("not found")
                val accounts = ledger.accountBalances().map {
                    SelectableOption(it.account.id, it.account.name)
                }
                val categoryKind = when (operation.kind) {
                    OperationKind.EXPENSE -> CategoryKind.EXPENSE
                    OperationKind.INCOME -> CategoryKind.INCOME
                    OperationKind.TRANSFER -> null
                }
                val categories = categoryKind?.let { ledger.categories(it) }
                    ?.map { SelectableOption(it.id, it.name) }
                    .orEmpty()
                _edit.value = EditOperationUiState(
                    loading = false,
                    operationId = operation.id,
                    kind = operation.kind,
                    accounts = accounts,
                    categories = categories,
                    selectedAccountId = operation.accountId ?: accounts.firstOrNull()?.id,
                    selectedFromAccountId = operation.fromAccountId ?: accounts.firstOrNull()?.id,
                    selectedToAccountId = operation.toAccountId
                        ?: accounts.drop(1).firstOrNull()?.id
                        ?: accounts.firstOrNull()?.id,
                    selectedCategoryId = operation.categoryId ?: categories.firstOrNull()?.id,
                    amountInput = Money.formatManat(operation.amountMinor),
                    dateInput = operation.date.format(DATE_FORMAT),
                    commentInput = operation.comment.orEmpty()
                )
            } catch (error: Exception) {
                _edit.update {
                    it.copy(loading = false, errorMessage = error.message ?: "Ошибка")
                }
            }
        }
    }

    fun onCashInputChange(value: String) {
        _onboarding.update { it.copy(cashInput = value, errorMessage = null) }
    }

    fun onCardInputChange(value: String) {
        _onboarding.update { it.copy(cardInput = value, errorMessage = null) }
    }

    fun submitOnboarding() {
        viewModelScope.launch {
            val cashText = _onboarding.value.cashInput
            val cardText = _onboarding.value.cardInput
            _onboarding.update { it.copy(submitting = true, errorMessage = null) }
            try {
                ledger.completeOnboarding(
                    cashOpeningMinor = Money.parseManat(cashText.ifBlank { "0" }),
                    cardOpeningMinor = Money.parseManat(cardText.ifBlank { "0" })
                )
                _onboarding.value = OnboardingUiState()
                refreshHome()
            } catch (error: Exception) {
                _onboarding.update {
                    it.copy(submitting = false, errorMessage = "Проверьте суммы. Пример: 10.50")
                }
            }
        }
    }

    fun onExpenseAccountSelected(id: Long) {
        _addExpense.update { it.copy(selectedAccountId = id, errorMessage = null) }
    }

    fun onFromAccountSelected(id: Long) {
        _addExpense.update { it.copy(selectedFromAccountId = id, errorMessage = null) }
    }

    fun onToAccountSelected(id: Long) {
        _addExpense.update { it.copy(selectedToAccountId = id, errorMessage = null) }
    }

    fun onExpenseCategorySelected(id: Long) {
        _addExpense.update { it.copy(selectedCategoryId = id, errorMessage = null) }
    }

    fun onExpenseAmountChange(value: String) {
        _addExpense.update { it.copy(amountInput = value, errorMessage = null) }
    }

    fun onExpenseDateChange(value: String) {
        _addExpense.update { it.copy(dateInput = value, errorMessage = null) }
    }

    fun onExpenseCommentChange(value: String) {
        _addExpense.update { it.copy(commentInput = value, errorMessage = null) }
    }

    fun consumeExpenseSaved() {
        _addExpense.update { it.copy(saved = false) }
    }

    fun submitExpense() {
        viewModelScope.launch {
            val state = _addExpense.value
            _addExpense.update { it.copy(submitting = true, errorMessage = null, saved = false) }
            try {
                val amount = Money.parseManat(state.amountInput)
                val date = LocalDate.parse(state.dateInput.trim(), DATE_FORMAT)
                when (state.kind) {
                    AddOperationKind.EXPENSE -> ledger.addExpense(
                        accountId = state.selectedAccountId ?: error("account"),
                        categoryId = state.selectedCategoryId ?: error("category"),
                        amountMinor = amount,
                        date = date,
                        comment = state.commentInput
                    )
                    AddOperationKind.INCOME -> ledger.addIncome(
                        accountId = state.selectedAccountId ?: error("account"),
                        categoryId = state.selectedCategoryId ?: error("category"),
                        amountMinor = amount,
                        date = date,
                        comment = state.commentInput
                    )
                    AddOperationKind.TRANSFER -> ledger.addTransfer(
                        fromAccountId = state.selectedFromAccountId ?: error("from"),
                        toAccountId = state.selectedToAccountId ?: error("to"),
                        amountMinor = amount,
                        date = date,
                        comment = state.commentInput
                    )
                }
                _addExpense.update {
                    it.copy(submitting = false, saved = true, amountInput = "", commentInput = "")
                }
                refreshHome()
            } catch (error: SameAccountTransferException) {
                _addExpense.update { it.copy(submitting = false, errorMessage = error.message) }
            } catch (error: DateTimeParseException) {
                _addExpense.update { it.copy(submitting = false, errorMessage = "Дата: ГГГГ-ММ-ДД") }
            } catch (error: Exception) {
                _addExpense.update {
                    it.copy(submitting = false, errorMessage = "Проверьте сумму и выбранные поля")
                }
            }
        }
    }

    fun onEditAccountSelected(id: Long) {
        _edit.update { it.copy(selectedAccountId = id, errorMessage = null) }
    }

    fun onEditFromAccountSelected(id: Long) {
        _edit.update { it.copy(selectedFromAccountId = id, errorMessage = null) }
    }

    fun onEditToAccountSelected(id: Long) {
        _edit.update { it.copy(selectedToAccountId = id, errorMessage = null) }
    }

    fun onEditCategorySelected(id: Long) {
        _edit.update { it.copy(selectedCategoryId = id, errorMessage = null) }
    }

    fun onEditAmountChange(value: String) {
        _edit.update { it.copy(amountInput = value, errorMessage = null) }
    }

    fun onEditDateChange(value: String) {
        _edit.update { it.copy(dateInput = value, errorMessage = null) }
    }

    fun onEditCommentChange(value: String) {
        _edit.update { it.copy(commentInput = value, errorMessage = null) }
    }

    fun consumeEditDone() {
        _edit.update { it.copy(done = false) }
    }

    fun saveEdit() {
        viewModelScope.launch {
            val state = _edit.value
            _edit.update { it.copy(submitting = true, errorMessage = null, done = false) }
            try {
                val amount = Money.parseManat(state.amountInput)
                val date = LocalDate.parse(state.dateInput.trim(), DATE_FORMAT)
                when (state.kind) {
                    OperationKind.EXPENSE -> ledger.updateExpense(
                        operationId = state.operationId,
                        accountId = state.selectedAccountId ?: error("account"),
                        categoryId = state.selectedCategoryId ?: error("category"),
                        amountMinor = amount,
                        date = date,
                        comment = state.commentInput
                    )
                    OperationKind.INCOME -> ledger.updateIncome(
                        operationId = state.operationId,
                        accountId = state.selectedAccountId ?: error("account"),
                        categoryId = state.selectedCategoryId ?: error("category"),
                        amountMinor = amount,
                        date = date,
                        comment = state.commentInput
                    )
                    OperationKind.TRANSFER -> ledger.updateTransfer(
                        operationId = state.operationId,
                        fromAccountId = state.selectedFromAccountId ?: error("from"),
                        toAccountId = state.selectedToAccountId ?: error("to"),
                        amountMinor = amount,
                        date = date,
                        comment = state.commentInput
                    )
                }
                _edit.update { it.copy(submitting = false, done = true) }
                refreshHome()
            } catch (error: SameAccountTransferException) {
                _edit.update { it.copy(submitting = false, errorMessage = error.message) }
            } catch (error: DateTimeParseException) {
                _edit.update { it.copy(submitting = false, errorMessage = "Дата: ГГГГ-ММ-ДД") }
            } catch (error: Exception) {
                _edit.update {
                    it.copy(submitting = false, errorMessage = "Не удалось сохранить")
                }
            }
        }
    }

    fun deleteEdit() {
        viewModelScope.launch {
            val state = _edit.value
            _edit.update { it.copy(submitting = true, errorMessage = null, done = false) }
            try {
                ledger.deleteOperation(state.operationId)
                _edit.update { it.copy(submitting = false, done = true) }
                refreshHome()
            } catch (error: Exception) {
                _edit.update {
                    it.copy(submitting = false, errorMessage = "Не удалось удалить")
                }
            }
        }
    }

    private fun AccountBalance.toRow(): AccountRowUi =
        AccountRowUi(name = account.name, balanceLabel = Money.formatManat(balanceMinor))

    private fun Operation.toExpenseRow(
        categories: Map<Long, Category>,
        accountNames: Map<Long, String>
    ): ExpenseRowUi {
        val category = categoryId?.let { categories[it] }
        val account = accountId?.let { accountNames[it] }.orEmpty()
        return ExpenseRowUi(
            id = id,
            title = category?.name?.ifBlank { null } ?: "Расход",
            subtitle = listOf(account, date.toString())
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            amountLabel = Money.formatManat(amountMinor),
            iconKey = category?.iconKey ?: "other",
            colorArgb = category?.colorArgb ?: 0xFF546E7A.toInt()
        )
    }

    private fun Operation.toHistoryRow(
        categoryNames: Map<Long, String>,
        accountNames: Map<Long, String>
    ): HistoryRowUi {
        val kindUi = when (kind) {
            OperationKind.EXPENSE -> HistoryKindUi.EXPENSE
            OperationKind.INCOME -> HistoryKindUi.INCOME
            OperationKind.TRANSFER -> HistoryKindUi.TRANSFER
        }
        val kindLabel = when (kind) {
            OperationKind.EXPENSE -> "Расход"
            OperationKind.INCOME -> "Доход"
            OperationKind.TRANSFER -> "Перевод"
        }
        val title = when (kind) {
            OperationKind.TRANSFER -> {
                val from = fromAccountId?.let { accountNames[it] }.orEmpty()
                val to = toAccountId?.let { accountNames[it] }.orEmpty()
                "$from → $to"
            }
            else -> categoryId?.let { categoryNames[it] }.orEmpty().ifBlank { kindLabel }
        }
        val accountPart = accountId?.let { accountNames[it] }.orEmpty()
        return HistoryRowUi(
            id = id,
            kind = kindUi,
            kindLabel = kindLabel,
            title = title,
            subtitle = listOf(accountPart, date.toString(), comment.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            amountLabel = Money.formatManat(amountMinor)
        )
    }

    companion object {
        fun factory(ledger: Ledger): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DalletViewModel(ledger) as T
                }
            }
    }
}
