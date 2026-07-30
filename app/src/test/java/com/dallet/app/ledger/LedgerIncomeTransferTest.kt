package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertFailsWith

class LedgerIncomeTransferTest {

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    @Test
    fun recordingIncome_increasesAccountAndTotalBalance() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 1_000L, cardOpeningMinor = 2_000L)
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }

        ledger.addIncome(
            accountId = card.id,
            categoryId = salary.id,
            amountMinor = 5_000L,
            date = LocalDate.of(2026, 7, 30),
            comment = "июнь"
        )

        val balances = ledger.accountBalances().associate { it.account.name to it.balanceMinor }
        assertThat(balances[Ledger.CARD_NAME]).isEqualTo(7_000L)
        assertThat(balances[Ledger.CASH_NAME]).isEqualTo(1_000L)
        assertThat(ledger.totalBalanceMinor()).isEqualTo(8_000L)
    }

    @Test
    fun transfer_movesMoneyBetweenAccounts_withoutChangingTotal() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 3_000L, cardOpeningMinor = 10_000L)
        val cash = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account

        ledger.addTransfer(
            fromAccountId = card.id,
            toAccountId = cash.id,
            amountMinor = 2_500L,
            date = LocalDate.of(2026, 7, 30),
            comment = "снятие"
        )

        val balances = ledger.accountBalances().associate { it.account.name to it.balanceMinor }
        assertThat(balances[Ledger.CARD_NAME]).isEqualTo(7_500L)
        assertThat(balances[Ledger.CASH_NAME]).isEqualTo(5_500L)
        assertThat(ledger.totalBalanceMinor()).isEqualTo(13_000L)
    }

    @Test
    fun transfer_toSameAccount_isRejected() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 1_000L, cardOpeningMinor = 1_000L)
        val cashId = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account.id

        assertFailsWith<SameAccountTransferException> {
            ledger.addTransfer(
                fromAccountId = cashId,
                toAccountId = cashId,
                amountMinor = 100L,
                date = LocalDate.of(2026, 7, 30),
                comment = null
            )
        }
        assertThat(ledger.totalBalanceMinor()).isEqualTo(2_000L)
    }
}
