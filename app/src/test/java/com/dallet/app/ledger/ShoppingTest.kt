package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ShoppingTest {

    private val now = LocalDateTime.of(2026, 10, 9, 14, 30)

    private class Env {
        val shoppingRepository = InMemoryShoppingRepository()
        val ledger = Ledger(
            accountRepository = InMemoryAccountRepository(),
            categoryRepository = InMemoryCategoryRepository(),
            operationRepository = InMemoryOperationRepository(),
            shoppingRepository = shoppingRepository
        )
        val service = ShoppingService(ledger, shoppingRepository)
    }

    private suspend fun readyEnv(): Env {
        val env = Env()
        env.ledger.completeOnboarding(cashOpeningMinor = 100_000L, cardOpeningMinor = 0L)
        return env
    }

    private suspend fun Env.cashId() =
        ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account.id

    private suspend fun Env.foodId() =
        ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }.id

    @Test
    fun lineTotal_isQuantityTimesUnitPrice_roundedHalfUp() {
        val item = ShoppingItem(
            id = 1, listId = 1, name = "Помидор", unit = QuantityUnit.KILOGRAM,
            quantityThousandths = 2_000, unitPriceMinor = 1_250, checked = false, position = 0
        )
        assertThat(item.lineTotalMinor).isEqualTo(2_500L)

        val fractional = item.copy(quantityThousandths = 1_500, unitPriceMinor = 333)
        assertThat(fractional.lineTotalMinor).isEqualTo(500L)
    }

    @Test
    fun quantity_parsesAndFormats() {
        assertThat(QuantityFormat.parse("1.5")).isEqualTo(1_500L)
        assertThat(QuantityFormat.parse("2")).isEqualTo(2_000L)
        assertThat(QuantityFormat.parse("0,25")).isEqualTo(250L)
        assertThat(QuantityFormat.format(1_500L)).isEqualTo("1.5")
        assertThat(QuantityFormat.format(2_000L)).isEqualTo("2")
        assertThat(QuantityFormat.format(250L)).isEqualTo("0.25")
    }

    @Test
    fun addItem_putsNameIntoCatalogOncePerNameAndUnit() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Базар", now)

        env.service.addItem(listId, "Помидор", QuantityUnit.KILOGRAM, 1_000, 0)
        env.service.addItem(listId, "помидор", QuantityUnit.KILOGRAM, 2_000, 0)
        env.service.addItem(listId, "Помидор", QuantityUnit.PIECE, 3_000, 0)

        assertThat(env.service.products().map { it.name to it.unit })
            .containsExactly("Помидор" to QuantityUnit.KILOGRAM, "Помидор" to QuantityUnit.PIECE)
    }

    @Test
    fun suggestions_prefixFirstThenWordPrefixThenContains() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Тест", now)
        listOf("Красная помада", "Помидор", "Апельсин", "Помада", "Спомин").forEach {
            env.service.addItem(listId, it, QuantityUnit.PIECE, 1_000, 0)
        }

        val names = env.service.suggestions("Пом").map { it.name }

        assertThat(names).containsExactly("Помада", "Помидор", "Красная помада", "Спомин").inOrder()
        assertThat(env.service.suggestions("  ")).isEmpty()
    }

    @Test
    fun savePrices_remembersLastPrice_andReplacesSameDayPoint() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Цены", now)
        env.service.addItem(listId, "Молоко", QuantityUnit.LITER, 1_000, 1_800)

        env.service.savePrices(listId, now)
        val item = env.service.detail(listId)!!.items.single()
        env.service.updateItem(item.copy(unitPriceMinor = 1_900))
        env.service.savePrices(listId, now.plusHours(2))

        val product = env.service.products().single()
        assertThat(product.lastPriceMinor).isEqualTo(1_900L)
        val points = env.service.pricePoints(product.id)
        assertThat(points).hasSize(1)
        assertThat(points.single().unitPriceMinor).isEqualTo(1_900L)

        env.service.savePrices(listId, now.plusDays(1))
        assertThat(env.service.pricePoints(product.id)).hasSize(2)
    }

    @Test
    fun savePrices_skipsItemsWithoutPrice() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Без цен", now)
        env.service.addItem(listId, "Хлеб", QuantityUnit.PIECE, 1_000, 0)

        val saved = env.service.savePrices(listId, now)

        assertThat(saved).isEqualTo(0)
        assertThat(env.service.products().single().lastPriceMinor).isNull()
    }

    @Test
    fun closePurchase_createsOneExpenseForCheckedItems() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Базар", now)
        val tomato = env.service.addItem(listId, "Помидор", QuantityUnit.KILOGRAM, 2_000, 1_250)
        env.service.addItem(listId, "Сыр", QuantityUnit.PACK, 1_000, 4_000)
        val bread = env.service.addItem(listId, "Хлеб", QuantityUnit.PIECE, 3_000, 300)
        env.service.setChecked(tomato, true)
        env.service.setChecked(bread, true)
        val before = env.ledger.totalBalanceMinor()

        val result = env.service.closePurchase(
            listId = listId,
            accountId = env.cashId(),
            categoryId = env.foodId(),
            date = LocalDate.of(2026, 10, 9),
            time = LocalTime.of(14, 30),
            now = now,
            carryOverUnchecked = false
        )

        assertThat(result.totalMinor).isEqualTo(3_400L)
        val operation = env.ledger.operation(result.operationId!!)!!
        assertThat(operation.amountMinor).isEqualTo(3_400L)
        assertThat(operation.comment).isEqualTo("Базар")
        assertThat(operation.time).isEqualTo(LocalTime.of(14, 30))
        assertThat(env.ledger.totalBalanceMinor()).isEqualTo(before - 3_400L)
        val list = env.service.detail(listId)!!.list
        assertThat(list.isClosed).isTrue()
        assertThat(list.operationId).isEqualTo(result.operationId)
        assertThat(env.service.openLists()).isEmpty()
        assertThat(env.service.archivedLists().map { it.list.id }).containsExactly(listId)
    }

    @Test
    fun closePurchase_remembersPricesOfCheckedItems() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Цены", now)
        val milk = env.service.addItem(listId, "Молоко", QuantityUnit.LITER, 1_000, 1_800)
        env.service.addItem(listId, "Сыр", QuantityUnit.PACK, 1_000, 4_000)
        env.service.setChecked(milk, true)

        env.service.closePurchase(
            listId, env.cashId(), env.foodId(), LocalDate.of(2026, 10, 9), null, now, false
        )

        val prices = env.service.products().associate { it.name to it.lastPriceMinor }
        assertThat(prices["Молоко"]).isEqualTo(1_800L)
        assertThat(prices["Сыр"]).isNull()
    }

    @Test
    fun closePurchase_carryOverMovesUncheckedItemsToNewList() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Базар", now)
        val tomato = env.service.addItem(listId, "Помидор", QuantityUnit.KILOGRAM, 1_000, 1_000)
        env.service.addItem(listId, "Сыр", QuantityUnit.PACK, 1_000, 4_000)
        env.service.setChecked(tomato, true)

        val result = env.service.closePurchase(
            listId, env.cashId(), env.foodId(), LocalDate.of(2026, 10, 9), null, now, true
        )

        val carried = env.service.detail(result.carriedListId!!)!!
        assertThat(carried.items.map { it.name }).containsExactly("Сыр")
        assertThat(carried.items.single().checked).isFalse()
        assertThat(carried.list.isClosed).isFalse()
        assertThat(env.service.openLists().map { it.list.id }).containsExactly(result.carriedListId)
    }

    @Test
    fun closePurchase_withZeroTotal_closesWithoutExpense() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Без цен", now)
        val item = env.service.addItem(listId, "Хлеб", QuantityUnit.PIECE, 1_000, 0)
        env.service.setChecked(item, true)
        val before = env.ledger.totalBalanceMinor()

        val result = env.service.closePurchase(
            listId, env.cashId(), env.foodId(), LocalDate.of(2026, 10, 9), null, now, false
        )

        assertThat(result.operationId).isNull()
        assertThat(env.ledger.totalBalanceMinor()).isEqualTo(before)
        assertThat(env.service.detail(listId)!!.list.isClosed).isTrue()
    }

    @Test
    fun closePurchase_requiresCheckedItems_andOpenList() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Пусто", now)
        env.service.addItem(listId, "Хлеб", QuantityUnit.PIECE, 1_000, 300)
        val cash = env.cashId()
        val food = env.foodId()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                env.service.closePurchase(
                    listId, cash, food, LocalDate.of(2026, 10, 9), null, now, false
                )
            }
        }

        val item = env.service.detail(listId)!!.items.single()
        env.service.setChecked(item.id, true)
        env.service.closePurchase(
            listId, cash, food, LocalDate.of(2026, 10, 9), null, now, false
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                env.service.closePurchase(
                    listId, cash, food, LocalDate.of(2026, 10, 9), null, now, false
                )
            }
        }
    }

    @Test
    fun closedList_cannotBeEdited() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Закрыт", now)
        val itemId = env.service.addItem(listId, "Хлеб", QuantityUnit.PIECE, 1_000, 300)
        env.service.setChecked(itemId, true)
        env.service.closePurchase(
            listId, env.cashId(), env.foodId(), LocalDate.of(2026, 10, 9), null, now, false
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                env.service.addItem(listId, "Сыр", QuantityUnit.PACK, 1_000, 100)
            }
        }
    }

    @Test
    fun deleteList_keepsCreatedExpense() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Базар", now)
        val itemId = env.service.addItem(listId, "Хлеб", QuantityUnit.PIECE, 2_000, 300)
        env.service.setChecked(itemId, true)
        val result = env.service.closePurchase(
            listId, env.cashId(), env.foodId(), LocalDate.of(2026, 10, 9), null, now, false
        )

        env.service.deleteList(listId)

        assertThat(env.service.detail(listId)).isNull()
        assertThat(env.ledger.operation(result.operationId!!)).isNotNull()
    }

    @Test
    fun items_checkedGoLast_andKeepInsertionOrder() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Порядок", now)
        val a = env.service.addItem(listId, "A", QuantityUnit.PIECE, 1_000, 0)
        env.service.addItem(listId, "B", QuantityUnit.PIECE, 1_000, 0)
        env.service.addItem(listId, "C", QuantityUnit.PIECE, 1_000, 0)
        env.service.setChecked(a, true)

        assertThat(env.service.detail(listId)!!.items.map { it.name })
            .containsExactly("B", "C", "A").inOrder()
    }

    @Test
    fun backup_roundTripKeepsShoppingData() = runTest {
        val source = readyEnv()
        val listId = source.service.createList("Базар", now)
        val itemId = source.service.addItem(listId, "Молоко", QuantityUnit.LITER, 1_500, 1_800)
        source.service.setChecked(itemId, true)
        source.service.savePrices(listId, LocalDateTime.now())

        val target = Env()
        target.ledger.importBackup(source.ledger.exportBackup())

        val detail = target.service.detail(listId)!!
        assertThat(detail.list.title).isEqualTo("Базар")
        assertThat(detail.items.single().name).isEqualTo("Молоко")
        assertThat(detail.items.single().quantityThousandths).isEqualTo(1_500L)
        assertThat(detail.items.single().checked).isTrue()
        val product = target.service.products().single()
        assertThat(product.lastPriceMinor).isEqualTo(1_800L)
        assertThat(target.service.pricePoints(product.id)).hasSize(1)
    }

    @Test
    fun backupWithoutShoppingSection_importsEmptyShopping() = runTest {
        val json = """
            {"app":"dallet","schemaVersion":1,
             "accounts":[{"id":1,"name":"Наличные","openingBalanceMinor":0}],
             "categories":[],"operations":[]}
        """.trimIndent()
        val env = Env()

        env.ledger.importBackup(json)

        assertThat(env.service.openLists()).isEmpty()
        assertThat(env.service.products()).isEmpty()
    }

    @Test
    fun setChecked_savesPriceOfBoughtItem_onlyWhenChecked() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Цены", now)
        val milk = env.service.addItem(listId, "Молоко", QuantityUnit.LITER, 1_000, 1_800)
        val bread = env.service.addItem(listId, "Хлеб", QuantityUnit.PIECE, 1_000, 0)

        env.service.setChecked(milk, true, now)
        env.service.setChecked(bread, true, now)

        val prices = env.service.products().associate { it.name to it.lastPriceMinor }
        assertThat(prices["Молоко"]).isEqualTo(1_800L)
        assertThat(prices["Хлеб"]).isNull()
        val milkProduct = env.service.products().first { it.name == "Молоко" }
        assertThat(env.service.pricePoints(milkProduct.id)).hasSize(1)

        env.service.setChecked(milk, false, now.plusDays(1))
        assertThat(env.service.pricePoints(milkProduct.id)).hasSize(1)
    }

    @Test
    fun updateListInfo_changesTitleAndNote() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Старое", now)

        env.service.updateListInfo(listId, "  Базар  ", "для дачи")

        val list = env.service.detail(listId)!!.list
        assertThat(list.title).isEqualTo("Базар")
        assertThat(list.note).isEqualTo("для дачи")
    }

    @Test
    fun closePurchase_expenseCommentIncludesNote() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Базар", now)
        env.service.updateListInfo(listId, "Базар", "для дачи")
        val item = env.service.addItem(listId, "Хлеб", QuantityUnit.PIECE, 1_000, 300)
        env.service.setChecked(item, true, now)

        val withNote = env.service.closePurchase(
            listId, env.cashId(), env.foodId(), LocalDate.of(2026, 10, 9), null, now, false
        )
        assertThat(env.ledger.operation(withNote.operationId!!)!!.comment)
            .isEqualTo("Базар — для дачи")

        val plainId = env.service.createList("Просто", now)
        val plainItem = env.service.addItem(plainId, "Сыр", QuantityUnit.PACK, 1_000, 500)
        env.service.setChecked(plainItem, true, now)
        val plain = env.service.closePurchase(
            plainId, env.cashId(), env.foodId(), LocalDate.of(2026, 10, 9), null, now, false
        )
        assertThat(env.ledger.operation(plain.operationId!!)!!.comment).isEqualTo("Просто")
    }

    @Test
    fun backup_roundTripKeepsListNote() = runTest {
        val source = readyEnv()
        val listId = source.service.createList("Базар", now)
        source.service.updateListInfo(listId, "Базар", "для дачи")

        val target = Env()
        target.ledger.importBackup(source.ledger.exportBackup())

        assertThat(target.service.detail(listId)!!.list.note).isEqualTo("для дачи")
    }

    @Test
    fun reset_clearsShoppingData() = runTest {
        val env = readyEnv()
        val listId = env.service.createList("Базар", now)
        env.service.addItem(listId, "Хлеб", QuantityUnit.PIECE, 1_000, 300)

        env.ledger.resetAll()

        assertThat(env.service.openLists()).isEmpty()
        assertThat(env.service.products()).isEmpty()
    }
}
