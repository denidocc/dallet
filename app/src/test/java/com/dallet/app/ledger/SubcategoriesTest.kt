package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class SubcategoriesTest {
    private val day = LocalDate.of(2026, 10, 14)

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    private suspend fun ledgerWithFood(): Triple<Ledger, Long, Category> {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 100_000L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first().account.id
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        return Triple(ledger, cash, food)
    }

    private suspend fun Ledger.addSub(name: String, parent: Category): Long =
        createCategory(name, CategoryKind.EXPENSE, 0xFF123456.toInt(), "cart", parent.id)

    @Test
    fun subcategoryListsRightAfterItsParent() = runTest {
        val (ledger, _, food) = ledgerWithFood()
        ledger.addSub("Продукты", food)

        val names = ledger.categories(CategoryKind.EXPENSE).map { it.name }

        val foodIndex = names.indexOf("Еда")
        assertThat(names[foodIndex + 1]).isEqualTo("Продукты")
    }

    @Test
    fun displayNameOfASubcategoryIncludesTheParent() = runTest {
        val (ledger, _, food) = ledgerWithFood()
        val id = ledger.addSub("Продукты", food)

        assertThat(ledger.categoryLabels()[id]).isEqualTo("Еда › Продукты")
        assertThat(ledger.categoryLabels()[food.id]).isEqualTo("Еда")
    }

    @Test
    fun subcategoryNeedsAParentOfTheSameKind() = runTest {
        val (ledger, _, food) = ledgerWithFood()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                ledger.createCategory("Премия", CategoryKind.INCOME, 0xFF123456.toInt(), "gift", food.id)
            }
        }
    }

    @Test
    fun subcategoriesCannotNest() = runTest {
        val (ledger, _, food) = ledgerWithFood()
        val sub = ledger.addSub("Продукты", food)
        val subCategory = ledger.categories(CategoryKind.EXPENSE).first { it.id == sub }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.addSub("Овощи", subCategory) }
        }
    }

    @Test
    fun aCategoryWithSubcategoriesCannotBecomeOne() = runTest {
        val (ledger, _, food) = ledgerWithFood()
        ledger.addSub("Продукты", food)
        val transport = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Транспорт" }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.updateCategory(food.id, "Еда", food.colorArgb, food.iconKey, transport.id) }
        }
    }

    @Test
    fun aSubcategoryCanMoveToAnotherParentOrBecomeTopLevel() = runTest {
        val (ledger, _, food) = ledgerWithFood()
        val sub = ledger.addSub("Билеты", food)
        val transport = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Транспорт" }

        ledger.updateCategory(sub, "Билеты", 0xFF123456.toInt(), "cart", transport.id)
        assertThat(ledger.categoryLabels()[sub]).isEqualTo("Транспорт › Билеты")

        ledger.updateCategory(sub, "Билеты", 0xFF123456.toInt(), "cart", null)
        assertThat(ledger.categoryLabels()[sub]).isEqualTo("Билеты")
    }

    @Test
    fun parentWithSubcategoriesCannotBeDeleted() = runTest {
        val (ledger, _, food) = ledgerWithFood()
        ledger.addSub("Продукты", food)

        assertThrows(CategoryHasChildrenException::class.java) {
            runBlocking { ledger.deleteCategory(food.id) }
        }
    }

    @Test
    fun analyticsCountsASubcategoryTowardItsParent() = runTest {
        val (ledger, cash, food) = ledgerWithFood()
        val groceries = ledger.addSub("Продукты", food)
        ledger.addExpense(cash, food.id, 1_000L, day, null)
        ledger.addExpense(cash, groceries, 3_000L, day, null)

        val slice = ledger.analytics(AnalyticsPeriod.MONTH, day).expensesByCategory.single()

        assertThat(slice.categoryName).isEqualTo("Еда")
        assertThat(slice.amountMinor).isEqualTo(4_000L)
        assertThat(slice.directMinor).isEqualTo(1_000L)
        assertThat(slice.children.single().name).isEqualTo("Продукты")
        assertThat(slice.children.single().amountMinor).isEqualTo(3_000L)
    }

    @Test
    fun budgetOnTheParentCoversSubcategories() = runTest {
        val (ledger, cash, food) = ledgerWithFood()
        val groceries = ledger.addSub("Продукты", food)
        ledger.setBudget(food.id, 5_000L)
        ledger.addExpense(cash, groceries, 4_000L, day, null)

        val status = ledger.budgetStatus(groceries, day)!!

        assertThat(status.category.id).isEqualTo(food.id)
        assertThat(status.spentMinor).isEqualTo(4_000L)
    }

    @Test
    fun historyFilterByParentIncludesSubcategories() = runTest {
        val (ledger, cash, food) = ledgerWithFood()
        val groceries = ledger.addSub("Продукты", food)
        ledger.addExpense(cash, groceries, 3_000L, day, null)
        val transport = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Транспорт" }
        ledger.addExpense(cash, transport.id, 700L, day, null)

        val found = ledger.history(OperationFilter(categoryIds = setOf(food.id)))

        assertThat(found.count).isEqualTo(1)
    }

    @Test
    fun searchFindsASubcategoryByItsParentName() = runTest {
        val (ledger, cash, food) = ledgerWithFood()
        val groceries = ledger.addSub("Продукты", food)
        ledger.addExpense(cash, groceries, 3_000L, day, null)

        val found = ledger.history(OperationFilter(query = "еда"))

        assertThat(found.count).isEqualTo(1)
    }

    @Test
    fun backupRoundTripKeepsSubcategories() = runTest {
        val (source, cash, food) = ledgerWithFood()
        val groceries = source.addSub("Продукты", food)
        source.addExpense(cash, groceries, 3_000L, day, null)

        val target = newLedger()
        target.importBackup(source.exportBackup())

        assertThat(target.categoryLabels()).isEqualTo(source.categoryLabels())
        assertThat(target.categories(CategoryKind.EXPENSE)).isEqualTo(source.categories(CategoryKind.EXPENSE))
    }

    @Test
    fun backupWithANestedSubcategoryIsRejected() = runTest {
        val (ledger, _, food) = ledgerWithFood()
        val groceries = ledger.addSub("Продукты", food)
        val nested = ledger.createCategory("Овощи", CategoryKind.EXPENSE, 0xFF123456.toInt(), "cart", food.id)
        val data = BackupCodec.decode(ledger.exportBackup())
        val broken = data.copy(
            categories = data.categories.map { if (it.id == nested) it.copy(parentId = groceries) else it }
        )

        assertThrows(InvalidBackupException::class.java) { BackupCodec.validate(broken) }
    }
}
