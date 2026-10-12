package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class AnalyticsCustomPeriodTest {

    private fun date(month: Int, day: Int, year: Int = 2026): LocalDate = LocalDate.of(year, month, day)

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    private class Fixture(
        val ledger: Ledger,
        val cashId: Long,
        val foodId: Long,
        val transportId: Long,
        val salaryId: Long
    )

    private suspend fun fixture(): Fixture {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 1_000_000L, cardOpeningMinor = 0L)
        val expenses = ledger.categories(CategoryKind.EXPENSE)
        return Fixture(
            ledger = ledger,
            cashId = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account.id,
            foodId = expenses.first { it.name == "Еда" }.id,
            transportId = expenses.first { it.name == "Транспорт" }.id,
            salaryId = ledger.categories(CategoryKind.INCOME).first().id
        )
    }

    // --- DateWindow ---------------------------------------------------------------------

    @Test
    fun window_countsBothEndDays() {
        assertThat(DateWindow(date(10, 5), date(10, 9)).days).isEqualTo(5L)
        assertThat(DateWindow(date(10, 9), date(10, 9)).days).isEqualTo(1L)
    }

    @Test
    fun window_withEndBeforeStart_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DateWindow(date(10, 9), date(10, 5))
        }
    }

    @Test
    fun window_shiftsByItsOwnLength() {
        val window = DateWindow(date(10, 5), date(10, 9))

        assertThat(window.shifted(-1)).isEqualTo(DateWindow(date(9, 30), date(10, 4)))
        assertThat(window.shifted(1)).isEqualTo(DateWindow(date(10, 10), date(10, 14)))
        assertThat(window.shifted(0)).isEqualTo(window)
        assertThat(window.shifted(-2)).isEqualTo(DateWindow(date(9, 25), date(9, 29)))
    }

    // --- totals -------------------------------------------------------------------------

    @Test
    fun customPeriod_countsOperationsOnBothEndDays_only() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 100L, date(10, 4), null)
        f.ledger.addExpense(f.cashId, f.foodId, 200L, date(10, 5), null)
        f.ledger.addExpense(f.cashId, f.transportId, 400L, date(10, 9), null)
        f.ledger.addExpense(f.cashId, f.foodId, 800L, date(10, 10), null)
        f.ledger.addIncome(f.cashId, f.salaryId, 5_000L, date(10, 7), null)

        val summary = f.ledger.analytics(
            AnalyticsPeriod.CUSTOM,
            date(10, 9),
            DateWindow(date(10, 5), date(10, 9))
        )

        assertThat(summary.fromInclusive).isEqualTo(date(10, 5))
        assertThat(summary.toInclusive).isEqualTo(date(10, 9))
        assertThat(summary.expenseTotalMinor).isEqualTo(600L)
        assertThat(summary.incomeTotalMinor).isEqualTo(5_000L)
        assertThat(summary.expensesByCategory.map { it.categoryName })
            .containsExactly("Транспорт", "Еда").inOrder()
    }

    @Test
    fun customPeriod_withoutAWindow_isRejected() = runTest {
        val f = fixture()

        val failure = runCatching {
            f.ledger.analytics(AnalyticsPeriod.CUSTOM, date(10, 9))
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }

    // --- comparison with the previous window --------------------------------------------

    @Test
    fun comparison_usesThePreviousWindowOfTheSameLength() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, date(9, 30), null)
        f.ledger.addExpense(f.cashId, f.foodId, 500L, date(10, 4), null)
        f.ledger.addExpense(f.cashId, f.foodId, 2_000L, date(10, 5), null)
        f.ledger.addExpense(f.cashId, f.foodId, 2_000L, date(9, 29), null)

        val comparison = f.ledger.analyticsComparison(
            period = AnalyticsPeriod.CUSTOM,
            asOf = date(10, 9),
            today = date(10, 20),
            window = DateWindow(date(10, 5), date(10, 9))
        )

        assertThat(comparison.current.expenseTotalMinor).isEqualTo(2_000L)
        assertThat(comparison.previous.fromInclusive).isEqualTo(date(9, 30))
        assertThat(comparison.previous.toInclusive).isEqualTo(date(10, 4))
        assertThat(comparison.previous.expenseTotalMinor).isEqualTo(1_500L)
        assertThat(percentChange(2_000L, 1_500L)).isEqualTo(33)
    }

    @Test
    fun comparison_flagsAnUnfinishedWindow() = runTest {
        val f = fixture()
        val window = DateWindow(date(10, 5), date(10, 9))

        val finished = f.ledger.analyticsComparison(
            AnalyticsPeriod.CUSTOM, date(10, 9), today = date(10, 9), window = window
        )
        val unfinished = f.ledger.analyticsComparison(
            AnalyticsPeriod.CUSTOM, date(10, 9), today = date(10, 8), window = window
        )

        assertThat(finished.currentIsComplete).isTrue()
        assertThat(unfinished.currentIsComplete).isFalse()
    }

    // --- flow chart ---------------------------------------------------------------------

    @Test
    fun shortWindow_isShownDayByDay() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 100L, date(10, 6), null)
        f.ledger.addExpense(f.cashId, f.foodId, 300L, date(10, 6), null)

        val summary = f.ledger.analytics(
            AnalyticsPeriod.CUSTOM,
            date(10, 9),
            DateWindow(date(10, 5), date(10, 9))
        )

        assertThat(summary.expenseFlow).hasSize(5)
        assertThat(summary.expenseFlow.map { it.bucketStart }.first()).isEqualTo(date(10, 5))
        assertThat(summary.expenseFlow[1].amountMinor).isEqualTo(400L)
        assertThat(summary.expenseFlow[0].amountMinor).isEqualTo(0L)
    }

    @Test
    fun windowOfExactly62Days_isStillDayByDay() = runTest {
        val f = fixture()
        val from = date(1, 1)

        val summary = f.ledger.analytics(
            AnalyticsPeriod.CUSTOM,
            from.plusDays(61),
            DateWindow(from, from.plusDays(61))
        )

        assertThat(summary.expenseFlow).hasSize(62)
    }

    @Test
    fun longWindow_isShownMonthByMonth() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 100L, date(1, 15), null)
        f.ledger.addExpense(f.cashId, f.foodId, 200L, date(1, 20), null)
        f.ledger.addExpense(f.cashId, f.foodId, 700L, date(3, 2), null)

        val summary = f.ledger.analytics(
            AnalyticsPeriod.CUSTOM,
            date(4, 30),
            DateWindow(date(1, 1), date(4, 30))
        )

        assertThat(summary.expenseFlow.map { it.bucketStart })
            .containsExactly(date(1, 1), date(2, 1), date(3, 1), date(4, 1)).inOrder()
        assertThat(summary.expenseFlow.map { it.amountMinor })
            .containsExactly(300L, 0L, 700L, 0L).inOrder()
    }

    @Test
    fun monthlyFlow_acrossYears_keepsEveryMonth() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 100L, date(12, 30, 2026), null)
        f.ledger.addExpense(f.cashId, f.foodId, 900L, date(1, 2, 2027), null)

        val summary = f.ledger.analytics(
            AnalyticsPeriod.CUSTOM,
            date(2, 28, 2027),
            DateWindow(date(11, 1, 2026), date(2, 28, 2027))
        )

        assertThat(summary.expenseFlow.map { it.bucketStart }).containsExactly(
            date(11, 1, 2026), date(12, 1, 2026), date(1, 1, 2027), date(2, 1, 2027)
        ).inOrder()
        assertThat(summary.expenseFlow.map { it.amountMinor })
            .containsExactly(0L, 100L, 900L, 0L).inOrder()
    }

    // --- by account and report ----------------------------------------------------------

    @Test
    fun byAccount_andReportRows_followTheWindow() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 100L, date(10, 4), "outside")
        f.ledger.addExpense(f.cashId, f.foodId, 250L, date(10, 6), "inside")

        val window = DateWindow(date(10, 5), date(10, 9))
        val summary = f.ledger.analytics(AnalyticsPeriod.CUSTOM, date(10, 9), window)
        val report = f.ledger.periodReport(
            period = AnalyticsPeriod.CUSTOM,
            asOf = date(10, 9),
            today = date(10, 20),
            window = window
        )

        assertThat(summary.byAccount.first { it.accountId == f.cashId }.expenseMinor)
            .isEqualTo(250L)
        assertThat(report.rows.map { it.comment }).containsExactly("inside")
    }
}
