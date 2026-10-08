package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class AnalyticsComparisonTest {

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    @Test
    fun percentChange_growth() {
        assertThat(percentChange(current = 112L, previous = 100L)).isEqualTo(12)
    }

    @Test
    fun percentChange_decline() {
        assertThat(percentChange(current = 50L, previous = 100L)).isEqualTo(-50)
    }

    @Test
    fun percentChange_noChange() {
        assertThat(percentChange(current = 100L, previous = 100L)).isEqualTo(0)
    }

    @Test
    fun percentChange_roundsToNearestInteger() {
        assertThat(percentChange(current = 1_005L, previous = 1_000L)).isEqualTo(1)
        assertThat(percentChange(current = 1_004L, previous = 1_000L)).isEqualTo(0)
    }

    @Test
    fun percentChange_withoutPreviousIsNull() {
        assertThat(percentChange(current = 500L, previous = 0L)).isNull()
        assertThat(percentChange(current = 0L, previous = 0L)).isNull()
    }

    @Test
    fun percentChange_toZeroIsMinusHundred() {
        assertThat(percentChange(current = 0L, previous = 300L)).isEqualTo(-100)
    }

    @Test
    fun shiftAsOf_movesByWholePeriods() {
        val day = LocalDate.of(2026, 10, 8)

        assertThat(shiftAsOf(AnalyticsPeriod.WEEK, day, -1)).isEqualTo(LocalDate.of(2026, 10, 1))
        assertThat(shiftAsOf(AnalyticsPeriod.MONTH, day, -1)).isEqualTo(LocalDate.of(2026, 9, 8))
        assertThat(shiftAsOf(AnalyticsPeriod.YEAR, day, -1)).isEqualTo(LocalDate.of(2025, 10, 8))
        assertThat(shiftAsOf(AnalyticsPeriod.MONTH, day, 0)).isEqualTo(day)
    }

    @Test
    fun comparison_previousIsPreviousFullPeriod() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }

        ledger.addExpense(card.id, food.id, 1_000L, LocalDate.of(2026, 6, 10), null)
        ledger.addExpense(card.id, food.id, 500L, LocalDate.of(2026, 6, 30), null)
        ledger.addIncome(card.id, salary.id, 9_000L, LocalDate.of(2026, 6, 5), null)
        ledger.addExpense(card.id, food.id, 2_000L, LocalDate.of(2026, 7, 3), null)

        val result = ledger.analyticsComparison(
            period = AnalyticsPeriod.MONTH,
            asOf = LocalDate.of(2026, 7, 15),
            today = LocalDate.of(2026, 7, 15)
        )

        assertThat(result.current.expenseTotalMinor).isEqualTo(2_000L)
        assertThat(result.previous.expenseTotalMinor).isEqualTo(1_500L)
        assertThat(result.previous.incomeTotalMinor).isEqualTo(9_000L)
        assertThat(result.previous.fromInclusive).isEqualTo(LocalDate.of(2026, 6, 1))
        assertThat(result.previous.toInclusive).isEqualTo(LocalDate.of(2026, 6, 30))
    }

    @Test
    fun comparison_currentMonthIsIncompleteUntilItEnds() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)

        val midMonth = ledger.analyticsComparison(
            AnalyticsPeriod.MONTH, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15)
        )
        val lastDay = ledger.analyticsComparison(
            AnalyticsPeriod.MONTH, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 31)
        )
        val past = ledger.analyticsComparison(
            AnalyticsPeriod.MONTH, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 15)
        )

        assertThat(midMonth.currentIsComplete).isFalse()
        assertThat(lastDay.currentIsComplete).isTrue()
        assertThat(past.currentIsComplete).isTrue()
    }
}
