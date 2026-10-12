package com.dallet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dallet.app.ledger.AccountHasOperationsException
import com.dallet.app.ledger.CategoryHasOperationsException
import com.dallet.app.AppStrings
import com.dallet.app.R
import com.dallet.app.ledger.AccountBalance
import com.dallet.app.ledger.BackupCodec
import com.dallet.app.ledger.BackupProblem
import com.dallet.app.ledger.BudgetStatus
import com.dallet.app.ledger.Frequency
import com.dallet.app.ledger.AccountTemplates
import com.dallet.app.ledger.Currency
import com.dallet.app.ledger.ExchangeRate
import com.dallet.app.ledger.RateMath
import com.dallet.app.ledger.NewAccount
import com.dallet.app.ledger.AccountPeriodTotal
import com.dallet.app.ui.categories.CategoryStyleOptions
import com.dallet.app.ledger.AnalyticsPeriod
import com.dallet.app.ledger.Category
import com.dallet.app.ledger.CategoryExpenseTotal
import com.dallet.app.ledger.CategoryKind
import com.dallet.app.ledger.Comparison
import com.dallet.app.ledger.ComparisonSide
import com.dallet.app.ledger.DateWindow
import com.dallet.app.ledger.ExpenseFlowPoint
import com.dallet.app.ledger.InvalidBackupException
import com.dallet.app.ledger.Ledger
import com.dallet.app.ledger.Money
import com.dallet.app.ledger.Operation
import com.dallet.app.ledger.HistoryPeriod
import com.dallet.app.ledger.OperationFilter
import com.dallet.app.ledger.OperationKind
import com.dallet.app.ledger.PurchaseExpenseLockedException
import com.dallet.app.ledger.categoryVsCategory
import com.dallet.app.ledger.periodVsPeriod
import com.dallet.app.ledger.SameAccountTransferException
import com.dallet.app.ledger.ReportCsv
import com.dallet.app.ledger.percentChange
import com.dallet.app.ledger.shiftAsOf
import com.dallet.app.security.PinLock
import com.dallet.app.ui.report.ReportPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class ReportFormat {
    CSV,
    PDF
}

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

data class DueItemUi(
    val ruleId: Long,
    val dueDate: String,
    val dateLabel: String,
    val title: String,
    val accountName: String,
    val amountInput: String,
    val isIncome: Boolean,
    val isFirst: Boolean
)

data class RecurringRuleUi(
    val id: Long,
    val title: String,
    val subtitle: String,
    val amountLabel: String,
    val isIncome: Boolean
)

data class RecurringUiState(
    val rules: List<RecurringRuleUi> = emptyList()
)

data class RecurringEditorUiState(
    val isNew: Boolean = true,
    val ruleId: Long? = null,
    val kind: OperationKind = OperationKind.EXPENSE,
    val accounts: List<SelectableOption> = emptyList(),
    val categories: List<SelectableOption> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val amountInput: String = "",
    val commentInput: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val startDateInput: String = LocalDate.now().toString(),
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false
)

private fun frequencyRes(frequency: Frequency): Int = when (frequency) {
    Frequency.WEEKLY -> R.string.freq_weekly
    Frequency.MONTHLY -> R.string.freq_monthly
    Frequency.YEARLY -> R.string.freq_yearly
}

enum class BudgetState {
    OK,
    NEAR,
    OVER
}

data class BudgetRowUi(
    val categoryId: Long,
    val name: String,
    val colorArgb: Int,
    val iconKey: String,
    val limitInput: String,
    val limitLabel: String,
    val spentLabel: String,
    val remainingLabel: String,
    val fraction: Float,
    val state: BudgetState
)

data class BudgetsUiState(
    val rows: List<BudgetRowUi> = emptyList(),
    val available: List<SelectableOption> = emptyList()
)

data class HomeUiState(
    val loading: Boolean = true,
    val needsOnboarding: Boolean = false,
    val totalBalanceLabel: String = "0.00",
    val monthIncomeLabel: String = "0.00",
    val monthExpenseLabel: String = "0.00",
    val dueItems: List<DueItemUi> = emptyList(),
    val budgetAlerts: List<BudgetRowUi> = emptyList(),
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
    val days: List<HistoryDayUi> = emptyList(),
    val query: String = "",
    /** False only when the ledger itself has no operations, as opposed to nothing being found. */
    val hasOperations: Boolean = false,
    val foundCount: Int = 0,
    val foundIncomeLabel: String = "0.00",
    val foundExpenseLabel: String = "0.00",
    val kind: OperationKind? = null,
    val selectedAccountIds: Set<Long> = emptySet(),
    val selectedCategoryIds: Set<Long> = emptySet(),
    val accountOptions: List<SelectableOption> = emptyList(),
    val categoryOptions: List<HistoryCategoryOption> = emptyList(),
    val period: HistoryPeriod = HistoryPeriod.ALL_TIME,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    /** The chosen custom dates as text, ready to show; null until a custom period is chosen. */
    val customPeriodLabel: String? = null
) {
    /** How many restrictions besides the text are on (shown on the Filters button). */
    val restrictionCount: Int
        get() = (if (kind != null) 1 else 0) + selectedAccountIds.size + selectedCategoryIds.size +
            (if (period != HistoryPeriod.ALL_TIME) 1 else 0)

    val isFiltered: Boolean get() = query.isNotBlank() || restrictionCount > 0
}

data class HistoryCategoryOption(
    val id: Long,
    val label: String,
    val kind: CategoryKind
)

/** One day of the history: a header with the day's totals and its operations. */
data class HistoryDayUi(
    val dateKey: Long,
    val title: String,
    val incomeLabel: String?,
    val expenseLabel: String?,
    val rows: List<HistoryRowUi>
)

data class HistoryRowUi(
    val id: Long,
    val kind: HistoryKindUi,
    val kindLabel: String,
    val title: String,
    val subtitle: String,
    val amountLabel: String
)

data class OnboardingAccountRowUi(
    val name: String,
    val enabled: Boolean,
    val openingInput: String = ""
)

data class OnboardingUiState(
    val currency: Currency = Currency.DEFAULT,
    val rows: List<OnboardingAccountRowUi> = AccountTemplates.defaults().map {
        OnboardingAccountRowUi(name = it.name, enabled = it.enabledByDefault)
    },
    val customName: String = "",
    val customOpening: String = "",
    val submitting: Boolean = false,
    val errorMessage: String? = null
)

/** One side of the comparison, ready to show. */
enum class ComparisonSideId { A, B }

data class ComparisonSideUi(
    val label: String,
    val periodLabel: String,
    /** Kind, accounts and categories of the side as one line; empty when it has none. */
    val restrictionsLabel: String,
    val days: Long,
    val incomeLabel: String,
    val expenseLabel: String,
    val averageIncomeLabel: String,
    val averageExpenseLabel: String,
    val isComplete: Boolean
)

/** A category row of the comparison; signs are -1, 0 or 1 and only drive the colour. */
data class ComparisonRowUi(
    val categoryId: Long,
    val name: String,
    val kind: CategoryKind,
    val aLabel: String,
    val bLabel: String,
    val diffLabel: String,
    val diffSign: Int,
    val changePercent: Int?
)

data class ComparisonUiState(
    val loaded: Boolean = false,
    val a: ComparisonSideUi? = null,
    val b: ComparisonSideUi? = null,
    val sheetA: FilterSheetData = FilterSheetData(),
    val sheetB: FilterSheetData = FilterSheetData(),
    val expenseDiffLabel: String = "",
    val expenseDiffSign: Int = 0,
    val expenseChangePercent: Int? = null,
    val incomeDiffLabel: String = "",
    val incomeDiffSign: Int = 0,
    val incomeChangePercent: Int? = null,
    val rows: List<ComparisonRowUi> = emptyList()
) {
    val lengthsDiffer: Boolean get() = a != null && b != null && a.days != b.days
}

