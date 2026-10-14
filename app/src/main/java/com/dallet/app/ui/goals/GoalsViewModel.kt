package com.dallet.app.ui.goals

import com.dallet.app.ui.common.OverdraftPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dallet.app.AppStrings
import com.dallet.app.R
import com.dallet.app.ledger.CategoryKind
import com.dallet.app.ledger.GoalFundsException
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

data class GoalRowUi(
    val id: Long,
    val name: String,
    val savedLabel: String,
    val targetLabel: String,
    val percent: Int,
    val reached: Boolean,
    val archived: Boolean
)

data class GoalsListUi(
    val loaded: Boolean = false,
    val goals: List<GoalRowUi> = emptyList(),
    val message: String? = null
)

data class GoalEntryUi(
    val id: Long,
    val dateLabel: String,
    val amountLabel: String,
    val accountName: String,
    val asIncome: Boolean,
    val isDeposit: Boolean
)

data class GoalDetailUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val goalId: Long = 0L,
    val name: String = "",
    val targetInput: String = "",
    val savedLabel: String = "0.00",
    val targetLabel: String = "0.00",
    val remainingLabel: String = "0.00",
    val percent: Int = 0,
    val reached: Boolean = false,
    val archived: Boolean = false,
    val entries: List<GoalEntryUi> = emptyList(),
    val accounts: List<SelectableOption> = emptyList(),
    val incomeCategories: List<SelectableOption> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false,
    val overdraft: OverdraftPrompt? = null
)

enum class DepositMode { FROM_ACCOUNT, AS_INCOME }

private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

