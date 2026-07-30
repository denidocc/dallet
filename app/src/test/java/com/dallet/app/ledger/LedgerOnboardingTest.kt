package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LedgerOnboardingTest {

    @Test
    fun afterOnboarding_accountBalancesMatchOpeningAmounts_andTotalSumsThem() = runTest {
        val ledger = Ledger(
            accountRepository = InMemoryAccountRepository(),
            categoryRepository = InMemoryCategoryRepository(),
            operationRepository = InMemoryOperationRepository()
        )

        ledger.completeOnboarding(
            cashOpeningMinor = 10_000L, // 100.00 TMT
            cardOpeningMinor = 5_050L   // 50.50 TMT
        )

        val balances = ledger.accountBalances()
        assertThat(balances).hasSize(2)
        assertThat(balances.map { it.account.name to it.balanceMinor }).containsExactly(
            "Наличные" to 10_000L,
            "ЗП карта" to 5_050L
        )
        assertThat(ledger.totalBalanceMinor()).isEqualTo(15_050L)
    }

    @Test
    fun beforeOnboarding_hasNoAccounts() = runTest {
        val ledger = Ledger(
            accountRepository = InMemoryAccountRepository(),
            categoryRepository = InMemoryCategoryRepository(),
            operationRepository = InMemoryOperationRepository()
        )

        assertThat(ledger.hasCompletedOnboarding()).isFalse()
        assertThat(ledger.accountBalances()).isEmpty()
        assertThat(ledger.totalBalanceMinor()).isEqualTo(0L)
    }
}
