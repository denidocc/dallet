package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class LedgerExpenseTest {

    private fun newLedger(): Ledger {
        return Ledger(
            accountRepository = InMemoryAccountRepository(),
            categoryRepository = InMemoryCategoryRepository(),
            operationRepository = InMemoryOperationRepository()
        )
    }

    @Test
    fun afterOnboarding_seedCategoriesIncludeIncomeAndExpense() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 10_000L, cardOpeningMinor = 0L)

        val income = ledger.categories(CategoryKind.INCOME)
        val expense = ledger.categories(CategoryKind.EXPENSE)

        assertThat(income.map { it.name }).contains("Зарплата")
        assertThat(expense.map { it.name }).containsAtLeast("Еда", "Транспорт", "Дом")
    }

    @Test
    fun recordingExpense_decreasesAccountAndTotalBalance() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 10_000L, cardOpeningMinor = 5_000L)
        val cash = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }

        ledger.addExpense(
            accountId = cash.id,
            categoryId = food.id,
            amountMinor = 1_500L, // 15.00
            date = LocalDate.of(2026, 7, 30),
            comment = "обед"
        )

        val balances = ledger.accountBalances().associate { it.account.name to it.balanceMinor }
        assertThat(balances[Ledger.CASH_NAME]).isEqualTo(8_500L)
        assertThat(balances[Ledger.CARD_NAME]).isEqualTo(5_000L)
        assertThat(ledger.totalBalanceMinor()).isEqualTo(13_500L)
    }

    @Test
    fun recentExpenses_returnsLastTenNewestFirst() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 100_000L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first().account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }

        repeat(12) { index ->
            ledger.addExpense(
                accountId = cash.id,
                categoryId = food.id,
                amountMinor = 100L + index,
                date = LocalDate.of(2026, 7, 1).plusDays(index.toLong()),
                comment = "n$index"
            )
        }

        val recent = ledger.recentExpenses(limit = 10)
        assertThat(recent).hasSize(10)
        assertThat(recent.map { it.comment }).containsExactly(
            "n11", "n10", "n9", "n8", "n7", "n6", "n5", "n4", "n3", "n2"
        ).inOrder()
        assertThat(recent.all { it.kind == OperationKind.EXPENSE }).isTrue()
    }
}
