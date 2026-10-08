package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class LedgerIncomeAnalyticsTest {

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    @Test
    fun incomesByCategory_groupsAndOrdersByAmountDescending() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }
        val side = ledger.categories(CategoryKind.INCOME).first { it.name == "Подработка" }
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }

        ledger.addIncome(card.id, salary.id, 10_000L, LocalDate.of(2026, 7, 5), null)
        ledger.addIncome(card.id, side.id, 2_000L, LocalDate.of(2026, 7, 6), null)
        ledger.addIncome(card.id, side.id, 3_000L, LocalDate.of(2026, 7, 7), null)
        ledger.addExpense(card.id, food.id, 500L, LocalDate.of(2026, 7, 8), null)

        val summary = ledger.analytics(AnalyticsPeriod.MONTH, asOf = LocalDate.of(2026, 7, 15))

        assertThat(summary.incomesByCategory.map { it.categoryName })
            .containsExactly("Зарплата", "Подработка").inOrder()
        assertThat(summary.incomesByCategory.map { it.amountMinor })
            .containsExactly(10_000L, 5_000L).inOrder()
    }

    @Test
    fun incomesByCategory_ignoresExpensesTransfersAndOtherPeriods() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        val accounts = ledger.accountBalances().map { it.account }
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }

        ledger.addIncome(accounts[0].id, salary.id, 1_000L, LocalDate.of(2026, 6, 30), null)
        ledger.addIncome(accounts[0].id, salary.id, 4_000L, LocalDate.of(2026, 7, 1), null)
        ledger.addTransfer(accounts[0].id, accounts[1].id, 500L, LocalDate.of(2026, 7, 2), null)

        val summary = ledger.analytics(AnalyticsPeriod.MONTH, asOf = LocalDate.of(2026, 7, 15))

        assertThat(summary.incomesByCategory.sumOf { it.amountMinor }).isEqualTo(4_000L)
        assertThat(summary.incomeTotalMinor).isEqualTo(4_000L)
    }

    @Test
    fun incomeFlow_month_hasPointPerDayWithDailySums() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }

        ledger.addIncome(card.id, salary.id, 700L, LocalDate.of(2026, 7, 5), null)
        ledger.addIncome(card.id, salary.id, 300L, LocalDate.of(2026, 7, 5), null)

        val summary = ledger.analytics(AnalyticsPeriod.MONTH, asOf = LocalDate.of(2026, 7, 15))

        assertThat(summary.incomeFlow).hasSize(31)
        assertThat(summary.incomeFlow.first { it.bucketStart == LocalDate.of(2026, 7, 5) }.amountMinor)
            .isEqualTo(1_000L)
        assertThat(summary.incomeFlow.first { it.bucketStart == LocalDate.of(2026, 7, 6) }.amountMinor)
            .isEqualTo(0L)
    }

    @Test
    fun incomeFlow_year_hasPointPerMonth() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }

        ledger.addIncome(card.id, salary.id, 900L, LocalDate.of(2026, 3, 10), null)
        ledger.addIncome(card.id, salary.id, 100L, LocalDate.of(2026, 3, 20), null)

        val summary = ledger.analytics(AnalyticsPeriod.YEAR, asOf = LocalDate.of(2026, 7, 15))

        assertThat(summary.incomeFlow).hasSize(12)
        assertThat(summary.incomeFlow[2].amountMinor).isEqualTo(1_000L)
    }

    @Test
    fun noIncome_givesEmptyCategoriesAndZeroFlow() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)

        val summary = ledger.analytics(AnalyticsPeriod.WEEK, asOf = LocalDate.of(2026, 7, 15))

        assertThat(summary.incomesByCategory).isEmpty()
        assertThat(summary.incomeFlow.all { it.amountMinor == 0L }).isTrue()
    }
}
