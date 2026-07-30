package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertFailsWith

class LedgerCategoryManagementTest {

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    @Test
    fun createCategory_appearsInKindList() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)

        ledger.createCategory(
            name = "Кофе",
            kind = CategoryKind.EXPENSE,
            colorArgb = 0xFF6D4C41.toInt(),
            iconKey = "coffee"
        )

        assertThat(ledger.categories(CategoryKind.EXPENSE).map { it.name }).contains("Кофе")
    }

    @Test
    fun updateCategory_changesNameColorIcon() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }

        ledger.updateCategory(
            categoryId = food.id,
            name = "Продукты",
            colorArgb = 0xFF000000.toInt(),
            iconKey = "cart"
        )

        val updated = ledger.categories(CategoryKind.EXPENSE).first { it.id == food.id }
        assertThat(updated.name).isEqualTo("Продукты")
        assertThat(updated.colorArgb).isEqualTo(0xFF000000.toInt())
        assertThat(updated.iconKey).isEqualTo("cart")
    }

    @Test
    fun deleteCategory_withOperations_isRejected() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 5_000L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first().account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        ledger.addExpense(cash.id, food.id, 100L, LocalDate.of(2026, 7, 1), null)

        assertFailsWith<CategoryHasOperationsException> {
            ledger.deleteCategory(food.id)
        }
        assertThat(ledger.categories(CategoryKind.EXPENSE).any { it.id == food.id }).isTrue()
    }

    @Test
    fun deleteCategory_withoutOperations_removesIt() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        val id = ledger.createCategory(
            name = "Временная",
            kind = CategoryKind.INCOME,
            colorArgb = 0xFF123456.toInt(),
            iconKey = "temp"
        )

        ledger.deleteCategory(id)

        assertThat(ledger.categories(CategoryKind.INCOME).map { it.name }).doesNotContain("Временная")
    }
}
