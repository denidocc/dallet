package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertFailsWith

class LedgerAccountManagementTest {

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    @Test
    fun createAccount_addsToBalances() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 1_000L, cardOpeningMinor = 0L)

        ledger.createAccount(name = "Копилка", openingBalanceMinor = 500L)

        val names = ledger.accountBalances().map { it.account.name to it.balanceMinor }
        assertThat(names).contains("Копилка" to 500L)
        assertThat(ledger.totalBalanceMinor()).isEqualTo(1_500L)
    }

    @Test
    fun updateAccount_renamesAndChangesOpeningBalance() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 1_000L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account

        ledger.updateAccount(
            accountId = cash.id,
            name = "Наличка",
            openingBalanceMinor = 2_000L
        )

        val updated = ledger.accountBalances().first { it.account.id == cash.id }
        assertThat(updated.account.name).isEqualTo("Наличка")
        assertThat(updated.balanceMinor).isEqualTo(2_000L)
    }

    @Test
    fun deleteAccount_withOperations_isRejected() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 5_000L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        ledger.addExpense(cash.id, food.id, 100L, LocalDate.of(2026, 7, 1), null)

        assertFailsWith<AccountHasOperationsException> {
            ledger.deleteAccount(cash.id)
        }
        assertThat(ledger.accountBalances().any { it.account.id == cash.id }).isTrue()
    }

    @Test
    fun deleteAccount_withoutOperations_removesIt() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 1_000L, cardOpeningMinor = 0L)
        val id = ledger.createAccount(name = "Временный", openingBalanceMinor = 100L)

        ledger.deleteAccount(id)

        assertThat(ledger.accountBalances().map { it.account.name }).doesNotContain("Временный")
        assertThat(ledger.totalBalanceMinor()).isEqualTo(1_000L)
    }
}
