package com.dallet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dallet.app.ledger.AccountBalance
import com.dallet.app.ledger.Ledger
import com.dallet.app.ledger.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val needsOnboarding: Boolean = false,
    val totalBalanceLabel: String = "0.00",
    val accountRows: List<AccountRowUi> = emptyList(),
    val errorMessage: String? = null
)

data class AccountRowUi(
    val name: String,
    val balanceLabel: String
)

data class OnboardingUiState(
    val cashInput: String = "",
    val cardInput: String = "",
    val submitting: Boolean = false,
    val errorMessage: String? = null
)

class DalletViewModel(
    private val ledger: Ledger
) : ViewModel() {

    private val _home = MutableStateFlow(HomeUiState())
    val home: StateFlow<HomeUiState> = _home.asStateFlow()

    private val _onboarding = MutableStateFlow(OnboardingUiState())
    val onboarding: StateFlow<OnboardingUiState> = _onboarding.asStateFlow()

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
                    _home.value = HomeUiState(
                        loading = false,
                        needsOnboarding = false,
                        totalBalanceLabel = Money.formatManat(ledger.totalBalanceMinor()),
                        accountRows = ledger.accountBalances().map { it.toRow() }
                    )
                }
            } catch (error: Exception) {
                _home.update {
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

    private fun AccountBalance.toRow(): AccountRowUi =
        AccountRowUi(
            name = account.name,
            balanceLabel = Money.formatManat(balanceMinor)
        )

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