/** What a backup file contains, shown before it replaces the current data. */
data class ImportPreview(
    val accounts: Int,
    val categories: Int,
    val operations: Int
)

data class DataUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val pendingImport: ImportPreview? = null
)

data class LockUiState(
    val locked: Boolean = false,
    val input: String = "",
    val errorMessage: String? = null
)

data class SecurityUiState(
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false
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
    val timeInput: String = currentTimeText(),
    val timeEdited: Boolean = false,
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
    val timeInput: String = "",
    val commentInput: String = "",
    /** Title of the purchase this expense came from; its amount is then read-only. */
    val amountLockedBy: String? = null,
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

enum class AnalyticsMode {
    EXPENSE,
    INCOME
}

data class AnalyticsUiState(
    val period: AnalyticsPeriod = AnalyticsPeriod.MONTH,
    val mode: AnalyticsMode = AnalyticsMode.EXPENSE,
    val incomeTotalLabel: String = "0.00",
    val expenseTotalLabel: String = "0.00",
    val rangeLabel: String = "",
    val categorySlices: List<AnalyticsCategorySliceUi> = emptyList(),
    val flowPoints: List<AnalyticsFlowPointUi> = emptyList(),
    val incomeSlices: List<AnalyticsCategorySliceUi> = emptyList(),
    val incomeFlowPoints: List<AnalyticsFlowPointUi> = emptyList(),
    val offset: Int = 0,
    val periodLabel: String = "",
    val isComplete: Boolean = true,
    val incomeChangePercent: Int? = null,
    val expenseChangePercent: Int? = null,
    val accountStats: List<AccountStatUi> = emptyList(),
    /** The dates picked for the custom period; kept while another period is shown. */
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val canGoForward: Boolean = false,
    /** The dates on screen and of the previous period, handed to the comparison. */
    val currentFrom: LocalDate? = null,
    val currentTo: LocalDate? = null,
    val previousFrom: LocalDate? = null,
    val previousTo: LocalDate? = null
) {
    /** The window on screen when the period is custom: the picked dates moved by [offset] windows. */
    val customWindow: DateWindow?
        get() {
            val from = customFrom
            val to = customTo
            return if (period == AnalyticsPeriod.CUSTOM && from != null && to != null) {
                DateWindow(from, to).shifted(offset.toLong())
            } else {
                null
            }
        }
}

data class AccountStatUi(
    val name: String,
    val incomeLabel: String,
    val expenseLabel: String,
    val incomeFraction: Float,
    val expenseFraction: Float
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
    val fraction: Float,
    val previousAmountLabel: String = "0.00",
    val previousFraction: Float = 0f
)

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun currentTimeText(): String = LocalTime.now().format(TIME_FORMAT)

private fun parseTimeOrNull(text: String): LocalTime? =
    text.trim().takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it, TIME_FORMAT) }

private fun kindRes(kind: OperationKind): Int = when (kind) {
    OperationKind.EXPENSE -> R.string.add_kind_expense
    OperationKind.INCOME -> R.string.add_kind_income
    OperationKind.TRANSFER -> R.string.add_kind_transfer
}

private fun Operation.dateTimeLabel(locale: Locale): String {
    val pattern = if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
    val day = date.format(DateTimeFormatter.ofPattern(pattern, locale))
    return day + (time?.let { " " + it.format(TIME_FORMAT) } ?: "")
}

