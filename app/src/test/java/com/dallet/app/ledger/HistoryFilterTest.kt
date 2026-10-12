package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class HistoryFilterTest {

    private val day = LocalDate.of(2026, 10, 9)

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

    private suspend fun Fixture.addSample() {
        ledger.addExpense(cashId, foodId, 1_000L, day, "food-cash")
        ledger.addExpense(cardId, foodId, 2_000L, day, "food-card")
        ledger.addExpense(cardId, transportId, 3_000L, day, "taxi-card")
        ledger.addIncome(cardId, salaryId, 40_000L, day, "salary")
        ledger.addTransfer(cardId, cashId, 5_000L, day, "move")
    }

    private suspend fun Ledger.comments(filter: OperationFilter): List<String?> =
        history(filter).days.flatMap { it.operations }.map { it.comment }

    @Test
    fun defaultFilter_isEmpty_andOtherFiltersAreNot() {
        assertThat(OperationFilter().isEmpty).isTrue()
        assertThat(OperationFilter(query = " ").isEmpty).isTrue()
        assertThat(OperationFilter(kind = OperationKind.EXPENSE).isEmpty).isFalse()
        assertThat(OperationFilter(accountIds = setOf(1L)).isEmpty).isFalse()
        assertThat(OperationFilter(categoryIds = setOf(1L)).isEmpty).isFalse()
    }

    @Test
    fun kind_expense() = runTest {
        val f = fixture().also { it.addSample() }

        val found = f.ledger.comments(OperationFilter(kind = OperationKind.EXPENSE))

        assertThat(found).containsExactly("food-cash", "food-card", "taxi-card")
    }

    @Test
    fun kind_income() = runTest {
        val f = fixture().also { it.addSample() }

        val found = f.ledger.comments(OperationFilter(kind = OperationKind.INCOME))

        assertThat(found).containsExactly("salary")
    }

    @Test
    fun kind_transfer() = runTest {
        val f = fixture().also { it.addSample() }

        val found = f.ledger.comments(OperationFilter(kind = OperationKind.TRANSFER))

        assertThat(found).containsExactly("move")
    }

    @Test
    fun singleAccount_includesTransfersTouchingIt() = runTest {
        val f = fixture().also { it.addSample() }

        val cash = f.ledger.comments(OperationFilter(accountIds = setOf(f.cashId)))

        assertThat(cash).containsExactly("food-cash", "move")
    }

    @Test
    fun severalAccounts_areCombinedWithOr() = runTest {
        val f = fixture().also { it.addSample() }

        val both = f.ledger.comments(OperationFilter(accountIds = setOf(f.cashId, f.cardId)))

        assertThat(both).hasSize(5)
    }

    @Test
    fun severalCategories_areCombinedWithOr_andSkipTransfers() = runTest {
        val f = fixture().also { it.addSample() }

        val found = f.ledger.comments(
            OperationFilter(categoryIds = setOf(f.foodId, f.transportId))
        )

        assertThat(found).containsExactly("food-cash", "food-card", "taxi-card")
    }

    @Test
    fun categories_areIgnoredForTransferKind() = runTest {
        val f = fixture().also { it.addSample() }

        val found = f.ledger.comments(
            OperationFilter(kind = OperationKind.TRANSFER, categoryIds = setOf(f.foodId))
        )

        assertThat(found).containsExactly("move")
    }

    @Test
    fun kindAndCategoryOfAnotherKind_findNothing() = runTest {
        val f = fixture().also { it.addSample() }

        val found = f.ledger.comments(
            OperationFilter(kind = OperationKind.EXPENSE, categoryIds = setOf(f.salaryId))
        )

        assertThat(found).isEmpty()
    }

    @Test
    fun allRestrictions_combineWithAnd_includingText() = runTest {
        val f = fixture().also { it.addSample() }

        val found = f.ledger.comments(
            OperationFilter(
                query = "food",
                kind = OperationKind.EXPENSE,
                accountIds = setOf(f.cardId),
                categoryIds = setOf(f.foodId)
            )
        )

        assertThat(found).containsExactly("food-card")
    }

    @Test
    fun totalsAndCount_followTheFilter() = runTest {
        val f = fixture().also { it.addSample() }

        val result = f.ledger.history(OperationFilter(accountIds = setOf(f.cardId)))

        assertThat(result.count).isEqualTo(4)
        assertThat(result.expenseMinor).isEqualTo(5_000L)
        assertThat(result.incomeMinor).isEqualTo(40_000L)
    }
}
