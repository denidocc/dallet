package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class LedgerEditDeleteTest {

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    @Test
    fun editingExpense_recalculatesBalances() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 10_000L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first().account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        val id = ledger.addExpense(
            accountId = cash.id,
            categoryId = food.id,
            amountMinor = 1_000L,
            date = LocalDate.of(2026, 7, 1),
            comment = "a"
        )

        ledger.updateExpense(
            operationId = id,
            accountId = cash.id,
            categoryId = food.id,
            amountMinor = 2_500L,
            date = LocalDate.of(2026, 7, 2),
            comment = "b"
        )

        assertThat(ledger.totalBalanceMinor()).isEqualTo(7_500L)
        val updated = ledger.operation(id)
        assertThat(updated?.amountMinor).isEqualTo(2_500L)
        assertThat(updated?.comment).isEqualTo("b")
    }

    @Test
    fun deletingExpense_restoresBalance() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 10_000L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first().account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        val id = ledger.addExpense(
            accountId = cash.id,
            categoryId = food.id,
            amountMinor = 1_500L,
            date = LocalDate.of(2026, 7, 1),
            comment = null
        )

        ledger.deleteOperation(id)

        assertThat(ledger.totalBalanceMinor()).isEqualTo(10_000L)
        assertThat(ledger.operation(id)).isNull()
        assertThat(ledger.allOperations()).isEmpty()
    }

    @Test
    fun allOperations_includesAllKindsNewestFirst() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 20_000L, cardOpeningMinor = 20_000L)
        val cash = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }

        ledger.addExpense(cash.id, food.id, 100L, LocalDate.of(2026, 7, 1), null)
        ledger.addIncome(card.id, salary.id, 200L, LocalDate.of(2026, 7, 3), null)
        ledger.addTransfer(card.id, cash.id, 50L, LocalDate.of(2026, 7, 2), null)

        val ops = ledger.allOperations()
        assertThat(ops.map { it.kind }).containsExactly(
            OperationKind.INCOME,
            OperationKind.TRANSFER,
            OperationKind.EXPENSE
        ).inOrder()
    }
}
