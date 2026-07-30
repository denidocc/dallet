package com.dallet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dallet.app.ledger.AccountBalance
import com.dallet.app.ledger.CategoryKind
import com.dallet.app.ledger.Ledger
import com.dallet.app.ledger.Money
import com.dallet.app.ledger.Operation
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

data class HomeUiState(
    val loading: Boolean = true,
    val needsOnboarding: Boolean = false,
    val totalBalanceLabel: String = "0.00",
    val accountRows: List<AccountRowUi> = emptyList(),
    val recentExpenses: List<ExpenseRowUi> = emptyList(),
    val errorMessage: String? = null
)

data class AccountRowUi(
    val name: String,
    val balanceLabel: String
)

data class ExpenseRowUi(
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

    init {
        refreshHome()
    }

    fun refreshHome() {
        viewModelScope.launch {
            _home.update { it.copy(loading = true, errorMessage = null) }
            try {
                val needsOnboarding = !ledger.hasCompletedOnboarding()
                if (needsOnboarding) {
                    _home.value = HomeUiState(
                        loading = false,
                        needsOnboarding = true
                    )
                } else {
                    val categoryNames = ledger.categories(CategoryKind.EXPENSE)
                        .associate { it.id to it.name }
                    val accountNames = ledger.accountBalances()
                        .associate { it.account.id to it.account.name }
                    _home.value = HomeUiState(
                        loading = false,
                        needsOnboarding = false,
                        totalBalanceLabel = Money.formatManat(ledger.totalBalanceMinor()),
                        accountRows = ledger.accountBalances().map { it.toRow() },
                        recentExpenses = ledger.recentExpenses(limit = 10).map { operation ->
                            operation.toExpenseRow(categoryNames, accountNames)
                        }
                    )
                }
            } catch (error: Exception) {
                _home.update {
                    it.copy(loading = false, errorMessage = error.message ?: "Ошибка")
                }
            }
        }
    }

    fun prepareAddExpense() {
        viewModelScope.launch {
            loadAddForm(kind = AddOperationKind.EXPENSE)
        }
    }

    fun onAddKindSelected(kind: AddOperationKind) {
        viewModelScope.launch {
            loadAddForm(kind = kind, keepSharedInputs = true)
        }
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
            _addExpense.update {
                it.copy(errorMessage = error.message ?: "Ошибка")
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
                val cash = Money.parseManat(cashText.ifBlank { "0" })
                val card = Money.parseManat(cardText.ifBlank { "0" })
                ledger.completeOnboarding(
                    cashOpeningMinor = cash,
                    cardOpeningMinor = card
                )
                _onboarding.value = OnboardingUiState()
                refreshHome()
            } catch (error: Exception) {
                _onboarding.update {
                    it.copy(
                        submitting = false,
                        errorMessage = "Проверьте суммы. Пример: 10.50"
                    )
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
                    AddOperationKind.EXPENSE -> {
                        ledger.addExpense(
                            accountId = state.selectedAccountId ?: error("account"),
                            categoryId = state.selectedCategoryId ?: error("category"),
                            amountMinor = amount,
                            date = date,
                            comment = state.commentInput
                        )
                    }
                    AddOperationKind.INCOME -> {
                        ledger.addIncome(
                            accountId = state.selectedAccountId ?: error("account"),
                            categoryId = state.selectedCategoryId ?: error("category"),
                            amountMinor = amount,
                            date = date,
                            comment = state.commentInput
                        )
                    }
                    AddOperationKind.TRANSFER -> {
                        ledger.addTransfer(
                            fromAccountId = state.selectedFromAccountId ?: error("from"),
                            toAccountId = state.selectedToAccountId ?: error("to"),
                            amountMinor = amount,
                            date = date,
                            comment = state.commentInput
                        )
                    }
                }
                _addExpense.update {
                    it.copy(submitting = false, saved = true, amountInput = "", commentInput = "")
                }
                refreshHome()
            } catch (error: SameAccountTransferException) {
                _addExpense.update {
                    it.copy(submitting = false, errorMessage = error.message)
                }
            } catch (error: DateTimeParseException) {
                _addExpense.update {
                    it.copy(submitting = false, errorMessage = "Дата: ГГГГ-ММ-ДД")
                }
            } catch (error: Exception) {
                _addExpense.update {
                    it.copy(
                        submitting = false,
                        errorMessage = "Проверьте сумму и выбранные поля"
                    )
                }
            }
        }
    }

    private fun AccountBalance.toRow(): AccountRowUi =
        AccountRowUi(
            name = account.name,
            balanceLabel = Money.formatManat(balanceMinor)
        )

    private fun Operation.toExpenseRow(
        categoryNames: Map<Long, String>,
        accountNames: Map<Long, String>
    ): ExpenseRowUi {
        val category = categoryId?.let { categoryNames[it] }.orEmpty()
        val account = accountId?.let { accountNames[it] }.orEmpty()
        return ExpenseRowUi(
            title = category.ifBlank { "Расход" },
            subtitle = listOf(account, date.toString(), comment.orEmpty())
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
