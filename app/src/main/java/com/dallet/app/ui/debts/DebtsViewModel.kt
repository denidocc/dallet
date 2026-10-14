package com.dallet.app.ui.debts

import com.dallet.app.ui.common.OverdraftPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dallet.app.AppStrings
import com.dallet.app.R
import com.dallet.app.ledger.DebtDirection
import com.dallet.app.ledger.DebtEntryKind
import com.dallet.app.ledger.DebtOverpayException
import com.dallet.app.ledger.Ledger
import com.dallet.app.ledger.Money
import com.dallet.app.ui.SelectableOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DebtRowUi(
    val id: Long,
    val person: String,
    val direction: DebtDirection,
    val remainingLabel: String,
    val totalLabel: String,
    val percent: Int,
    val settled: Boolean
)

data class DebtsListUi(
    val loaded: Boolean = false,
    val owedToMe: List<DebtRowUi> = emptyList(),
    val iOwe: List<DebtRowUi> = emptyList(),
    val owedToMeTotalLabel: String = "0.00",
    val iOweTotalLabel: String = "0.00",
    val message: String? = null,
    val overdraft: OverdraftPrompt? = null
)

data class DebtEntryUi(
    val id: Long,
    val dateLabel: String,
    val amountLabel: String,
    val accountName: String?,
    val isIssue: Boolean,
    val isWriteOff: Boolean = false
)

data class DebtDetailUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val debtId: Long = 0L,
    val person: String = "",
    val note: String = "",
    val direction: DebtDirection = DebtDirection.LENT,
    val remainingLabel: String = "0.00",
    val totalLabel: String = "0.00",
    val remainingInput: String = "",
    val percent: Int = 0,
    val settled: Boolean = false,
    val entries: List<DebtEntryUi> = emptyList(),
    val accounts: List<SelectableOption> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false,
    val overdraft: OverdraftPrompt? = null
)

private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

