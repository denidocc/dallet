package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class LedgerAnalyticsTest {

    private fun newLedger(): Ledger {
        return Ledger(
            accountRepository = InMemoryAccountRepository(),
            categoryRepository = InMemoryCategoryRepository(),
            operationRepository = InMemoryOperationRepository()
        )
    }

    @Test
    fun analytics_month_sumsIncomeAndExpense_ignoresTransfersAndOutsidePeriod() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 100_000L, cardOpeningMinor = 50_000L)
        val cash = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        val transport = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Транспорт" }
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }

        ledger.addExpense(cash.id, food.id, 1_000L, LocalDate.of(2026, 7, 10), null)
        ledger.addExpense(cash.id, transport.id, 500L, LocalDate.of(2026, 7, 20), null)
        ledger.addIncome(card.id, salary.id, 10_000L, LocalDate.of(2026, 7, 5), null)
        ledger.addTransfer(card.id, cash.id, 2_000L, LocalDate.of(2026, 7, 12), null)
        // outside July
        ledger.addExpense(cash.id, food.id, 9_000L, LocalDate.of(2026, 6, 30), null)
        ledger.addIncome(card.id, salary.id, 8_000L, LocalDate.of(2026, 8, 1), null)

        val summary = ledger.analytics(AnalyticsPeriod.MONTH, asOf = LocalDate.of(2026, 7, 15))

        assertThat(summary.fromInclusive).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(summary.toInclusive).isEqualTo(LocalDate.of(2026, 7, 31))
        assertThat(summary.incomeTotalMinor).isEqualTo(10_000L)
        assertThat(summary.expenseTotalMinor).isEqualTo(1_500L)
    }

    @Test
    fun analytics_expensesByCategory_groupsAndOrdersByAmountDescending() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 100_000L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first().account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        val transport = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Транспорт" }
        val home = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Дом" }

        ledger.addExpense(cash.id, food.id, 300L, LocalDate.of(2026, 7, 2), null)
        ledger.addExpense(cash.id, food.id, 200L, LocalDate.of(2026, 7, 3), null)
        ledger.addExpense(cash.id, transport.id, 100L, LocalDate.of(2026, 7, 4), null)
        ledger.addExpense(cash.id, home.id, 700L, LocalDate.of(2026, 7, 5), null)
        ledger.addTransfer(
            fromAccountId = cash.id,
            toAccountId = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account.id,
            amountMinor = 50L,
            date = LocalDate.of(2026, 7, 6),
            comment = null
        )

        val summary = ledger.analytics(AnalyticsPeriod.MONTH, asOf = LocalDate.of(2026, 7, 15))

        assertThat(summary.expensesByCategory.map { it.categoryName to it.amountMinor })
            .containsExactly(
                "Дом" to 700L,
                "Еда" to 500L,
                "Транспорт" to 100L
            )
            .inOrder()
        assertThat(summary.expenseTotalMinor).isEqualTo(1_300L)
        assertThat(summary.expensesByCategory.map { it.categoryId })
            .containsExactly(home.id, food.id, transport.id)
            .inOrder()
    }

    @Test
    fun analytics_weekAndYear_useCalendarBounds() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 100_000L, cardOpeningMinor = 50_000L)
        val cash = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }

        // 2026-07-15 is Wednesday → week Mon 13 … Sun 19
        ledger.addExpense(cash.id, food.id, 100L, LocalDate.of(2026, 7, 13), null)
        ledger.addExpense(cash.id, food.id, 200L, LocalDate.of(2026, 7, 19), null)
        ledger.addExpense(cash.id, food.id, 50L, LocalDate.of(2026, 7, 12), null) // previous week
        ledger.addIncome(card.id, salary.id, 1_000L, LocalDate.of(2026, 7, 15), null)

        val week = ledger.analytics(AnalyticsPeriod.WEEK, asOf = LocalDate.of(2026, 7, 15))
        assertThat(week.fromInclusive).isEqualTo(LocalDate.of(2026, 7, 13))
        assertThat(week.toInclusive).isEqualTo(LocalDate.of(2026, 7, 19))
        assertThat(week.expenseTotalMinor).isEqualTo(300L)
        assertThat(week.incomeTotalMinor).isEqualTo(1_000L)

        ledger.addExpense(cash.id, food.id, 400L, LocalDate.of(2025, 12, 31), null)
        ledger.addIncome(card.id, salary.id, 5_000L, LocalDate.of(2026, 1, 1), null)

        val year = ledger.analytics(AnalyticsPeriod.YEAR, asOf = LocalDate.of(2026, 7, 15))
        assertThat(year.fromInclusive).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(year.toInclusive).isEqualTo(LocalDate.of(2026, 12, 31))
        assertThat(year.expenseTotalMinor).isEqualTo(350L) // 100+200+50 in 2026, not 400 from 2025
        assertThat(year.incomeTotalMinor).isEqualTo(6_000L)
    }
}
