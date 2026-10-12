package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class HistoryPeriodTest {

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    private class Fixture(val ledger: Ledger, val cashId: Long, val foodId: Long)

    private suspend fun fixture(): Fixture {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 1_000_000L, cardOpeningMinor = 0L)
        return Fixture(
            ledger = ledger,
            cashId = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account.id,
            foodId = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }.id
        )
    }

    private suspend fun Fixture.add(date: LocalDate, comment: String) {
        ledger.addExpense(cashId, foodId, 100L, date, comment)
    }

    private suspend fun Ledger.comments(filter: OperationFilter, today: LocalDate): List<String?> =
        history(filter, today).days.flatMap { it.operations }.map { it.comment }

    @Test
    fun allTime_isTheDefault_andLetsEverythingThrough() = runTest {
        val f = fixture()
        f.add(LocalDate.of(2020, 1, 1), "old")
        f.add(LocalDate.of(2026, 10, 9), "new")

        val found = f.ledger.comments(OperationFilter(), LocalDate.of(2026, 10, 12))

        assertThat(found).containsExactly("old", "new")
        assertThat(OperationFilter().period).isEqualTo(HistoryPeriod.ALL_TIME)
    }

    @Test
    fun thisMonth_includesFirstAndLastDay_only() = runTest {
        val f = fixture()
        f.add(LocalDate.of(2026, 9, 30), "before")
        f.add(LocalDate.of(2026, 10, 1), "first")
        f.add(LocalDate.of(2026, 10, 31), "last")
        f.add(LocalDate.of(2026, 11, 1), "after")

        val found = f.ledger.comments(
            OperationFilter(period = HistoryPeriod.THIS_MONTH),
            today = LocalDate.of(2026, 10, 12)
        )

        assertThat(found).containsExactly("first", "last")
    }

    @Test
    fun lastMonth_acrossYearBoundary_includesDecember31() = runTest {
        val f = fixture()
        f.add(LocalDate.of(2026, 11, 30), "before")
        f.add(LocalDate.of(2026, 12, 1), "first")
        f.add(LocalDate.of(2026, 12, 31), "last")
        f.add(LocalDate.of(2027, 1, 1), "after")

        val found = f.ledger.comments(
            OperationFilter(period = HistoryPeriod.LAST_MONTH),
            today = LocalDate.of(2027, 1, 5)
        )

        assertThat(found).containsExactly("first", "last")
    }

    @Test
    fun lastMonth_inALeapYear_includesFebruary29() = runTest {
        val f = fixture()
        f.add(LocalDate.of(2028, 1, 31), "before")
        f.add(LocalDate.of(2028, 2, 1), "first")
        f.add(LocalDate.of(2028, 2, 29), "leapDay")
        f.add(LocalDate.of(2028, 3, 1), "after")

        val found = f.ledger.comments(
            OperationFilter(period = HistoryPeriod.LAST_MONTH),
            today = LocalDate.of(2028, 3, 15)
        )

        assertThat(found).containsExactly("first", "leapDay")
    }

    @Test
    fun thisYear_coversJanuary1ToDecember31() = runTest {
        val f = fixture()
        f.add(LocalDate.of(2025, 12, 31), "before")
        f.add(LocalDate.of(2026, 1, 1), "first")
        f.add(LocalDate.of(2026, 12, 31), "last")
        f.add(LocalDate.of(2027, 1, 1), "after")

        val found = f.ledger.comments(
            OperationFilter(period = HistoryPeriod.THIS_YEAR),
            today = LocalDate.of(2026, 6, 15)
        )

        assertThat(found).containsExactly("first", "last")
    }

    @Test
    fun customPeriod_includesBothEndDates() = runTest {
        val f = fixture()
        f.add(LocalDate.of(2026, 10, 4), "before")
        f.add(LocalDate.of(2026, 10, 5), "start")
        f.add(LocalDate.of(2026, 10, 7), "middle")
        f.add(LocalDate.of(2026, 10, 9), "end")
        f.add(LocalDate.of(2026, 10, 10), "after")

        val found = f.ledger.comments(
            OperationFilter(
                period = HistoryPeriod.CUSTOM,
                customFrom = LocalDate.of(2026, 10, 5),
                customTo = LocalDate.of(2026, 10, 9)
            ),
            today = LocalDate.of(2026, 10, 12)
        )

        assertThat(found).containsExactly("start", "middle", "end")
    }

    @Test
    fun customPeriod_ofOneDay_works() = runTest {
        val f = fixture()
        f.add(LocalDate.of(2026, 10, 8), "other")
        f.add(LocalDate.of(2026, 10, 9), "that")

        val found = f.ledger.comments(
            OperationFilter(
                period = HistoryPeriod.CUSTOM,
                customFrom = LocalDate.of(2026, 10, 9),
                customTo = LocalDate.of(2026, 10, 9)
            ),
            today = LocalDate.of(2026, 10, 12)
        )

        assertThat(found).containsExactly("that")
    }

    @Test
    fun customPeriod_withEndBeforeStart_findsNothing() = runTest {
        val f = fixture()
        f.add(LocalDate.of(2026, 10, 9), "x")

        val found = f.ledger.comments(
            OperationFilter(
                period = HistoryPeriod.CUSTOM,
                customFrom = LocalDate.of(2026, 10, 12),
                customTo = LocalDate.of(2026, 10, 1)
            ),
            today = LocalDate.of(2026, 10, 12)
        )

        assertThat(found).isEmpty()
    }

    @Test
    fun customPeriod_withoutDates_doesNotRestrict() = runTest {
        val f = fixture()
        f.add(LocalDate.of(2020, 1, 1), "old")
        f.add(LocalDate.of(2026, 10, 9), "new")

        val found = f.ledger.comments(
            OperationFilter(period = HistoryPeriod.CUSTOM),
            today = LocalDate.of(2026, 10, 12)
        )

        assertThat(found).containsExactly("old", "new")
    }

    @Test
    fun period_combinesWithTextAndTotals() = runTest {
        val f = fixture()
        f.add(LocalDate.of(2026, 9, 20), "tea")
        f.add(LocalDate.of(2026, 10, 3), "tea")
        f.add(LocalDate.of(2026, 10, 4), "bread")

        val result = f.ledger.history(
            OperationFilter(query = "tea", period = HistoryPeriod.THIS_MONTH),
            today = LocalDate.of(2026, 10, 12)
        )

        assertThat(result.count).isEqualTo(1)
        assertThat(result.expenseMinor).isEqualTo(100L)
    }

    @Test
    fun filterWithPeriod_isNotEmpty() {
        assertThat(OperationFilter(period = HistoryPeriod.THIS_YEAR).isEmpty).isFalse()
    }
}