class DebtsViewModel(
    private val ledger: Ledger,
    private val strings: AppStrings
) : ViewModel() {

    private val _list = MutableStateFlow(DebtsListUi())
    val list: StateFlow<DebtsListUi> = _list.asStateFlow()

    private val _detail = MutableStateFlow(DebtDetailUi())
    val detail: StateFlow<DebtDetailUi> = _detail.asStateFlow()

    private val _accounts = MutableStateFlow<List<SelectableOption>>(emptyList())

    /** Accounts for the "new debt" dialog on the list screen, with their balances. */
    val accounts: StateFlow<List<SelectableOption>> = _accounts.asStateFlow()

    fun refreshList() {
        viewModelScope.launch {
            val all = ledger.debts()
            fun row(summary: com.dallet.app.ledger.DebtSummary) = DebtRowUi(
                id = summary.debt.id,
                person = summary.debt.person,
                direction = summary.debt.direction,
                remainingLabel = Money.formatManat(summary.remainingMinor),
                totalLabel = Money.formatManat(summary.issuedMinor),
                percent = summary.percent,
                settled = summary.settled
            )
            fun rows(direction: DebtDirection) = all
                .filter { it.debt.direction == direction }
                .sortedWith(compareBy({ it.settled }, { it.debt.id }))
                .map { row(it) }
            fun total(direction: DebtDirection) = all
                .filter { it.debt.direction == direction }
                .sumOf { it.remainingMinor }
            _accounts.value = accountOptions()
            _list.update {
                it.copy(
                    loaded = true,
                    owedToMe = rows(DebtDirection.LENT),
                    iOwe = rows(DebtDirection.BORROWED),
                    owedToMeTotalLabel = Money.formatManat(total(DebtDirection.LENT)),
                    iOweTotalLabel = Money.formatManat(total(DebtDirection.BORROWED))
                )
            }
        }
    }

    private suspend fun accountOptions(): List<SelectableOption> =
        ledger.accountBalances().map {
            SelectableOption(it.account.id, it.account.name + " · " + Money.formatManat(it.balanceMinor))
        }

    private var pendingList: (suspend () -> Unit)? = null
    private var pendingDetail: (suspend () -> Unit)? = null

    fun createDebt(
        person: String,
        direction: DebtDirection,
        amountInput: String,
        accountId: Long?,
        note: String,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val amount = parseAmount(amountInput)
            if (person.isBlank() || amount == null || amount <= 0L) {
                _list.update { it.copy(message = strings.get(R.string.err_debt_input)) }
                return@launch
            }
            val perform: suspend () -> Unit = {
                try {
                    val id = ledger.createDebt(person, direction, amount, accountId, LocalDate.now(), note)
                    refreshList()
                    onCreated(id)
                } catch (error: Exception) {
                    _list.update { it.copy(message = strings.get(R.string.err_generic)) }
                }
            }
            // Lending takes money out of the account.
            val prompt = if (direction == DebtDirection.LENT) overdraftOf(accountId, amount) else null
            if (prompt == null) {
                perform()
            } else {
                pendingList = perform
                _list.update { it.copy(overdraft = prompt) }
            }
        }
    }

    fun confirmListOverdraft() {
        val action = pendingList
        pendingList = null
        _list.update { it.copy(overdraft = null) }
        if (action != null) viewModelScope.launch { action() }
    }

    fun cancelListOverdraft() {
        pendingList = null
        _list.update { it.copy(overdraft = null) }
    }

    private suspend fun overdraftOf(accountId: Long?, amountMinor: Long): OverdraftPrompt? {
        if (accountId == null) return null
        val balance = ledger.accountBalances().firstOrNull { it.account.id == accountId } ?: return null
        val after = balance.balanceMinor - amountMinor
        return if (after < 0L) {
            OverdraftPrompt(balance.account.name, "−" + Money.formatManat(-after))
        } else {
            null
        }
    }

    fun consumeListMessage() {
        _list.update { it.copy(message = null) }
    }

    fun openDebt(debtId: Long) {
        viewModelScope.launch {
            _detail.value = DebtDetailUi(loading = true, debtId = debtId)
            loadDetail(debtId)
        }
    }

    private suspend fun loadDetail(debtId: Long, message: String? = null, isError: Boolean = false) {
        val summary = ledger.debtSummary(debtId)
        if (summary == null) {
            _detail.value = DebtDetailUi(loading = false, missing = true, debtId = debtId)
            return
        }
        val accountNames = ledger.accountBalances().associate { it.account.id to it.account.name }
        val entries = ledger.debtEntries(debtId).map { entry ->
            DebtEntryUi(
                id = entry.id,
                dateLabel = entry.date.format(DISPLAY_DATE),
                amountLabel = Money.formatManat(entry.amountMinor),
                accountName = entry.accountId?.let { accountNames[it] },
                isIssue = entry.kind == DebtEntryKind.ISSUE,
                isWriteOff = entry.kind == DebtEntryKind.WRITE_OFF
            )
        }
        _detail.value = DebtDetailUi(
            loading = false,
            debtId = debtId,
            person = summary.debt.person,
            note = summary.debt.note,
            direction = summary.debt.direction,
            remainingLabel = Money.formatManat(summary.remainingMinor),
            totalLabel = Money.formatManat(summary.issuedMinor),
            remainingInput = Money.formatManat(summary.remainingMinor),
            percent = summary.percent,
            settled = summary.settled,
            entries = entries,
            accounts = accountOptions(),
            message = message,
            isError = isError
        )
    }

    fun updateDebt(person: String, note: String) {
        val debtId = _detail.value.debtId
        viewModelScope.launch {
            if (person.isBlank()) {
                showMessage(strings.get(R.string.err_debt_input), isError = true)
                return@launch
            }
            try {
                ledger.updateDebt(debtId, person, note)
                loadDetail(debtId)
            } catch (error: Exception) {
                showMessage(strings.get(R.string.err_generic), isError = true)
            }
        }
    }

    fun repay(amountInput: String, accountId: Long?) {
        val debtId = _detail.value.debtId
        viewModelScope.launch {
            val amount = parseAmount(amountInput)
            if (amount == null || amount <= 0L) {
                showMessage(strings.get(R.string.err_debt_input), isError = true)
                return@launch
            }
            val perform: suspend () -> Unit = {
                try {
                    ledger.repayDebt(debtId, amount, accountId, LocalDate.now())
                    loadDetail(debtId, strings.get(R.string.msg_debt_repaid))
                } catch (error: DebtOverpayException) {
                    showMessage(strings.get(R.string.err_debt_overpay), isError = true)
                } catch (error: Exception) {
                    showMessage(strings.get(R.string.err_generic), isError = true)
                }
            }
            // Paying back a debt of the user's takes money out of the account.
            val borrowed = _detail.value.direction == DebtDirection.BORROWED
            val prompt = if (borrowed) overdraftOf(accountId, amount) else null
            if (prompt == null) {
                perform()
            } else {
                pendingDetail = perform
                _detail.update { it.copy(overdraft = prompt) }
            }
        }
    }

    fun confirmOverdraft() {
        val action = pendingDetail
        pendingDetail = null
        _detail.update { it.copy(overdraft = null) }
        if (action != null) viewModelScope.launch { action() }
    }

    fun cancelOverdraft() {
        pendingDetail = null
        _detail.update { it.copy(overdraft = null) }
    }

    fun writeOff() {
        val debtId = _detail.value.debtId
        viewModelScope.launch {
            try {
                ledger.writeOffDebt(
                    debtId = debtId,
                    date = LocalDate.now(),
                    expenseCategoryName = strings.get(R.string.debt_category_written_off),
                    incomeCategoryName = strings.get(R.string.debt_category_forgiven)
                )
                loadDetail(debtId, strings.get(R.string.msg_debt_written_off))
            } catch (error: Exception) {
                showMessage(strings.get(R.string.err_generic), isError = true)
            }
        }
    }

    fun undoPayment(entryId: Long) {
        val debtId = _detail.value.debtId
        viewModelScope.launch {
            try {
                ledger.deleteDebtPayment(entryId)
                loadDetail(debtId)
            } catch (error: Exception) {
                showMessage(strings.get(R.string.err_generic), isError = true)
            }
        }
    }

    fun deleteDebt(onDone: () -> Unit) {
        val debtId = _detail.value.debtId
        viewModelScope.launch {
            try {
                ledger.deleteDebt(debtId)
                refreshList()
                onDone()
            } catch (error: Exception) {
                showMessage(strings.get(R.string.err_generic), isError = true)
            }
        }
    }

    fun consumeMessage() {
        _detail.update { it.copy(message = null, isError = false) }
    }

    private fun showMessage(text: String, isError: Boolean) {
        _detail.update { it.copy(message = text, isError = isError) }
    }

    private fun parseAmount(input: String): Long? = try {
        Money.parseManat(input)
    } catch (error: Exception) {
        null
    }

    companion object {
        fun factory(ledger: Ledger, strings: AppStrings): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DebtsViewModel(ledger, strings) as T
                }
            }
    }
}
