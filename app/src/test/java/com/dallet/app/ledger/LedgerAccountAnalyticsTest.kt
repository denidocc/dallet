package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class LedgerAccountAnalyticsTest {

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    @Test
    fun byAccount_sumsIncomeAndExpensePerAccount() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }

        ledger.addExpense(cash.id, food.id, 300L, LocalDate.of(2026, 7, 2), null)
        ledger.addExpense(cash.id, food.id, 200L, LocalDate.of(2026, 7, 3), null)
        ledger.addExpense(card.id, food.id, 900L, LocalDate.of(2026, 7, 4), null)
        ledger.addIncome(card.id, salary.id, 10_000L, LocalDate.of(2026, 7, 5), null)

        val summary = ledger.analytics(AnalyticsPeriod.MONTH, asOf = LocalDate.of(2026, 7, 15))

        val cashRow = summary.byAccount.first { it.accountId == cash.id }
        val cardRow = summary.byAccount.first { it.accountId == card.id }
        assertThat(cashRow.expenseMinor).isEqualTo(500L)
        assertThat(cashRow.incomeMinor).isEqualTo(0L)
        assertThat(cardRow.expenseMinor).isEqualTo(900L)
        assertThat(cardRow.incomeMinor).isEqualTo(10_000L)
    }

    @Test
    fun byAccount_ignoresTransfersAndOtherPeriods() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }

        ledger.addTransfer(card.id, cash.id, 5_000L, LocalDate.of(2026, 7, 6), null)
        ledger.addExpense(cash.id, food.id, 700L, LocalDate.of(2026, 6, 30), null)

        val summary = ledger.analytics(AnalyticsPeriod.MONTH, asOf = LocalDate.of(2026, 7, 15))

        assertThat(summary.byAccount.all { it.incomeMinor == 0L && it.expenseMinor == 0L }).isTrue()
    }

    @Test
    fun byAccount_listsEveryAccountInOrder_evenWithoutOperations() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        ledger.createAccount("Накопления", 0L)

        val summary = ledger.analytics(AnalyticsPeriod.WEEK, asOf = LocalDate.of(2026, 7, 15))

        assertThat(summary.byAccount.map { it.accountName })
            .containsExactly(Ledger.CASH_NAME, Ledger.CARD_NAME, "Накопления").inOrder()
    }
}
