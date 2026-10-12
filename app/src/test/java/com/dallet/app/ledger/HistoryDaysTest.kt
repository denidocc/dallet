package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HistoryDaysTest {

    private val day1 = LocalDate.of(2026, 10, 9)
    private val day2 = LocalDate.of(2026, 10, 10)

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
        val salaryId: Long
    )

    private suspend fun fixture(): Fixture {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 100_000L, cardOpeningMinor = 100_000L)
        val balances = ledger.accountBalances()
        return Fixture(
            ledger = ledger,
            cashId = balances.first { it.account.name == Ledger.CASH_NAME }.account.id,
            cardId = balances.first { it.account.name == Ledger.CARD_NAME }.account.id,
            foodId = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }.id,
            salaryId = ledger.categories(CategoryKind.INCOME).first().id
        )
    }

    @Test
    fun emptyLedger_hasNoDays() = runTest {
        val f = fixture()

        val result = f.ledger.history()

        assertThat(result.days).isEmpty()
        assertThat(result.count).isEqualTo(0)
        assertThat(result.incomeMinor).isEqualTo(0L)
        assertThat(result.expenseMinor).isEqualTo(0L)
    }

    @Test
    fun operationsAreGroupedByDay_newestDayFirst() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, "a")
        f.ledger.addExpense(f.cashId, f.foodId, 2_000L, day2, "b")
        f.ledger.addExpense(f.cashId, f.foodId, 3_000L, day1, "c")

        val result = f.ledger.history()

        assertThat(result.days.map { it.date }).containsExactly(day2, day1).inOrder()
        assertThat(result.days[0].operations.map { it.comment }).containsExactly("b")
        assertThat(result.days[1].operations).hasSize(2)
    }

    @Test
    fun insideADay_newestTimeFirst_thenNewestId() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 100L, day1, "morning", LocalTime.of(8, 0))
        f.ledger.addExpense(f.cashId, f.foodId, 200L, day1, "evening", LocalTime.of(20, 30))
        f.ledger.addExpense(f.cashId, f.foodId, 300L, day1, "noTime-first")
        f.ledger.addExpense(f.cashId, f.foodId, 400L, day1, "noTime-second")

        val comments = f.ledger.history().days.single().operations.map { it.comment }

        assertThat(comments)
            .containsExactly("evening", "morning", "noTime-second", "noTime-first")
            .inOrder()
    }

    @Test
    fun dayTotals_countIncomeAndExpenseSeparately() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, null)
        f.ledger.addExpense(f.cardId, f.foodId, 2_500L, day1, null)
        f.ledger.addIncome(f.cardId, f.salaryId, 10_000L, day1, null)

        val day = f.ledger.history().days.single()

        assertThat(day.expenseMinor).isEqualTo(3_500L)
        assertThat(day.incomeMinor).isEqualTo(10_000L)
    }

    @Test
    fun transfers_areListedButNotCountedInTotals() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, null)
        f.ledger.addTransfer(f.cardId, f.cashId, 50_000L, day1, null)

        val result = f.ledger.history()
        val day = result.days.single()

        assertThat(day.operations).hasSize(2)
        assertThat(day.expenseMinor).isEqualTo(1_000L)
        assertThat(day.incomeMinor).isEqualTo(0L)
        assertThat(result.count).isEqualTo(2)
        assertThat(result.expenseMinor).isEqualTo(1_000L)
        assertThat(result.incomeMinor).isEqualTo(0L)
    }

    @Test
    fun resultTotals_sumAllDays() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, null)
        f.ledger.addExpense(f.cashId, f.foodId, 2_000L, day2, null)
        f.ledger.addIncome(f.cardId, f.salaryId, 7_000L, day2, null)

        val result = f.ledger.history()

        assertThat(result.count).isEqualTo(3)
        assertThat(result.expenseMinor).isEqualTo(3_000L)
        assertThat(result.incomeMinor).isEqualTo(7_000L)
    }

    @Test
    fun daysWithoutOperations_doNotAppear() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, LocalDate.of(2026, 10, 1), null)
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, LocalDate.of(2026, 10, 20), null)

        val dates = f.ledger.history().days.map { it.date }

        assertThat(dates).containsExactly(
            LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 10, 1)
        ).inOrder()
    }
}
