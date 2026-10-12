package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class HistorySearchTest {

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
        val transportId: Long,
        val salaryId: Long
    )

    private suspend fun fixture(): Fixture {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 1_000_000L, cardOpeningMinor = 1_000_000L)
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

    private suspend fun Ledger.found(query: String): List<String?> =
        history(OperationFilter(query = query)).days.flatMap { it.operations }.map { it.comment }

    @Test
    fun emptyQuery_returnsEverything() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, "хлеб")
        f.ledger.addExpense(f.cashId, f.foodId, 2_000L, day2, "молоко")

        assertThat(f.ledger.history(OperationFilter(query = "")).count).isEqualTo(2)
        assertThat(f.ledger.history(OperationFilter(query = "   ")).count).isEqualTo(2)
    }

    @Test
    fun matchesComment_ignoringCase_bySubstring() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, "Свежий ХЛЕБ")
        f.ledger.addExpense(f.cashId, f.foodId, 2_000L, day1, "молоко")

        assertThat(f.ledger.found("хлеб")).containsExactly("Свежий ХЛЕБ")
        assertThat(f.ledger.found("ЛЕБ")).containsExactly("Свежий ХЛЕБ")
    }

    @Test
    fun matchesCategoryName() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, "a")
        f.ledger.addExpense(f.cashId, f.transportId, 2_000L, day1, "b")

        assertThat(f.ledger.found("еда")).containsExactly("a")
        assertThat(f.ledger.found("транспорт")).containsExactly("b")
    }

    @Test
    fun matchesAccountName() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, "paidCash")
        f.ledger.addExpense(f.cardId, f.foodId, 2_000L, day1, "paidCard")

        assertThat(f.ledger.found("налич")).containsExactly("paidCash")
        assertThat(f.ledger.found("карта")).containsExactly("paidCard")
    }

    @Test
    fun transfer_matchesEitherAccountName() = runTest {
        val f = fixture()
        f.ledger.addTransfer(f.cardId, f.cashId, 5_000L, day1, "move")
        f.ledger.addExpense(f.cardId, f.foodId, 1_000L, day1, "food")

        assertThat(f.ledger.found("налич")).containsExactly("move")
        assertThat(f.ledger.found("карта")).containsExactly("move", "food")
    }

    @Test
    fun matchesAmountText_withDotOrComma() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 12_000L, day1, "a")
        f.ledger.addExpense(f.cashId, f.foodId, 12_050L, day1, "b")
        f.ledger.addExpense(f.cashId, f.foodId, 700L, day1, "c")

        assertThat(f.ledger.found("120")).containsExactly("a", "b")
        assertThat(f.ledger.found("120.5")).containsExactly("b")
        assertThat(f.ledger.found("120,5")).containsExactly("b")
        assertThat(f.ledger.found("7.00")).containsExactly("c")
    }

    @Test
    fun noMatch_givesEmptyResult() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, "хлеб")

        val result = f.ledger.history(OperationFilter(query = "nothing like this"))

        assertThat(result.days).isEmpty()
        assertThat(result.count).isEqualTo(0)
        assertThat(result.expenseMinor).isEqualTo(0L)
        assertThat(result.incomeMinor).isEqualTo(0L)
    }

    @Test
    fun queryIsTrimmed() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, "хлеб")

        assertThat(f.ledger.found("  хлеб  ")).containsExactly("хлеб")
    }

    @Test
    fun daysAndTotals_coverOnlyWhatWasFound() = runTest {
        val f = fixture()
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, "чай")
        f.ledger.addExpense(f.cashId, f.transportId, 9_000L, day1, "такси")
        f.ledger.addExpense(f.cashId, f.foodId, 2_000L, day2, "чай")
        f.ledger.addIncome(f.cardId, f.salaryId, 50_000L, day2, "премия")

        val result = f.ledger.history(OperationFilter(query = "чай"))

        assertThat(result.count).isEqualTo(2)
        assertThat(result.expenseMinor).isEqualTo(3_000L)
        assertThat(result.incomeMinor).isEqualTo(0L)
        assertThat(result.days.map { it.date }).containsExactly(day2, day1).inOrder()
        assertThat(result.days[1].expenseMinor).isEqualTo(1_000L)
    }

    @Test
    fun transfersFound_areNotCountedInTotals() = runTest {
        val f = fixture()
        f.ledger.addTransfer(f.cardId, f.cashId, 5_000L, day1, "перевод наличные")
        f.ledger.addExpense(f.cashId, f.foodId, 1_000L, day1, "хлеб")

        val result = f.ledger.history(OperationFilter(query = "налич"))

        assertThat(result.count).isEqualTo(2)
        assertThat(result.expenseMinor).isEqualTo(1_000L)
        assertThat(result.incomeMinor).isEqualTo(0L)
    }
}