class DalletViewModel(
    private val ledger: Ledger,
    private val pinLock: PinLock,
    private val strings: AppStrings,
    private val onDataChanged: () -> Unit = {}
) : ViewModel() {

    private val _currency = MutableStateFlow(Currency.DEFAULT)
    val currency: StateFlow<Currency> = _currency.asStateFlow()

    private val _budgets = MutableStateFlow(BudgetsUiState())
    val budgets: StateFlow<BudgetsUiState> = _budgets.asStateFlow()

    private val _budgetWarning = MutableStateFlow<String?>(null)
    val budgetWarning: StateFlow<String?> = _budgetWarning.asStateFlow()

    private val _rates = MutableStateFlow<List<ExchangeRate>>(emptyList())
    val rates: StateFlow<List<ExchangeRate>> = _rates.asStateFlow()

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

    private val _lock = MutableStateFlow(LockUiState(locked = pinLock.isEnabled))
    val lock: StateFlow<LockUiState> = _lock.asStateFlow()

    private val _security = MutableStateFlow(
        SecurityUiState(
            pinEnabled = pinLock.isEnabled,
            biometricEnabled = pinLock.biometricEnabled
        )
    )
    val security: StateFlow<SecurityUiState> = _security.asStateFlow()

    private var failedAttempts = 0
    private var blockedUntilMillis = 0L

    fun onLockDigit(digit: Char) {
        val current = _lock.value
        if (current.input.length >= PinLock.PIN_LENGTH) return
        if (System.currentTimeMillis() < blockedUntilMillis) {
            _lock.update { it.copy(errorMessage = strings.get(R.string.lock_too_many)) }
            return
        }
        val input = current.input + digit
        _lock.update { it.copy(input = input, errorMessage = null) }
        if (input.length < PinLock.PIN_LENGTH) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { pinLock.verify(input) }
            if (ok) {
                failedAttempts = 0
                _lock.value = LockUiState(locked = false)
            } else {
                failedAttempts++
                val tooMany = failedAttempts % MAX_ATTEMPTS == 0
                if (tooMany) blockedUntilMillis = System.currentTimeMillis() + BLOCK_MILLIS
                _lock.value = LockUiState(
                    locked = true,
                    errorMessage = if (tooMany) {
                        strings.get(R.string.lock_too_many_30)
                    } else {
                        strings.get(R.string.security_wrong)
                    }
                )
            }
        }
    }

    fun onLockBackspace() {
        _lock.update { it.copy(input = it.input.dropLast(1), errorMessage = null) }
    }

    fun unlockWithBiometric() {
        _lock.value = LockUiState(locked = false)
    }

    fun resetFromLock() {
        viewModelScope.launch {
            try {
                ledger.resetAll()
                pinLock.disable()
                failedAttempts = 0
                resetTransientStates()
                syncSecurity()
                _lock.value = LockUiState(locked = false)
                refreshHome()
            } catch (error: Exception) {
                _lock.update { it.copy(errorMessage = strings.get(R.string.err_reset_failed)) }
            }
        }
    }

    fun enablePin(pin: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { pinLock.setPin(pin) }
            syncSecurity()
        }
    }

    fun disablePin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { pinLock.verify(pin) }
            if (ok) {
                pinLock.disable()
                syncSecurity()
            }
            onResult(ok)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        pinLock.setBiometricEnabled(enabled)
        syncSecurity()
    }

    private fun syncSecurity() {
        _security.value = SecurityUiState(
            pinEnabled = pinLock.isEnabled,
            biometricEnabled = pinLock.biometricEnabled
        )
        onDataChanged()
    }

    fun exportBackup(write: suspend (String) -> Unit) {
        viewModelScope.launch {
            _data.value = DataUiState(busy = true)
            try {
                write(ledger.exportBackup())
                _data.value = DataUiState(message = strings.get(R.string.msg_backup_saved))
            } catch (error: Exception) {
                _data.value = DataUiState(message = strings.get(R.string.err_save_file), isError = true)
            }
        }
    }

    fun exportReport(format: ReportFormat, write: suspend (ByteArray) -> Unit) {
        viewModelScope.launch {
            _data.value = DataUiState(busy = true)
            try {
                val state = _analytics.value
                val today = LocalDate.now()
                val report = ledger.periodReport(
                    period = state.period,
                    asOf = shiftAsOf(state.period, today, state.offset.toLong()),
                    today = today,
                    window = state.customWindow
                )
                val currencyCode = ledger.mainCurrency().code
                val bytes = withContext(Dispatchers.Default) {
                    when (format) {
                        ReportFormat.CSV -> ReportCsv.encode(
                            rows = report.rows,
                            header = listOf(
                                strings.get(R.string.add_date_label),
                                strings.get(R.string.add_time_label),
                                strings.get(R.string.pdf_col_kind),
                                strings.get(R.string.add_account_label),
                                strings.get(R.string.add_category_label),
                                strings.get(R.string.add_amount_label),
                                strings.get(R.string.add_comment_label)
                            ),
                            kindLabel = { kind -> strings.get(kindRes(kind)) }
                        ).toByteArray(Charsets.UTF_8)
                        ReportFormat.PDF -> ReportPdf.render(
                            report = report,
                            title = state.periodLabel,
                            createdOn = today.toString(),
                            incompleteNote = if (state.isComplete) null else strings.get(R.string.analytics_incomplete),
                            currencyCode = currencyCode,
                            strings = strings
                        )
                    }
                }
                write(bytes)
                _data.value = DataUiState(message = strings.get(R.string.msg_report_saved))
            } catch (error: Exception) {
                _data.value = DataUiState(message = strings.get(R.string.err_save_report), isError = true)
            }
        }
    }

    private var pendingImportJson: String? = null

    /** Reads and checks the file, then waits for the user to confirm before replacing data. */
    fun importBackup(read: suspend () -> String) {
        viewModelScope.launch {
            _data.value = DataUiState(busy = true)
            try {
                val json = read()
                val backup = BackupCodec.decode(json)
                BackupCodec.validate(backup)
                pendingImportJson = json
                _data.value = DataUiState(
                    pendingImport = ImportPreview(
                        accounts = backup.accounts.size,
                        categories = backup.categories.size,
                        operations = backup.operations.size
                    )
                )
            } catch (error: InvalidBackupException) {
                _data.value = DataUiState(
                    message = strings.get(
                        if (error.problem == BackupProblem.TOO_NEW) R.string.err_backup_too_new
                        else R.string.err_not_backup
                    ),
                    isError = true
                )
            } catch (error: Exception) {
                _data.value = DataUiState(message = strings.get(R.string.err_read_file), isError = true)
            }
        }
    }

    fun cancelImport() {
        pendingImportJson = null
        _data.value = DataUiState()
    }

    fun confirmImport() {
        val json = pendingImportJson ?: return
        pendingImportJson = null
        viewModelScope.launch {
            _data.value = DataUiState(busy = true)
            try {
                ledger.importBackup(json)
                resetTransientStates()
                _data.value = DataUiState(message = strings.get(R.string.msg_data_restored))
                refreshHome()
            } catch (error: InvalidBackupException) {
                _data.value = DataUiState(
                    message = strings.get(
                        if (error.problem == BackupProblem.TOO_NEW) R.string.err_backup_too_new
                        else R.string.err_not_backup
                    ),
                    isError = true
                )
            } catch (error: Exception) {
                _data.value = DataUiState(message = strings.get(R.string.err_read_file), isError = true)
            }
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            _data.value = DataUiState(busy = true)
            try {
                ledger.resetAll()
                pinLock.disable()
                syncSecurity()
                resetTransientStates()
                _data.value = DataUiState()
                refreshHome()
            } catch (error: Exception) {
                _data.value = DataUiState(message = strings.get(R.string.err_reset_failed), isError = true)
            }
        }
    }

    private fun resetTransientStates() {
        _onboarding.value = OnboardingUiState()
        _addExpense.value = AddExpenseUiState()
        _edit.value = EditOperationUiState()
        _recurringEditor.value = RecurringEditorUiState()
        _accountEditor.value = AccountEditorUiState()
        _categoryEditor.value = CategoryEditorUiState()
    }

    fun consumeBudgetWarning() {
        _budgetWarning.value = null
    }

    fun refreshBudgets() {
        viewModelScope.launch {
            try {
                _budgets.value = budgetsStateOf(ledger.budgetStatuses(LocalDate.now()))
            } catch (_: Exception) {
                _budgets.value = BudgetsUiState()
            }
        }
    }

    fun saveBudget(categoryId: Long, input: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val limit = Money.parseManat(input)
                if (limit <= 0L) {
                    onResult(false)
                    return@launch
                }
                ledger.setBudget(categoryId, limit)
                _budgets.value = budgetsStateOf(ledger.budgetStatuses(LocalDate.now()))
                refreshHome()
                onResult(true)
            } catch (error: Exception) {
                onResult(false)
            }
        }
    }

    fun removeBudget(categoryId: Long) {
        viewModelScope.launch {
            try {
                ledger.setBudget(categoryId, null)
                _budgets.value = budgetsStateOf(ledger.budgetStatuses(LocalDate.now()))
                refreshHome()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun budgetsStateOf(statuses: List<BudgetStatus>): BudgetsUiState {
        val withBudget = statuses.map { it.category.id }.toSet()
        return BudgetsUiState(
            rows = statuses.map { it.toBudgetRow() },
            available = ledger.categories(CategoryKind.EXPENSE)
                .filter { it.id !in withBudget }
                .map { SelectableOption(it.id, it.name) }
        )
    }

    private fun BudgetStatus.toBudgetRow(): BudgetRowUi = BudgetRowUi(
        categoryId = category.id,
        name = category.name,
        colorArgb = category.colorArgb,
        iconKey = category.iconKey,
        limitInput = Money.formatManat(limitMinor),
        limitLabel = Money.formatManat(limitMinor),
        spentLabel = Money.formatManat(spentMinor),
        remainingLabel = Money.formatManat(Math.abs(remainingMinor)),
        fraction = fraction,
        state = when {
            isOver -> BudgetState.OVER
            isNear -> BudgetState.NEAR
            else -> BudgetState.OK
        }
    )

    private suspend fun warnIfBudgetTight(categoryId: Long, date: LocalDate) {
        val status = ledger.budgetStatus(categoryId, date) ?: return
        if (!status.isOver && !status.isNear) return
        val code = _currency.value.code
        _budgetWarning.value = strings.get(
            if (status.isOver) R.string.budget_warning_over else R.string.budget_warning_near,
            status.category.name,
            "${Money.formatManat(status.spentMinor)} $code",
            "${Money.formatManat(status.limitMinor)} $code"
        )
    }

    private val _recurring = MutableStateFlow(RecurringUiState())
    val recurring: StateFlow<RecurringUiState> = _recurring.asStateFlow()

    private val _recurringEditor = MutableStateFlow(RecurringEditorUiState())
    val recurringEditor: StateFlow<RecurringEditorUiState> = _recurringEditor.asStateFlow()

    private suspend fun categoryLookup(): Map<Long, Category> =
        (ledger.categories(CategoryKind.EXPENSE) + ledger.categories(CategoryKind.INCOME))
            .associateBy { it.id }

    private suspend fun accountOptions(): List<SelectableOption> =
        ledger.accountBalances().map { SelectableOption(it.account.id, it.account.name) }

    private suspend fun recurringCategoryOptions(kind: OperationKind): List<SelectableOption> =
        ledger.categories(
            if (kind == OperationKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        ).map { SelectableOption(it.id, it.name) }

    private suspend fun dueItemsUi(): List<DueItemUi> {
        val categories = categoryLookup()
        val accounts = ledger.accountBalances().associate { it.account.id to it.account.name }
        val format = DateTimeFormatter.ofPattern("d MMM yyyy", strings.locale)
        return ledger.dueItems(LocalDate.now()).map { item ->
            val rule = item.rule
            DueItemUi(
                ruleId = rule.id,
                dueDate = item.dueDate.toString(),
                dateLabel = item.dueDate.format(format),
                title = rule.comment ?: categories[rule.categoryId]?.name.orEmpty(),
                accountName = accounts[rule.accountId].orEmpty(),
                amountInput = Money.formatManat(rule.amountMinor),
                isIncome = rule.kind == OperationKind.INCOME,
                isFirst = item.isFirst
            )
        }
    }

    private suspend fun recurringUiState(): RecurringUiState {
        val categories = categoryLookup()
        val accounts = ledger.accountBalances().associate { it.account.id to it.account.name }
        val format = DateTimeFormatter.ofPattern("d MMM yyyy", strings.locale)
        return RecurringUiState(
            rules = ledger.recurringRules().map { rule ->
                RecurringRuleUi(
                    id = rule.id,
                    title = rule.comment ?: categories[rule.categoryId]?.name.orEmpty(),
                    subtitle = listOf(
                        accounts[rule.accountId].orEmpty(),
                        strings.get(frequencyRes(rule.frequency)),
                        strings.get(R.string.recurring_next, rule.nextDue.format(format))
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    amountLabel = Money.formatManat(rule.amountMinor),
                    isIncome = rule.kind == OperationKind.INCOME
                )
            }
        )
    }

    fun refreshRecurring() {
        viewModelScope.launch {
            try {
                _recurring.value = recurringUiState()
            } catch (_: Exception) {
                _recurring.value = RecurringUiState()
            }
        }
    }

    fun confirmDue(ruleId: Long, dueDate: String, amountInput: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val date = LocalDate.parse(dueDate)
                val rule = ledger.recurringRules().firstOrNull { it.id == ruleId }
                ledger.confirmDue(ruleId, date, Money.parseManat(amountInput))
                refreshHome()
                refreshRecurring()
                if (rule != null && rule.kind == OperationKind.EXPENSE) {
                    warnIfBudgetTight(rule.categoryId, date)
                }
                onResult(true)
            } catch (error: Exception) {
                onResult(false)
            }
        }
    }

    fun skipDue(ruleId: Long, dueDate: String) {
        viewModelScope.launch {
            try {
                ledger.skipDue(ruleId, LocalDate.parse(dueDate))
                refreshHome()
                refreshRecurring()
            } catch (_: Exception) {
            }
        }
    }

    fun prepareNewRecurring() {
        viewModelScope.launch {
            val accounts = accountOptions()
            val categories = recurringCategoryOptions(OperationKind.EXPENSE)
            _recurringEditor.value = RecurringEditorUiState(
                accounts = accounts,
                categories = categories,
                selectedAccountId = accounts.firstOrNull()?.id,
                selectedCategoryId = categories.firstOrNull()?.id
            )
        }
    }

    fun prepareEditRecurring(id: Long) {
        viewModelScope.launch {
            val rule = ledger.recurringRules().firstOrNull { it.id == id } ?: return@launch
            _recurringEditor.value = RecurringEditorUiState(
                isNew = false,
                ruleId = id,
                kind = rule.kind,
                accounts = accountOptions(),
                categories = recurringCategoryOptions(rule.kind),
                selectedAccountId = rule.accountId,
                selectedCategoryId = rule.categoryId,
                amountInput = Money.formatManat(rule.amountMinor),
                commentInput = rule.comment.orEmpty(),
                frequency = rule.frequency,
                startDateInput = rule.startDate.toString()
            )
        }
    }

    fun onRecurringKindSelected(kind: OperationKind) {
        viewModelScope.launch {
            val categories = recurringCategoryOptions(kind)
            _recurringEditor.update {
                it.copy(
                    kind = kind,
                    categories = categories,
                    selectedCategoryId = categories.firstOrNull()?.id,
                    errorMessage = null
                )
            }
        }
    }

    fun onRecurringAccountSelected(id: Long) {
        _recurringEditor.update { it.copy(selectedAccountId = id, errorMessage = null) }
    }

    fun onRecurringCategorySelected(id: Long) {
        _recurringEditor.update { it.copy(selectedCategoryId = id, errorMessage = null) }
    }

    fun onRecurringAmountChange(value: String) {
        _recurringEditor.update { it.copy(amountInput = value, errorMessage = null) }
    }

    fun onRecurringCommentChange(value: String) {
        _recurringEditor.update { it.copy(commentInput = value, errorMessage = null) }
    }

    fun onRecurringFrequencySelected(frequency: Frequency) {
        _recurringEditor.update { it.copy(frequency = frequency, errorMessage = null) }
    }

    fun onRecurringStartDateChange(value: String) {
        _recurringEditor.update { it.copy(startDateInput = value, errorMessage = null) }
    }

    fun saveRecurring() {
        viewModelScope.launch {
            val state = _recurringEditor.value
            _recurringEditor.update { it.copy(submitting = true, errorMessage = null) }
            try {
                val amount = Money.parseManat(state.amountInput)
                val start = LocalDate.parse(state.startDateInput.trim())
                val accountId = state.selectedAccountId ?: error("account")
                val categoryId = state.selectedCategoryId ?: error("category")
                val ruleId = state.ruleId
                if (ruleId == null) {
                    ledger.addRecurring(
                        state.kind, accountId, categoryId, amount,
                        state.commentInput, state.frequency, start
                    )
                } else {
                    ledger.updateRecurring(
                        ruleId, state.kind, accountId, categoryId, amount,
                        state.commentInput, state.frequency, start
                    )
                }
                _recurringEditor.update { it.copy(submitting = false, done = true) }
                refreshRecurring()
                refreshHome()
            } catch (error: DateTimeParseException) {
                _recurringEditor.update {
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_check_datetime))
                }
            } catch (error: Exception) {
                _recurringEditor.update {
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_check_amount_fields))
                }
            }
        }
    }

    fun deleteRecurring() {
        viewModelScope.launch {
            val id = _recurringEditor.value.ruleId ?: return@launch
            try {
                ledger.deleteRecurring(id)
                _recurringEditor.update { it.copy(done = true) }
                refreshRecurring()
                refreshHome()
            } catch (_: Exception) {
                _recurringEditor.update {
                    it.copy(errorMessage = strings.get(R.string.err_delete_failed))
                }
            }
        }
    }

    fun consumeRecurringEditorDone() {
        _recurringEditor.update { it.copy(done = false) }
    }

    fun refreshRates() {
        viewModelScope.launch { _rates.value = ledger.rates() }
    }

    fun saveRates(inputs: Map<Currency, String>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val parsed = mutableMapOf<Currency, Long>()
            for ((currency, text) in inputs) {
                if (text.isBlank()) continue
                val scaled = RateMath.parse(text)
                if (scaled == null) {
                    onResult(false)
                    return@launch
                }
                parsed[currency] = scaled
            }
            try {
                ledger.saveRates(parsed, LocalDate.now())
                _rates.value = ledger.rates()
                onResult(true)
            } catch (error: Exception) {
                onResult(false)
            }
        }
    }

    fun setMainCurrency(currency: Currency) {
        viewModelScope.launch {
            ledger.setMainCurrency(currency)
            _currency.value = currency
            _rates.value = emptyList()
        }
    }

    fun refreshHome() {
        viewModelScope.launch {
            _home.update { it.copy(loading = true, errorMessage = null) }
            try {
                _currency.value = ledger.mainCurrency()
                _rates.value = ledger.rates()
                val needsOnboarding = !ledger.hasCompletedOnboarding()
                if (needsOnboarding) {
                    _home.value = HomeUiState(loading = false, needsOnboarding = true)
                } else {
                    val categoriesById = ledger.categories(CategoryKind.EXPENSE)
                        .associateBy { it.id }
                    val accountNames = ledger.accountBalances()
                        .associate { it.account.id to it.account.name }
                    val month = ledger.analytics(AnalyticsPeriod.MONTH, LocalDate.now())
                    val budgetStatuses = ledger.budgetStatuses(LocalDate.now())
                    val dueUi = dueItemsUi()
                    _budgets.value = budgetsStateOf(budgetStatuses)
                    _home.value = HomeUiState(
                        loading = false,
                        needsOnboarding = false,
                        totalBalanceLabel = Money.formatManat(ledger.totalBalanceMinor()),
                        monthIncomeLabel = Money.formatManat(month.incomeTotalMinor),
                        monthExpenseLabel = Money.formatManat(month.expenseTotalMinor),
                        dueItems = dueUi,
                        budgetAlerts = budgetStatuses
                            .filter { it.isOver || it.isNear }
                            .map { it.toBudgetRow() },
                        accountRows = ledger.accountBalances().map { it.toRow() },
                        recentExpenses = ledger.recentExpenses(limit = 10).map { operation ->
                            operation.toExpenseRow(categoriesById, accountNames)
                        }
                    )
                    refreshHistoryInternal()
                    refreshAccountsInternal()
                    refreshCategoriesInternal(_categories.value.selectedKind)
                    onDataChanged()
                }
            } catch (error: Exception) {
                _home.update {
                    it.copy(loading = false, errorMessage = error.message ?: strings.get(R.string.err_generic))
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
                _accountEditor.value = AccountEditorUiState(errorMessage = strings.get(R.string.err_account_not_found))
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
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_check_name_amount))
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
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_account_has_ops))
                }
            } catch (error: Exception) {
                _accountEditor.update {
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_delete_failed))
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
                _categoryEditor.value = CategoryEditorUiState(errorMessage = strings.get(R.string.err_category_not_found))
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
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_check_name))
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
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_category_has_ops))
                }
            } catch (error: Exception) {
                _categoryEditor.update {
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_delete_failed))
                }
            }
        }
    }

    fun refreshAnalytics() {
        loadAnalytics(_analytics.value.period, _analytics.value.offset)
    }

    fun onAnalyticsPeriodSelected(period: AnalyticsPeriod) {
        // A custom period needs dates first; they come through onAnalyticsCustomPeriod.
        if (period == AnalyticsPeriod.CUSTOM) return
        loadAnalytics(period, 0)
    }

    fun onAnalyticsCustomPeriod(from: LocalDate, to: LocalDate) {
        if (to.isBefore(from)) return
        _analytics.update { it.copy(customFrom = from, customTo = to) }
        loadAnalytics(AnalyticsPeriod.CUSTOM, 0)
    }

    fun onAnalyticsStep(delta: Int) {
        val state = _analytics.value
        val offset = state.offset + delta
        if (delta > 0 && !state.canGoForward) return
        loadAnalytics(state.period, offset)
    }

    private fun loadAnalytics(period: AnalyticsPeriod, offset: Int) {
        viewModelScope.launch {
            try {
                refreshAnalyticsInternal(period, offset)
            } catch (_: Exception) {
                _analytics.update {
                    AnalyticsUiState(
                        period = period,
                        offset = offset,
                        customFrom = it.customFrom,
                        customTo = it.customTo
                    )
                }
            }
        }
    }

    fun onAnalyticsModeSelected(mode: AnalyticsMode) {
        _analytics.update { it.copy(mode = mode) }
    }

    private suspend fun refreshAnalyticsInternal(period: AnalyticsPeriod, offset: Int) {
        val today = LocalDate.now()
        val customFrom = _analytics.value.customFrom
        val customTo = _analytics.value.customTo
        val window = if (period == AnalyticsPeriod.CUSTOM && customFrom != null && customTo != null) {
            DateWindow(customFrom, customTo).shifted(offset.toLong())
        } else {
            null
        }
        if (period == AnalyticsPeriod.CUSTOM && window == null) return
        val result = ledger.analyticsComparison(
            period = period,
            asOf = shiftAsOf(period, today, offset.toLong()),
            today = today,
            window = window
        )
        val summary = result.current
        val previous = result.previous
        // Forward is possible while the next period has started; a calendar period may not pass today.
        val canGoForward = if (window != null) {
            !window.shifted(1).from.isAfter(today)
        } else {
            offset < 0
        }
        _analytics.value = AnalyticsUiState(
            period = period,
            customFrom = customFrom,
            customTo = customTo,
            canGoForward = canGoForward,
            currentFrom = summary.fromInclusive,
            currentTo = summary.toInclusive,
            previousFrom = previous.fromInclusive,
            previousTo = previous.toInclusive,
            mode = _analytics.value.mode,
            incomeTotalLabel = Money.formatManat(summary.incomeTotalMinor),
            expenseTotalLabel = Money.formatManat(summary.expenseTotalMinor),
            rangeLabel = "${summary.fromInclusive} — ${summary.toInclusive}",
            categorySlices = summary.expensesByCategory.toSlices(summary.expenseTotalMinor),
            flowPoints = summary.expenseFlow.toFlowPoints(previous.expenseFlow),
            incomeSlices = summary.incomesByCategory.toSlices(summary.incomeTotalMinor),
            incomeFlowPoints = summary.incomeFlow.toFlowPoints(previous.incomeFlow),
            offset = offset,
            periodLabel = periodLabel(period, summary.fromInclusive, summary.toInclusive),
            isComplete = result.currentIsComplete,
            incomeChangePercent = percentChange(
                summary.incomeTotalMinor,
                previous.incomeTotalMinor
            ),
            expenseChangePercent = percentChange(
                summary.expenseTotalMinor,
                previous.expenseTotalMinor
            ),
            accountStats = summary.byAccount.toAccountStats()
        )
    }

    private fun List<AccountPeriodTotal>.toAccountStats(): List<AccountStatUi> {
        val maxAmount = maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) } ?: 0L
        fun fractionOf(amount: Long): Float =
            if (maxAmount > 0L) amount.toFloat() / maxAmount.toFloat() else 0f
        return map { row ->
            AccountStatUi(
                name = row.accountName,
                incomeLabel = Money.formatManat(row.incomeMinor),
                expenseLabel = Money.formatManat(row.expenseMinor),
                incomeFraction = fractionOf(row.incomeMinor),
                expenseFraction = fractionOf(row.expenseMinor)
            )
        }
    }

    private fun periodLabel(period: AnalyticsPeriod, from: LocalDate, to: LocalDate): String {
        val ru = strings.locale
        return when (period) {
            AnalyticsPeriod.WEEK -> {
                val format = DateTimeFormatter.ofPattern("d MMM", ru)
                "${from.format(format)} — ${to.format(format)}"
            }
            AnalyticsPeriod.MONTH -> from.format(DateTimeFormatter.ofPattern("LLLL yyyy", ru))
                .replaceFirstChar { it.uppercase() }
            AnalyticsPeriod.YEAR -> from.year.toString()
            AnalyticsPeriod.CUSTOM -> {
                val format = DateTimeFormatter.ofPattern("d MMM yyyy", ru)
                if (from == to) from.format(format) else "${from.format(format)} — ${to.format(format)}"
            }
        }
    }

    private fun List<CategoryExpenseTotal>.toSlices(total: Long): List<AnalyticsCategorySliceUi> =
        map { row ->
            AnalyticsCategorySliceUi(
                categoryId = row.categoryId,
                name = row.categoryName,
                colorArgb = row.colorArgb,
                amountLabel = Money.formatManat(row.amountMinor),
                fraction = if (total > 0L) row.amountMinor.toFloat() / total.toFloat() else 0f
            )
        }

    private fun List<ExpenseFlowPoint>.toFlowPoints(
        previous: List<ExpenseFlowPoint>
    ): List<AnalyticsFlowPointUi> {
        val maxFlow = maxOf(
            maxOfOrNull { it.amountMinor } ?: 0L,
            previous.maxOfOrNull { it.amountMinor } ?: 0L
        )
        fun fractionOf(amount: Long): Float =
            if (maxFlow > 0L) amount.toFloat() / maxFlow.toFloat() else 0f
        return mapIndexed { index, point ->
            val previousAmount = previous.getOrNull(index)?.amountMinor ?: 0L
            AnalyticsFlowPointUi(
                label = point.label,
                amountLabel = Money.formatManat(point.amountMinor),
                fraction = fractionOf(point.amountMinor),
                previousAmountLabel = Money.formatManat(previousAmount),
                previousFraction = fractionOf(previousAmount)
            )
        }
    }

    private var comparisonA = OperationFilter()
    private var comparisonB = OperationFilter()
    private val _comparison = MutableStateFlow(ComparisonUiState())
    val comparison: StateFlow<ComparisonUiState> = _comparison.asStateFlow()

    /** Opens the comparison with side A = the period on the Analytics screen and B = the one before. */
    fun openComparison() {
        val state = _analytics.value
        comparisonA = periodFilter(state.currentFrom, state.currentTo, HistoryPeriod.THIS_MONTH)
        comparisonB = periodFilter(state.previousFrom, state.previousTo, HistoryPeriod.LAST_MONTH)
        _comparison.value = ComparisonUiState()
        refreshComparison()
    }

    private fun periodFilter(from: LocalDate?, to: LocalDate?, fallback: HistoryPeriod): OperationFilter =
        if (from != null && to != null) {
            OperationFilter(period = HistoryPeriod.CUSTOM, customFrom = from, customTo = to)
        } else {
            OperationFilter(period = fallback)
        }

    private fun comparisonFilter(side: ComparisonSideId): OperationFilter =
        if (side == ComparisonSideId.A) comparisonA else comparisonB

    private fun changeComparison(side: ComparisonSideId, change: (OperationFilter) -> OperationFilter) {
        val changed = change(comparisonFilter(side))
        if (side == ComparisonSideId.A) comparisonA = changed else comparisonB = changed
        refreshComparison()
    }

    private fun Set<Long>.toggled(id: Long): Set<Long> = if (id in this) this - id else this + id

    fun onComparisonKindSelected(side: ComparisonSideId, kind: OperationKind?) {
        viewModelScope.launch {
            val kept = categoriesKeptFor(kind, comparisonFilter(side).categoryIds)
            changeComparison(side) { it.copy(kind = kind, categoryIds = kept) }
        }
    }

    fun onComparisonAccountToggled(side: ComparisonSideId, accountId: Long) =
        changeComparison(side) { it.copy(accountIds = it.accountIds.toggled(accountId)) }

    fun onComparisonCategoryToggled(side: ComparisonSideId, categoryId: Long) =
        changeComparison(side) { it.copy(categoryIds = it.categoryIds.toggled(categoryId)) }

    /** Presets and "all time"; a custom period comes through [onComparisonCustomPeriod]. */
    fun onComparisonPeriodSelected(side: ComparisonSideId, period: HistoryPeriod) {
        if (period == HistoryPeriod.CUSTOM) return
        changeComparison(side) { it.copy(period = period) }
    }

    fun onComparisonCustomPeriod(side: ComparisonSideId, from: LocalDate, to: LocalDate) {
        if (to.isBefore(from)) return
        changeComparison(side) {
            it.copy(period = HistoryPeriod.CUSTOM, customFrom = from, customTo = to)
        }
    }

    fun clearComparisonSide(side: ComparisonSideId) = changeComparison(side) { OperationFilter() }

    fun applyPeriodVsPeriod() {
        val (a, b) = periodVsPeriod(comparisonA, comparisonB)
        comparisonA = a
        comparisonB = b
        refreshComparison()
    }

    fun applyCategoryVsCategory() {
        val (a, b) = categoryVsCategory(comparisonA, comparisonB)
        comparisonA = a
        comparisonB = b
        refreshComparison()
    }

    private fun refreshComparison() {
        viewModelScope.launch {
            try {
                _comparison.value = buildComparisonUi()
            } catch (_: Exception) {
                _comparison.value = ComparisonUiState()
            }
        }
    }

    private suspend fun buildComparisonUi(): ComparisonUiState {
        val result = ledger.compare(comparisonA, comparisonB)
        val accountOptions = ledger.accountBalances().map { SelectableOption(it.account.id, it.account.name) }
        val categoryOptions = ledger.categories(CategoryKind.EXPENSE).map {
            HistoryCategoryOption(it.id, it.name, CategoryKind.EXPENSE)
        } + ledger.categories(CategoryKind.INCOME).map {
            HistoryCategoryOption(it.id, it.name, CategoryKind.INCOME)
        }
        return result.toUi(
            sheetA = filterSheetData(comparisonA, accountOptions, categoryOptions),
            sheetB = filterSheetData(comparisonB, accountOptions, categoryOptions),
            restrictionsA = restrictionsLabel(comparisonA, accountOptions, categoryOptions),
            restrictionsB = restrictionsLabel(comparisonB, accountOptions, categoryOptions)
        )
    }

    private fun filterSheetData(
        filter: OperationFilter,
        accountOptions: List<SelectableOption>,
        categoryOptions: List<HistoryCategoryOption>
    ) = FilterSheetData(
        kind = filter.kind,
        period = filter.period,
        customFrom = filter.customFrom,
        customTo = filter.customTo,
        customPeriodLabel = customPeriodLabel(filter.customFrom, filter.customTo),
        selectedAccountIds = filter.accountIds,
        selectedCategoryIds = filter.categoryIds,
        accountOptions = accountOptions,
        categoryOptions = categoryOptions
    )

    /** The restrictions of a side besides its dates, as one line; empty when there are none. */
    private fun restrictionsLabel(
        filter: OperationFilter,
        accountOptions: List<SelectableOption>,
        categoryOptions: List<HistoryCategoryOption>
    ): String {
        val parts = mutableListOf<String>()
        filter.kind?.let { parts += strings.get(kindRes(it)) }
        parts += accountOptions.filter { it.id in filter.accountIds }.map { it.label }
        parts += categoryOptions.filter { it.id in filter.categoryIds }.map { it.label }
        return parts.joinToString(", ")
    }

    private fun Comparison.toUi(
        sheetA: FilterSheetData,
        sheetB: FilterSheetData,
        restrictionsA: String,
        restrictionsB: String
    ): ComparisonUiState {
        fun sideUi(label: String, s: ComparisonSide, restrictions: String) = ComparisonSideUi(
            label = label,
            periodLabel = dateRangeLabel(s.from, s.to),
            restrictionsLabel = restrictions,
            days = s.days,
            incomeLabel = Money.formatManat(s.incomeMinor),
            expenseLabel = Money.formatManat(s.expenseMinor),
            averageIncomeLabel = Money.formatManat(s.averageIncomePerDayMinor),
            averageExpenseLabel = Money.formatManat(s.averageExpensePerDayMinor),
            isComplete = s.isComplete
        )
        return ComparisonUiState(
            loaded = true,
            a = sideUi("A", a, restrictionsA),
            b = sideUi("B", b, restrictionsB),
            sheetA = sheetA,
            sheetB = sheetB,
            expenseDiffLabel = signedMoney(expenseDiffMinor),
            expenseDiffSign = expenseDiffMinor.signum(),
            expenseChangePercent = expenseChangePercent,
            incomeDiffLabel = signedMoney(incomeDiffMinor),
            incomeDiffSign = incomeDiffMinor.signum(),
            incomeChangePercent = incomeChangePercent,
            rows = categories.map { row ->
                ComparisonRowUi(
                    categoryId = row.categoryId,
                    name = row.categoryName,
                    kind = row.kind,
                    aLabel = Money.formatManat(row.aMinor),
                    bLabel = Money.formatManat(row.bMinor),
                    diffLabel = signedMoney(row.diffMinor),
                    diffSign = row.diffMinor.signum(),
                    changePercent = row.changePercent
                )
            }
        )
    }

    private fun Long.signum(): Int = if (this > 0L) 1 else if (this < 0L) -1 else 0

    private fun signedMoney(minor: Long): String {
        val text = Money.formatManat(if (minor < 0L) -minor else minor)
        return when {
            minor > 0L -> "+$text"
            minor < 0L -> "−$text"
            else -> text
        }
    }

    private fun dateRangeLabel(from: LocalDate, to: LocalDate): String {
        val format = DateTimeFormatter.ofPattern("d MMM yyyy", strings.locale)
        return if (from == to) from.format(format) else "${from.format(format)} — ${to.format(format)}"
    }

    /** The filter stays while the app is open and is empty after a new start. */
    private var historyFilter = OperationFilter()
    private var historyJob: Job? = null

    fun refreshHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            try {
                refreshHistoryInternal()
            } catch (_: Exception) {
                _history.value = HistoryUiState(query = historyFilter.query)
            }
        }
    }

    fun onHistoryQueryChange(text: String) {
        historyFilter = historyFilter.copy(query = text)
        // The text field reads the query from the state, so update it before the slower refresh.
        _history.update { it.copy(query = text) }
        refreshHistory()
    }

    fun clearHistoryFilter() {
        historyFilter = OperationFilter()
        _history.update { it.copy(query = "") }
        refreshHistory()
    }

    /** Clears only the restrictions, keeping the typed text. */
    fun clearHistoryRestrictions() {
        historyFilter = historyFilter.copy(
            kind = null,
            accountIds = emptySet(),
            categoryIds = emptySet(),
            period = HistoryPeriod.ALL_TIME,
            customFrom = null,
            customTo = null
        )
        refreshHistory()
    }

    /** Presets and "all time"; a custom period comes through [onHistoryCustomPeriod]. */
    fun onHistoryPeriodSelected(period: HistoryPeriod) {
        if (period == HistoryPeriod.CUSTOM) return
        historyFilter = historyFilter.copy(period = period)
        refreshHistory()
    }

    fun onHistoryCustomPeriod(from: LocalDate, to: LocalDate) {
        if (to.isBefore(from)) return
        historyFilter = historyFilter.copy(
            period = HistoryPeriod.CUSTOM,
            customFrom = from,
            customTo = to
        )
        refreshHistory()
    }

    /** The chosen categories that still make sense once the kind of operation is [kind]. */
    private suspend fun categoriesKeptFor(kind: OperationKind?, chosen: Set<Long>): Set<Long> {
        if (kind == null) return chosen
        // Transfers have no category, and categories of the other kind can no longer match.
        val wanted = when (kind) {
            OperationKind.EXPENSE -> CategoryKind.EXPENSE
            OperationKind.INCOME -> CategoryKind.INCOME
            OperationKind.TRANSFER -> return emptySet()
        }
        return ledger.categories(wanted).map { it.id }.filter { it in chosen }.toSet()
    }

    fun onHistoryKindSelected(kind: OperationKind?) {
        viewModelScope.launch {
            val keptCategories = categoriesKeptFor(kind, historyFilter.categoryIds)
            historyFilter = historyFilter.copy(kind = kind, categoryIds = keptCategories)
            refreshHistory()
        }
    }

    fun onHistoryAccountToggled(accountId: Long) {
        val ids = historyFilter.accountIds
        historyFilter = historyFilter.copy(
            accountIds = if (accountId in ids) ids - accountId else ids + accountId
        )
        refreshHistory()
    }

    fun onHistoryCategoryToggled(categoryId: Long) {
        val ids = historyFilter.categoryIds
        historyFilter = historyFilter.copy(
            categoryIds = if (categoryId in ids) ids - categoryId else ids + categoryId
        )
        refreshHistory()
    }

    private suspend fun refreshHistoryInternal() {
        val filter = historyFilter
        val expenseCategories = ledger.categories(CategoryKind.EXPENSE).associate { it.id to it.name }
        val incomeCategories = ledger.categories(CategoryKind.INCOME).associate { it.id to it.name }
        val accountNames = ledger.accountBalances().associate { it.account.id to it.account.name }
        val categoryNames = expenseCategories + incomeCategories
        val today = LocalDate.now()
        val result = ledger.history(filter)
        val hasOperations = if (filter.isEmpty) result.count > 0 else ledger.allOperations().isNotEmpty()
        _history.value = HistoryUiState(
            days = result.days.map { day ->
                HistoryDayUi(
                    dateKey = day.date.toEpochDay(),
                    title = dayTitle(day.date, today),
                    incomeLabel = day.incomeMinor.takeIf { it > 0L }?.let { Money.formatManat(it) },
                    expenseLabel = day.expenseMinor.takeIf { it > 0L }?.let { Money.formatManat(it) },
                    rows = day.operations.map { op -> op.toHistoryRow(categoryNames, accountNames) }
                )
            },
            query = _history.value.query,
            hasOperations = hasOperations,
            foundCount = result.count,
            foundIncomeLabel = Money.formatManat(result.incomeMinor),
            foundExpenseLabel = Money.formatManat(result.expenseMinor),
            kind = filter.kind,
            period = filter.period,
            customFrom = filter.customFrom,
            customTo = filter.customTo,
            customPeriodLabel = customPeriodLabel(filter.customFrom, filter.customTo),
            selectedAccountIds = filter.accountIds,
            selectedCategoryIds = filter.categoryIds,
            accountOptions = ledger.accountBalances().map { SelectableOption(it.account.id, it.account.name) },
            categoryOptions = ledger.categories(CategoryKind.EXPENSE).map {
                HistoryCategoryOption(it.id, it.name, CategoryKind.EXPENSE)
            } + ledger.categories(CategoryKind.INCOME).map {
                HistoryCategoryOption(it.id, it.name, CategoryKind.INCOME)
            }
        )
    }

    private fun customPeriodLabel(from: LocalDate?, to: LocalDate?): String? {
        if (from == null || to == null) return null
        val format = DateTimeFormatter.ofPattern("d MMM yyyy", strings.locale)
        return if (from == to) from.format(format) else "${from.format(format)} – ${to.format(format)}"
    }

    private fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
        today -> strings.get(R.string.history_today)
        today.minusDays(1) -> strings.get(R.string.history_yesterday)
        else -> {
            val pattern = if (date.year == today.year) "d MMMM" else "d MMMM yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, strings.locale))
        }
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
                timeInput = if (keepSharedInputs) previous.timeInput else currentTimeText(),
                timeEdited = if (keepSharedInputs) previous.timeEdited else false,
                commentInput = if (keepSharedInputs) previous.commentInput else ""
            )
        } catch (error: Exception) {
            _addExpense.update { it.copy(errorMessage = error.message ?: strings.get(R.string.err_generic)) }
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
                    timeInput = operation.time?.format(TIME_FORMAT).orEmpty(),
                    commentInput = operation.comment.orEmpty(),
                    amountLockedBy = ledger.purchaseOf(operation.id)?.title
                )
            } catch (error: Exception) {
                _edit.update {
                    it.copy(loading = false, errorMessage = error.message ?: strings.get(R.string.err_generic))
                }
            }
        }
    }

    fun onCurrencySelected(currency: Currency) {
        _onboarding.update { it.copy(currency = currency, errorMessage = null) }
    }

    fun onTemplateToggled(name: String, enabled: Boolean) {
        _onboarding.update { state ->
            state.copy(
                rows = state.rows.map { if (it.name == name) it.copy(enabled = enabled) else it },
                errorMessage = null
            )
        }
    }

    fun onTemplateOpeningChange(name: String, value: String) {
        _onboarding.update { state ->
            state.copy(
                rows = state.rows.map { if (it.name == name) it.copy(openingInput = value) else it },
                errorMessage = null
            )
        }
    }

    fun onCustomNameChange(value: String) {
        _onboarding.update { it.copy(customName = value, errorMessage = null) }
    }

    fun onCustomOpeningChange(value: String) {
        _onboarding.update { it.copy(customOpening = value, errorMessage = null) }
    }

    fun submitOnboarding() {
        viewModelScope.launch {
            val state = _onboarding.value
            _onboarding.update { it.copy(submitting = true, errorMessage = null) }
            try {
                val chosen = state.rows.filter { it.enabled }
                    .map {
                        NewAccount(
                            templateNameRes(it.name)?.let { id -> strings.get(id) } ?: it.name,
                            Money.parseManat(it.openingInput.ifBlank { "0" })
                        )
                    } +
                    listOfNotNull(
                        state.customName.trim().takeIf { it.isNotEmpty() }?.let {
                            NewAccount(it, Money.parseManat(state.customOpening.ifBlank { "0" }))
                        }
                    )
                if (chosen.isEmpty()) {
                    _onboarding.update {
                        it.copy(submitting = false, errorMessage = strings.get(R.string.onboarding_pick_one))
                    }
                    return@launch
                }
                ledger.completeOnboarding(
                    chosen,
                    state.currency,
                    listOf(
                        R.string.cat_salary,
                        R.string.cat_side_job,
                        R.string.cat_food,
                        R.string.cat_transport,
                        R.string.cat_home,
                        R.string.cat_other
                    ).map { strings.get(it) }
                )
                _onboarding.value = OnboardingUiState()
                refreshHome()
            } catch (error: IllegalArgumentException) {
                _onboarding.update {
                    it.copy(
                        submitting = false,
                        errorMessage = strings.get(R.string.onboarding_error_names)
                    )
                }
            } catch (error: Exception) {
                _onboarding.update {
                    it.copy(submitting = false, errorMessage = strings.get(R.string.onboarding_error_amounts))
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

    fun onExpenseTimeChange(value: String) {
        _addExpense.update { it.copy(timeInput = value, timeEdited = true, errorMessage = null) }
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
                val time = if (state.timeEdited) {
                    parseTimeOrNull(state.timeInput)
                } else {
                    LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
                }
                when (state.kind) {
                    AddOperationKind.EXPENSE -> ledger.addExpense(
                        accountId = state.selectedAccountId ?: error("account"),
                        categoryId = state.selectedCategoryId ?: error("category"),
                        amountMinor = amount,
                        date = date,
                        time = time,
                        comment = state.commentInput
                    )
                    AddOperationKind.INCOME -> ledger.addIncome(
                        accountId = state.selectedAccountId ?: error("account"),
                        categoryId = state.selectedCategoryId ?: error("category"),
                        amountMinor = amount,
                        date = date,
                        time = time,
                        comment = state.commentInput
                    )
                    AddOperationKind.TRANSFER -> ledger.addTransfer(
                        fromAccountId = state.selectedFromAccountId ?: error("from"),
                        toAccountId = state.selectedToAccountId ?: error("to"),
                        amountMinor = amount,
                        date = date,
                        time = time,
                        comment = state.commentInput
                    )
                }
                _addExpense.update {
                    it.copy(submitting = false, saved = true, amountInput = "", commentInput = "")
                }
                if (state.kind == AddOperationKind.EXPENSE) {
                    state.selectedCategoryId?.let { warnIfBudgetTight(it, date) }
                }
                refreshHome()
            } catch (error: SameAccountTransferException) {
                _addExpense.update { it.copy(submitting = false, errorMessage = strings.get(R.string.err_same_account)) }
            } catch (error: DateTimeParseException) {
                _addExpense.update { it.copy(submitting = false, errorMessage = strings.get(R.string.err_check_datetime)) }
            } catch (error: Exception) {
                _addExpense.update {
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_check_amount_fields))
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

    fun onEditTimeChange(value: String) {
        _edit.update { it.copy(timeInput = value, errorMessage = null) }
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
                val time = parseTimeOrNull(state.timeInput)
                when (state.kind) {
                    OperationKind.EXPENSE -> ledger.updateExpense(
                        operationId = state.operationId,
                        accountId = state.selectedAccountId ?: error("account"),
                        categoryId = state.selectedCategoryId ?: error("category"),
                        amountMinor = amount,
                        date = date,
                        time = time,
                        comment = state.commentInput
                    )
                    OperationKind.INCOME -> ledger.updateIncome(
                        operationId = state.operationId,
                        accountId = state.selectedAccountId ?: error("account"),
                        categoryId = state.selectedCategoryId ?: error("category"),
                        amountMinor = amount,
                        date = date,
                        time = time,
                        comment = state.commentInput
                    )
                    OperationKind.TRANSFER -> ledger.updateTransfer(
                        operationId = state.operationId,
                        fromAccountId = state.selectedFromAccountId ?: error("from"),
                        toAccountId = state.selectedToAccountId ?: error("to"),
                        amountMinor = amount,
                        date = date,
                        time = time,
                        comment = state.commentInput
                    )
                }
                _edit.update { it.copy(submitting = false, done = true) }
                if (state.kind == OperationKind.EXPENSE) {
                    state.selectedCategoryId?.let { warnIfBudgetTight(it, date) }
                }
                refreshHome()
            } catch (error: SameAccountTransferException) {
                _edit.update { it.copy(submitting = false, errorMessage = strings.get(R.string.err_same_account)) }
            } catch (error: DateTimeParseException) {
                _edit.update { it.copy(submitting = false, errorMessage = strings.get(R.string.err_check_datetime)) }
            } catch (error: PurchaseExpenseLockedException) {
                _edit.update {
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_purchase_amount_locked))
                }
            } catch (error: Exception) {
                _edit.update {
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_save_failed))
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
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_delete_failed))
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
            title = category?.name?.ifBlank { null } ?: strings.get(R.string.add_kind_expense),
            subtitle = listOf(account, dateTimeLabel(strings.locale))
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
            OperationKind.EXPENSE -> strings.get(R.string.add_kind_expense)
            OperationKind.INCOME -> strings.get(R.string.add_kind_income)
            OperationKind.TRANSFER -> strings.get(R.string.add_kind_transfer)
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
            subtitle = listOf(
                accountPart,
                time?.format(TIME_FORMAT).orEmpty(),
                comment.orEmpty()
            )
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            amountLabel = Money.formatManat(amountMinor)
        )
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val BLOCK_MILLIS = 30_000L

        fun factory(
            ledger: Ledger,
            pinLock: PinLock,
            strings: AppStrings,
            onDataChanged: () -> Unit = {}
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DalletViewModel(ledger, pinLock, strings, onDataChanged) as T
                }
            }
    }
}
