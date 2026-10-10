package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class BudgetTest {

    private val today = LocalDate.of(2026, 3, 15)

    private suspend fun setup(): Triple<Ledger, Long, Long> {
        val ledger = Ledger(
            accountRepository = InMemoryAccountRepository(),
            categoryRepository = InMemoryCategoryRepository(),
            operationRepository = InMemoryOperationRepository()
        )
        ledger.completeOnboarding(listOf(NewAccount("Cash", 100_000L)), Currency.TMT)
        val accountId = ledger.accountBalances().first().account.id
        val categoryId = ledger.categories(CategoryKind.EXPENSE).first().id
        return Triple(ledger, accountId, categoryId)
    }

    @Test
    fun status_countsOnlyThisMonthExpensesOfTheCategory() = runTest {
        val (ledger, accountId, categoryId) = setup()
        ledger.setBudget(categoryId, 10_000L)
        ledger.addExpense(accountId, categoryId, 3_000L, today, null)
        ledger.addExpense(accountId, categoryId, 2_000L, today.minusDays(14), null)
        ledger.addExpense(accountId, categoryId, 9_000L, today.minusMonths(1), null)

        val status = ledger.budgetStatus(categoryId, today)!!

        assertThat(status.spentMinor).isEqualTo(5_000L)
        assertThat(status.remainingMinor).isEqualTo(5_000L)
        assertThat(status.isOver).isFalse()
        assertThat(status.isNear).isFalse()
    }

    @Test
    fun status_flagsNearAndOver() = runTest {
        val (ledger, accountId, categoryId) = setup()
        ledger.setBudget(categoryId, 10_000L)

        ledger.addExpense(accountId, categoryId, 8_500L, today, null)
        assertThat(ledger.budgetStatus(categoryId, today)!!.isNear).isTrue()

        ledger.addExpense(accountId, categoryId, 2_000L, today, null)
        assertThat(ledger.budgetStatus(categoryId, today)!!.isOver).isTrue()
    }

    @Test
    fun setBudget_nullOrZeroRemovesIt() = runTest {
        val (ledger, _, categoryId) = setup()
        ledger.setBudget(categoryId, 10_000L)
        assertThat(ledger.budgets()).containsKey(categoryId)

        ledger.setBudget(categoryId, null)

        assertThat(ledger.budgets()).isEmpty()
        assertThat(ledger.budgetStatuses(today)).isEmpty()
    }

    @Test
    fun setBudget_rejectsIncomeCategory() = runTest {
        val (ledger, _, _) = setup()
        val incomeId = ledger.categories(CategoryKind.INCOME).first().id

        val failure = runCatching { ledger.setBudget(incomeId, 1_000L) }

        assertThat(failure.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun budgets_travelInBackupAndResetClearsThem() = runTest {
        val (ledger, _, categoryId) = setup()
        ledger.setBudget(categoryId, 12_300L)
        val json = ledger.exportBackup()

        ledger.resetAll()
        assertThat(ledger.budgets()).isEmpty()

        ledger.importBackup(json)
        assertThat(ledger.budgets()).containsExactly(categoryId, 12_300L)
    }

    @Test
    fun backupWithoutBudgets_importsWithNone() = runTest {
        val (ledger, _, _) = setup()
        val json = """
            {"app":"dallet","schemaVersion":2,
             "accounts":[{"id":1,"name":"Cash","openingBalanceMinor":0}],
             "categories":[],"operations":[]}
        """.trimIndent()

        ledger.importBackup(json)

        assertThat(ledger.budgets()).isEmpty()
    }
}
