package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PurchaseExpenseTest {

    private val closeTime = LocalDateTime.of(2026, 10, 9, 14, 30)
    private val date = LocalDate.of(2026, 10, 9)

    private class Env {
        val shoppingRepository = InMemoryShoppingRepository()
        val operationRepository = InMemoryOperationRepository()
        val ledger = Ledger(
            accountRepository = InMemoryAccountRepository(),
            categoryRepository = InMemoryCategoryRepository(),
            operationRepository = operationRepository,
            shoppingRepository = shoppingRepository
        )
        val service = ShoppingService(ledger, shoppingRepository)
    }

    private suspend fun readyEnv(): Env {
        val env = Env()
        env.ledger.completeOnboarding(cashOpeningMinor = 100_000L, cardOpeningMinor = 50_000L)
        return env
    }

    private suspend fun Env.cashId() =
        ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account.id

    private suspend fun Env.cardId() =
        ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account.id

    private suspend fun Env.foodId() =
        ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }.id

    private suspend fun Env.transportId() =
        ledger.categories(CategoryKind.EXPENSE).first { it.name == "Транспорт" }.id

    /** Closes a one-item purchase at [price]; returns the list id. */
    private suspend fun Env.closedPurchase(price: Long = 1_000L, title: String = "Базар"): Long {
        val listId = service.createList(title, closeTime.minusHours(1))
        service.addItem(listId, "Помидор", QuantityUnit.KILOGRAM, 1_000L, price)
        service.setChecked(service.detail(listId)!!.items.single().id, true, closeTime.minusMinutes(5))
        service.closePurchase(
            listId = listId,
            accountId = cashId(),
            categoryId = foodId(),
            date = date,
            time = null,
            now = closeTime,
            carryOverUnchecked = false
        )
        return listId
    }

    private suspend fun Env.expenseOf(listId: Long): Long = service.detail(listId)!!.list.operationId!!

    // --- amount is locked -----------------------------------------------------------------

    @Test
    fun changingTheAmountOfAPurchaseExpense_isRejected() = runTest {
        val env = readyEnv()
        val operationId = env.expenseOf(env.closedPurchase(price = 1_000L))

        val failure = runCatching {
            env.ledger.updateExpense(
                operationId = operationId,
                accountId = env.cashId(),
                categoryId = env.foodId(),
                amountMinor = 9_999L,
                date = date,
                comment = null
            )
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(PurchaseExpenseLockedException::class.java)
        assertThat(env.ledger.operation(operationId)!!.amountMinor).isEqualTo(1_000L)
    }

    @Test
    fun theOtherFieldsOfAPurchaseExpense_canStillBeEdited() = runTest {
        val env = readyEnv()
        val operationId = env.expenseOf(env.closedPurchase(price = 1_000L))

        env.ledger.updateExpense(
            operationId = operationId,
            accountId = env.cardId(),
            categoryId = env.transportId(),
            amountMinor = 1_000L,
            date = LocalDate.of(2026, 10, 8),
            comment = "новый комментарий",
            time = LocalTime.of(9, 15)
        )

        val updated = env.ledger.operation(operationId)!!
        assertThat(updated.accountId).isEqualTo(env.cardId())
        assertThat(updated.categoryId).isEqualTo(env.transportId())
        assertThat(updated.date).isEqualTo(LocalDate.of(2026, 10, 8))
        assertThat(updated.comment).isEqualTo("новый комментарий")
        assertThat(updated.time).isEqualTo(LocalTime.of(9, 15))
        assertThat(updated.amountMinor).isEqualTo(1_000L)
    }

    @Test
    fun anOrdinaryExpense_stillAcceptsANewAmount() = runTest {
        val env = readyEnv()
        env.closedPurchase()
        val id = env.ledger.addExpense(env.cashId(), env.foodId(), 500L, date, "кофе")

        env.ledger.updateExpense(id, env.cashId(), env.foodId(), 800L, date, "кофе")

        assertThat(env.ledger.operation(id)!!.amountMinor).isEqualTo(800L)
    }

    @Test
    fun purchaseOf_namesThePurchaseBehindAnExpense() = runTest {
        val env = readyEnv()
        val listId = env.closedPurchase(title = "Субботний базар")
        val plain = env.ledger.addExpense(env.cashId(), env.foodId(), 500L, date, "кофе")

        assertThat(env.ledger.purchaseOf(env.expenseOf(listId))?.title).isEqualTo("Субботний базар")
        assertThat(env.ledger.purchaseOf(plain)).isNull()
        assertThat(env.ledger.purchaseOf(404L)).isNull()
    }

    @Test
    fun afterReopening_theExpenseIsGone_andNothingIsLocked() = runTest {
        val env = readyEnv()
        val listId = env.closedPurchase()
        val operationId = env.expenseOf(listId)

        env.service.reopenPurchase(listId)

        assertThat(env.ledger.purchaseOf(operationId)).isNull()
        assertThat(env.ledger.operation(operationId)).isNull()
    }

    // --- deleting the expense by hand -----------------------------------------------------

    @Test
    fun deletingThePurchaseExpense_keepsThePurchaseClosed_withoutALink() = runTest {
        val env = readyEnv()
        val listId = env.closedPurchase()
        val operationId = env.expenseOf(listId)

        env.ledger.deleteOperation(operationId)

        val detail = env.service.detail(listId)!!
        assertThat(detail.list.isClosed).isTrue()
        assertThat(detail.list.operationId).isNull()
        assertThat(env.service.archivedLists().map { it.list.id }).contains(listId)
        assertThat(env.ledger.totalBalanceMinor()).isEqualTo(150_000L)
    }

    @Test
    fun deletingAnUnrelatedExpense_leavesPurchasesAlone() = runTest {
        val env = readyEnv()
        val listId = env.closedPurchase()
        val operationId = env.expenseOf(listId)
        val plain = env.ledger.addExpense(env.cashId(), env.foodId(), 500L, date, "кофе")

        env.ledger.deleteOperation(plain)

        assertThat(env.service.detail(listId)!!.list.operationId).isEqualTo(operationId)
    }

    // --- the "expense deleted" mark ---------------------------------------------------------

    @Test
    fun expenseDeleted_isTrueOnlyWhenARealExpenseWasRemoved() = runTest {
        val env = readyEnv()
        val withExpense = env.closedPurchase(price = 1_000L, title = "A")
        val deleted = env.closedPurchase(price = 1_000L, title = "B")
        val zeroTotal = env.closedPurchase(price = 0L, title = "C")
        val open = env.service.createList("D", closeTime)

        env.ledger.deleteOperation(env.expenseOf(deleted))

        assertThat(env.service.detail(withExpense)!!.expenseDeleted).isFalse()
        assertThat(env.service.detail(deleted)!!.expenseDeleted).isTrue()
        assertThat(env.service.detail(zeroTotal)!!.expenseDeleted).isFalse()
        assertThat(env.service.detail(open)!!.expenseDeleted).isFalse()
    }

    @Test
    fun aStaleLink_isDroppedWhenThePurchaseIsOpened() = runTest {
        val env = readyEnv()
        val listId = env.closedPurchase()
        // Deleted behind the ledger's back, as before links were cleared with the expense.
        env.operationRepository.delete(env.expenseOf(listId))

        val detail = env.service.detail(listId)!!

        assertThat(detail.list.operationId).isNull()
        assertThat(detail.expenseDeleted).isTrue()
        assertThat(env.service.detail(listId)!!.list.operationId).isNull()
    }

    @Test
    fun aStaleLink_isDroppedOnImport() = runTest {
        val source = readyEnv()
        val listId = source.closedPurchase()
        source.operationRepository.delete(source.expenseOf(listId))
        val target = Env()

        target.ledger.importBackup(source.ledger.exportBackup())

        assertThat(target.service.detail(listId)!!.list.operationId).isNull()
        assertThat(target.shoppingRepository.list(listId)!!.operationId).isNull()
    }

    @Test
    fun aLiveLink_survivesImport() = runTest {
        val source = readyEnv()
        val listId = source.closedPurchase()
        val operationId = source.expenseOf(listId)
        val target = Env()

        target.ledger.importBackup(source.ledger.exportBackup())

        assertThat(target.service.detail(listId)!!.list.operationId).isEqualTo(operationId)
    }

    @Test
    fun theMark_survivesABackupRoundTrip() = runTest {
        val source = readyEnv()
        val listId = source.closedPurchase()
        source.ledger.deleteOperation(source.expenseOf(listId))
        val target = Env()

        target.ledger.importBackup(source.ledger.exportBackup())

        val detail = target.service.detail(listId)!!
        assertThat(detail.list.isClosed).isTrue()
        assertThat(detail.expenseDeleted).isTrue()
    }

    @Test
    fun aPurchaseExpense_staysLockedAfterABackupRoundTrip() = runTest {
        val source = readyEnv()
        val listId = source.closedPurchase()
        val operationId = source.expenseOf(listId)
        val target = Env()

        target.ledger.importBackup(source.ledger.exportBackup())

        val failure = runCatching {
            target.ledger.updateExpense(
                operationId = operationId,
                accountId = target.cashId(),
                categoryId = target.foodId(),
                amountMinor = 2_000L,
                date = date,
                comment = null
            )
        }.exceptionOrNull()
        assertThat(failure).isInstanceOf(PurchaseExpenseLockedException::class.java)
    }
}
