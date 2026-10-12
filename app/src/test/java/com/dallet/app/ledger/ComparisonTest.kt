package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class ComparisonTest {

    private val today = LocalDate.of(2026, 10, 20)

    private fun date(month: Int, day: Int, year: Int = 2026): LocalDate = LocalDate.of(year, month, day)

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    private class Fixture(
        val ledger: Ledger,
        val cashId: Long,
        val cardId: Long,
        val foodId: Long,
        val transportId: Long,
        val salaryId: Long
    )

    private suspend fun fixture(): Fixture {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 10_000_000L, cardOpeningMinor = 10_000_000L)
        val balances = ledger.accountBalances()
        val expenses = ledger.categories(CategoryKind.EXPENSE)
        return Fixture(
            ledger = ledger,
            cashId = balances.first { it.account.name == Ledger.CASH_NAME }.account.id,
            cardId = balances.first { it.account.name == Ledger.CARD_NAME }.account.id,
            foodId = expenses.first { it.name == "Еда" }.id,
            transportId = expenses.first { it.name == "Транспорт" }.id,
            salaryId = ledger.categories(CategoryKind.INCOME).first().id
        )
    }

    private fun window(from: LocalDate, to: LocalDate) =
        OperationFilter(period = HistoryPeriod.CUSTOM, customFrom = from, customTo = to)

    private val september = window(date(9, 1), date(9, 30))
    private val october = window(date(10, 1), date(10, 31))

    @Test
    fun totals_differenceAndPercent() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_500L, date(9, 10), null)
        f.ledger.addExpense(f.cashId, f.foodId, 2_000L, date(10, 10), null)
        f.ledger.addIncome(f.cardId, f.salaryId, 9_000L, date(9, 5), null)
        f.ledger.addIncome(f.cardId, f.salaryId, 6_000L, date(10, 5), null)

        val result = f.ledger.compare(october, september, today)

        assertThat(result.a.expenseMinor).isEqualTo(2_000L)
        assertThat(result.b.expenseMinor).isEqualTo(1_500L)
        assertThat(result.expenseDiffMinor).isEqualTo(500L)
        assertThat(result.expenseChangePercent).isEqualTo(33)
        assertThat(result.a.incomeMinor).isEqualTo(6_000L)
        assertThat(result.b.incomeMinor).isEqualTo(9_000L)
        assertThat(result.incomeDiffMinor).isEqualTo(-3_000L)
        assertThat(result.incomeChangePercent).isEqualTo(-33)
    }

    @Test
    fun percent_isNullWhenTheBaseIsZero() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, date(10, 10), null)

        val result = f.ledger.compare(october, september, today)

        assertThat(result.expenseDiffMinor).isEqualTo(1_000L)
        assertThat(result.expenseChangePercent).isNull()
        assertThat(result.incomeChangePercent).isNull()
    }

    @Test
    fun identicalSides_haveNoDifference() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, date(10, 10), null)

        val result = f.ledger.compare(october, october, today)

        assertThat(result.expenseDiffMinor).isEqualTo(0L)
        assertThat(result.expenseChangePercent).isEqualTo(0)
    }

    @Test
    fun transfers_areNotCounted() = runTest {
        val f = fixture()
        f.ledger.addTransfer(f.cardId, f.cashId, 5_000L, date(10, 3), null)
        f.ledger.addExpense(f.cashId, f.foodId, 700L, date(10, 4), null)

        val result = f.ledger.compare(october, september, today)

        assertThat(result.a.expenseMinor).isEqualTo(700L)
        assertThat(result.a.incomeMinor).isEqualTo(0L)
        assertThat(result.a.operationCount).isEqualTo(2)
    }

    @Test
    fun categoryTable_isTheUnionOfBothSides_withZeros() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, date(9, 3), null)
        f.ledger.addExpense(f.cashId, f.foodId, 3_000L, date(10, 3), null)
        f.ledger.addExpense(f.cashId, f.transportId, 800L, date(10, 4), null)
        f.ledger.addIncome(f.cardId, f.salaryId, 5_000L, date(9, 20), null)

        val rows = f.ledger.compare(october, september, today).categories

        val food = rows.first { it.categoryId == f.foodId }
        assertThat(food.aMinor).isEqualTo(3_000L)
        assertThat(food.bMinor).isEqualTo(1_000L)
        assertThat(food.diffMinor).isEqualTo(2_000L)
        assertThat(food.changePercent).isEqualTo(200)
        val transport = rows.first { it.categoryId == f.transportId }
        assertThat(transport.aMinor).isEqualTo(800L)
        assertThat(transport.bMinor).isEqualTo(0L)
        assertThat(transport.changePercent).isNull()
        val salary = rows.first { it.categoryId == f.salaryId }
        assertThat(salary.kind).isEqualTo(CategoryKind.INCOME)
        assertThat(salary.aMinor).isEqualTo(0L)
        assertThat(salary.bMinor).isEqualTo(5_000L)
        assertThat(rows.map { it.categoryId }).containsNoDuplicates()
    }

    @Test
    fun categoryTable_listsCategoriesWithoutAnyOperationsNowhere() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, date(10, 3), null)

        val rows = f.ledger.compare(october, september, today).categories

        assertThat(rows.map { it.categoryId }).containsExactly(f.foodId)
    }

    @Test
    fun categoryTable_largestAmountFirst_withinAKind() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, date(10, 3), null)
        f.ledger.addExpense(f.cashId, f.transportId, 5_000L, date(9, 3), null)

        val expenseRows = f.ledger.compare(october, september, today).categories
            .filter { it.kind == CategoryKind.EXPENSE }

        assertThat(expenseRows.map { it.categoryId })
            .containsExactly(f.transportId, f.foodId).inOrder()
    }

    @Test
    fun sideLengths_andDailyAverages() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 3_100L, date(10, 10), null)
        f.ledger.addExpense(f.cashId, f.foodId, 2_800L, date(9, 10), null)

        val result = f.ledger.compare(october, september, today)

        assertThat(result.a.days).isEqualTo(31L)
        assertThat(result.b.days).isEqualTo(30L)
        assertThat(result.a.averageExpensePerDayMinor).isEqualTo(100L)
        assertThat(result.b.averageExpensePerDayMinor).isEqualTo(93L)
    }

    @Test
    fun dailyAverage_roundsHalfUp() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 100L, date(10, 2), null)
        f.ledger.addExpense(f.cashId, f.foodId, 200L, date(9, 2), null)

        val result = f.ledger.compare(
            window(date(10, 1), date(10, 3)),
            window(date(9, 1), date(9, 3)),
            today
        )

        assertThat(result.a.averageExpensePerDayMinor).isEqualTo(33L)
        assertThat(result.b.averageExpensePerDayMinor).isEqualTo(67L)
    }

    @Test
    fun aSideThatHasNotEnded_isMarkedIncomplete() = runTest {
        val f = fixture()

        val result = f.ledger.compare(october, september, today)

        assertThat(result.a.isComplete).isFalse()
        assertThat(result.b.isComplete).isTrue()
    }

    @Test
    fun accountsRestrictEachSideOnItsOwn() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, date(10, 3), null)
        f.ledger.addExpense(f.cardId, f.foodId, 4_000L, date(10, 3), null)

        val result = f.ledger.compare(
            october.copy(accountIds = setOf(f.cashId)),
            october.copy(accountIds = setOf(f.cardId)),
            today
        )

        assertThat(result.a.expenseMinor).isEqualTo(1_000L)
        assertThat(result.b.expenseMinor).isEqualTo(4_000L)
    }

    @Test
    fun categoryAgainstCategory_inTheSamePeriod() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, date(10, 3), null)
        f.ledger.addExpense(f.cashId, f.transportId, 400L, date(10, 4), null)

        val result = f.ledger.compare(
            october.copy(categoryIds = setOf(f.foodId)),
            october.copy(categoryIds = setOf(f.transportId)),
            today
        )

        assertThat(result.a.expenseMinor).isEqualTo(1_000L)
        assertThat(result.b.expenseMinor).isEqualTo(400L)
        assertThat(result.expenseDiffMinor).isEqualTo(600L)
    }

    @Test
    fun presetPeriods_areResolvedAgainstToday() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, date(10, 3), null)
        f.ledger.addExpense(f.cashId, f.foodId, 300L, date(9, 3), null)

        val result = f.ledger.compare(
            OperationFilter(period = HistoryPeriod.THIS_MONTH),
            OperationFilter(period = HistoryPeriod.LAST_MONTH),
            today
        )

        assertThat(result.a.from).isEqualTo(date(10, 1))
        assertThat(result.a.to).isEqualTo(date(10, 31))
        assertThat(result.b.from).isEqualTo(date(9, 1))
        assertThat(result.b.to).isEqualTo(date(9, 30))
        assertThat(result.a.expenseMinor).isEqualTo(1_000L)
        assertThat(result.b.expenseMinor).isEqualTo(300L)
    }

    @Test
    fun allTime_runsFromTheFirstOperationToToday() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 100L, date(3, 7), null)
        f.ledger.addExpense(f.cashId, f.foodId, 200L, date(10, 3), null)

        val result = f.ledger.compare(OperationFilter(), october, today)

        assertThat(result.a.from).isEqualTo(date(3, 7))
        assertThat(result.a.to).isEqualTo(today)
        assertThat(result.a.expenseMinor).isEqualTo(300L)
    }

    @Test
    fun allTime_withoutOperations_isOneDay() = runTest {
        val f = fixture()

        val result = f.ledger.compare(OperationFilter(), october, today)

        assertThat(result.a.from).isEqualTo(today)
        assertThat(result.a.to).isEqualTo(today)
        assertThat(result.a.days).isEqualTo(1L)
    }

    @Test
    fun customSideWithoutDates_isTreatedAsAllTime() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 100L, date(3, 7), null)

        val result = f.ledger.compare(OperationFilter(period = HistoryPeriod.CUSTOM), october, today)

        assertThat(result.a.from).isEqualTo(date(3, 7))
        assertThat(result.a.expenseMinor).isEqualTo(100L)
    }
}
