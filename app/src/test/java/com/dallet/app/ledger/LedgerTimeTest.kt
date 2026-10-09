package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LedgerTimeTest {

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    private suspend fun readyLedger(): Ledger {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        return ledger
    }

    private suspend fun Ledger.cashId() =
        accountBalances().first { it.account.name == Ledger.CASH_NAME }.account.id

    private suspend fun Ledger.foodId() =
        categories(CategoryKind.EXPENSE).first { it.name == "Еда" }.id

    @Test
    fun addedExpense_keepsTime() = runTest {
        val ledger = readyLedger()

        val id = ledger.addExpense(
            ledger.cashId(), ledger.foodId(), 100L,
            LocalDate.of(2026, 7, 1), null, time = LocalTime.of(14, 30)
        )

        assertThat(ledger.operation(id)?.time).isEqualTo(LocalTime.of(14, 30))
    }

    @Test
    fun expenseWithoutTime_hasNullTime() = runTest {
        val ledger = readyLedger()

        val id = ledger.addExpense(
            ledger.cashId(), ledger.foodId(), 100L, LocalDate.of(2026, 7, 1), null
        )

        assertThat(ledger.operation(id)?.time).isNull()
    }

    @Test
    fun updateExpense_changesTime() = runTest {
        val ledger = readyLedger()
        val id = ledger.addExpense(
            ledger.cashId(), ledger.foodId(), 100L,
            LocalDate.of(2026, 7, 1), null, time = LocalTime.of(8, 0)
        )

        ledger.updateExpense(
            id, ledger.cashId(), ledger.foodId(), 100L,
            LocalDate.of(2026, 7, 1), null, time = LocalTime.of(21, 15)
        )

        assertThat(ledger.operation(id)?.time).isEqualTo(LocalTime.of(21, 15))
    }

    @Test
    fun operations_sortedByDateThenTimeDescending_legacyLastWithinDay() = runTest {
        val ledger = readyLedger()
        val cash = ledger.cashId()
        val food = ledger.foodId()
        val day = LocalDate.of(2026, 7, 1)

        val legacy = ledger.addExpense(cash, food, 1L, day, "legacy")
        val morning = ledger.addExpense(cash, food, 1L, day, "morning", time = LocalTime.of(8, 0))
        val evening = ledger.addExpense(cash, food, 1L, day, "evening", time = LocalTime.of(20, 0))
        val nextDay = ledger.addExpense(cash, food, 1L, day.plusDays(1), "next")

        assertThat(ledger.allOperations().map { it.id })
            .containsExactly(nextDay, evening, morning, legacy).inOrder()
    }

    @Test
    fun backup_roundTripKeepsTime() = runTest {
        val source = readyLedger()
        source.addExpense(
            source.cashId(), source.foodId(), 500L,
            LocalDate.of(2026, 7, 3), null, time = LocalTime.of(9, 45)
        )
        source.addExpense(source.cashId(), source.foodId(), 700L, LocalDate.of(2026, 7, 4), null)

        val target = newLedger()
        target.importBackup(source.exportBackup())

        val times = target.allOperations().associate { it.amountMinor to it.time }
        assertThat(times[500L]).isEqualTo(LocalTime.of(9, 45))
        assertThat(times[700L]).isNull()
    }

    @Test
    fun backupWithoutTimeField_importsWithNullTime() = runTest {
        val json = """
            {"app":"dallet","schemaVersion":1,
             "accounts":[{"id":1,"name":"Наличные","openingBalanceMinor":0}],
             "categories":[{"id":1,"name":"Еда","kind":"EXPENSE","colorArgb":-1,"iconKey":"food"}],
             "operations":[{"id":1,"kind":"EXPENSE","amountMinor":100,"date":"2026-07-01",
                            "accountId":1,"categoryId":1}]}
        """.trimIndent()
        val ledger = newLedger()

        ledger.importBackup(json)

        assertThat(ledger.allOperations().single().time).isNull()
    }

    @Test
    fun periodReport_sortedByTimeWithinDay_legacyFirst() = runTest {
        val ledger = readyLedger()
        val cash = ledger.cashId()
        val food = ledger.foodId()
        val day = LocalDate.of(2026, 7, 5)
        ledger.addExpense(cash, food, 3L, day, null, time = LocalTime.of(18, 0))
        ledger.addExpense(cash, food, 1L, day, null)
        ledger.addExpense(cash, food, 2L, day, null, time = LocalTime.of(9, 0))

        val report = ledger.periodReport(
            AnalyticsPeriod.MONTH, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15)
        )

        assertThat(report.rows.map { it.amountMinor }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(report.rows.map { it.time })
            .containsExactly(null, LocalTime.of(9, 0), LocalTime.of(18, 0)).inOrder()
    }
}