class GoalsViewModel(
    private val ledger: Ledger,
    private val strings: AppStrings
) : ViewModel() {

    private val _list = MutableStateFlow(GoalsListUi())
    val list: StateFlow<GoalsListUi> = _list.asStateFlow()

    private val _detail = MutableStateFlow(GoalDetailUi())
    val detail: StateFlow<GoalDetailUi> = _detail.asStateFlow()

    fun refreshList() {
        viewModelScope.launch {
            val rows = ledger.goals()
                .sortedWith(compareBy({ it.goal.archived }, { it.goal.id }))
                .map {
                    GoalRowUi(
                        id = it.goal.id,
                        name = it.goal.name,
                        savedLabel = Money.formatManat(it.savedMinor),
                        targetLabel = Money.formatManat(it.goal.targetMinor),
                        percent = it.percent,
                        reached = it.reached,
                        archived = it.goal.archived
                    )
                }
            _list.update { it.copy(loaded = true, goals = rows) }
        }
    }

    fun createGoal(name: String, targetInput: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val target = parseAmount(targetInput)
            if (name.isBlank() || target == null || target <= 0L) {
                _list.update { it.copy(message = strings.get(R.string.err_goal_input)) }
                return@launch
            }
            try {
                val id = ledger.createGoal(name, target, LocalDate.now())
                refreshList()
                onCreated(id)
            } catch (error: Exception) {
                _list.update { it.copy(message = strings.get(R.string.err_generic)) }
            }
        }
    }

    fun consumeListMessage() {
        _list.update { it.copy(message = null) }
    }

    fun openGoal(goalId: Long) {
        viewModelScope.launch {
            _detail.value = GoalDetailUi(loading = true, goalId = goalId)
            loadDetail(goalId)
        }
    }

    private suspend fun loadDetail(goalId: Long, message: String? = null, isError: Boolean = false) {
        val summary = ledger.goalSummary(goalId)
        if (summary == null) {
            _detail.value = GoalDetailUi(loading = false, missing = true, goalId = goalId)
            return
        }
        val balances = ledger.accountBalances()
        val accountNames = balances.associate { it.account.id to it.account.name }
        val entries = ledger.goalEntries(goalId).map { entry ->
            GoalEntryUi(
                id = entry.id,
                dateLabel = entry.date.format(DISPLAY_DATE),
                amountLabel = (if (entry.amountMinor > 0) "+" else "−") +
                    Money.formatManat(kotlin.math.abs(entry.amountMinor)),
                accountName = accountNames[entry.accountId].orEmpty(),
                asIncome = entry.operationId != null,
                isDeposit = entry.amountMinor > 0
            )
        }
        _detail.value = GoalDetailUi(
            loading = false,
            goalId = goalId,
            name = summary.goal.name,
            targetInput = Money.formatManat(summary.goal.targetMinor),
            savedLabel = Money.formatManat(summary.savedMinor),
            targetLabel = Money.formatManat(summary.goal.targetMinor),
            remainingLabel = Money.formatManat(summary.remainingMinor),
            percent = summary.percent,
            reached = summary.reached,
            archived = summary.goal.archived,
            entries = entries,
            accounts = balances.map {
                SelectableOption(it.account.id, it.account.name + " · " + Money.formatManat(it.balanceMinor))
            },
            incomeCategories = ledger.displayCategories(CategoryKind.INCOME).map { SelectableOption(it.id, it.name) },
            message = message,
            isError = isError
        )
    }

    fun updateGoal(name: String, targetInput: String) {
        val goalId = _detail.value.goalId
        viewModelScope.launch {
            val target = parseAmount(targetInput)
            if (name.isBlank() || target == null || target <= 0L) {
                showMessage(strings.get(R.string.err_goal_input), isError = true)
                return@launch
            }
            try {
                ledger.updateGoal(goalId, name, target)
                loadDetail(goalId)
            } catch (error: Exception) {
                showMessage(strings.get(R.string.err_generic), isError = true)
            }
        }
    }

    private var pendingAfterOverdraft: (suspend () -> Unit)? = null

    fun deposit(mode: DepositMode, amountInput: String, accountId: Long?, categoryId: Long?) {
        val goalId = _detail.value.goalId
        viewModelScope.launch {
            val amount = parseAmount(amountInput)
            if (amount == null || amount <= 0L || accountId == null ||
                (mode == DepositMode.AS_INCOME && categoryId == null)
            ) {
                showMessage(strings.get(R.string.err_goal_input), isError = true)
                return@launch
            }
            val perform: suspend () -> Unit = {
                try {
                    when (mode) {
                        DepositMode.FROM_ACCOUNT ->
                            ledger.depositToGoal(goalId, accountId, amount, LocalDate.now())
                        DepositMode.AS_INCOME ->
                            ledger.depositToGoalAsIncome(goalId, accountId, categoryId!!, amount, LocalDate.now(), null)
                    }
                    loadDetail(goalId, strings.get(R.string.msg_goal_deposited))
                } catch (error: Exception) {
                    showMessage(strings.get(R.string.err_generic), isError = true)
                }
            }
            // Only money taken from an account can push it below zero.
            val prompt = if (mode == DepositMode.FROM_ACCOUNT) overdraftOf(accountId, amount) else null
            if (prompt == null) {
                perform()
            } else {
                pendingAfterOverdraft = perform
                _detail.update { it.copy(overdraft = prompt) }
            }
        }
    }

    fun confirmOverdraft() {
        val action = pendingAfterOverdraft
        pendingAfterOverdraft = null
        _detail.update { it.copy(overdraft = null) }
        if (action != null) viewModelScope.launch { action() }
    }

    fun cancelOverdraft() {
        pendingAfterOverdraft = null
        _detail.update { it.copy(overdraft = null) }
    }

    private suspend fun overdraftOf(accountId: Long, amountMinor: Long): OverdraftPrompt? {
        val balance = ledger.accountBalances().firstOrNull { it.account.id == accountId } ?: return null
        val after = balance.balanceMinor - amountMinor
        return if (after < 0L) {
            OverdraftPrompt(balance.account.name, "−" + Money.formatManat(-after))
        } else {
            null
        }
    }

    fun withdraw(amountInput: String, accountId: Long?) {
        val goalId = _detail.value.goalId
        viewModelScope.launch {
            val amount = parseAmount(amountInput)
            if (amount == null || amount <= 0L || accountId == null) {
                showMessage(strings.get(R.string.err_goal_input), isError = true)
                return@launch
            }
            try {
                ledger.withdrawFromGoal(goalId, accountId, amount, LocalDate.now())
                loadDetail(goalId, strings.get(R.string.msg_goal_withdrawn))
            } catch (error: GoalFundsException) {
                showMessage(strings.get(R.string.err_goal_funds), isError = true)
            } catch (error: Exception) {
                showMessage(strings.get(R.string.err_generic), isError = true)
            }
        }
    }

    fun deleteEntry(entryId: Long) {
        val goalId = _detail.value.goalId
        viewModelScope.launch {
            try {
                ledger.deleteGoalEntry(entryId)
                loadDetail(goalId)
            } catch (error: GoalFundsException) {
                showMessage(strings.get(R.string.err_goal_funds), isError = true)
            } catch (error: Exception) {
                showMessage(strings.get(R.string.err_generic), isError = true)
            }
        }
    }

    fun setArchived(archived: Boolean) {
        val goalId = _detail.value.goalId
        viewModelScope.launch {
            try {
                ledger.setGoalArchived(goalId, archived)
                loadDetail(goalId)
            } catch (error: Exception) {
                showMessage(strings.get(R.string.err_generic), isError = true)
            }
        }
    }

    fun deleteGoal(onDone: () -> Unit) {
        val goalId = _detail.value.goalId
        viewModelScope.launch {
            try {
                ledger.deleteGoal(goalId)
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
                    return GoalsViewModel(ledger, strings) as T
                }
            }
    }
}
