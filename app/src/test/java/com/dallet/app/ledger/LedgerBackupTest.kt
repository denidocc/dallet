package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class LedgerBackupTest {

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    private suspend fun filledLedger(): Ledger {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 10_000L, cardOpeningMinor = 5_000L)
        val accounts = ledger.accountBalances().map { it.account }
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }
        ledger.addExpense(accounts[0].id, food.id, 1_250L, LocalDate.of(2026, 7, 1), "обед")
        ledger.addIncome(accounts[1].id, salary.id, 20_000L, LocalDate.of(2026, 7, 2), null)
        ledger.addTransfer(accounts[1].id, accounts[0].id, 3_000L, LocalDate.of(2026, 7, 3), "на наличные")
        return ledger
    }

    private fun assertRejected(ledger: Ledger, json: String) {
        assertThrows(InvalidBackupException::class.java) {
            runBlocking { ledger.importBackup(json) }
        }
    }

    @Test
    fun exportThenImport_restoresSameDataAndBalances() = runTest {
        val source = filledLedger()
        val json = source.exportBackup()

        val target = newLedger()
        target.importBackup(json)

        assertThat(target.accountBalances()).isEqualTo(source.accountBalances())
        assertThat(target.totalBalanceMinor()).isEqualTo(source.totalBalanceMinor())
        assertThat(target.allOperations()).isEqualTo(source.allOperations())
        assertThat(target.categories(CategoryKind.EXPENSE))
            .isEqualTo(source.categories(CategoryKind.EXPENSE))
        assertThat(target.categories(CategoryKind.INCOME))
            .isEqualTo(source.categories(CategoryKind.INCOME))
    }

    @Test
    fun importReplacesExistingData() = runTest {
        val source = filledLedger()
        val json = source.exportBackup()

        val target = newLedger()
        target.completeOnboarding(cashOpeningMinor = 999L, cardOpeningMinor = 1L)
        target.importBackup(json)

        assertThat(target.totalBalanceMinor()).isEqualTo(source.totalBalanceMinor())
        assertThat(target.allOperations()).hasSize(3)
    }

    @Test
    fun exportOfFreshOnboarding_importsBack() = runTest {
        val source = newLedger()
        source.completeOnboarding(cashOpeningMinor = 100L, cardOpeningMinor = 200L)

        val target = newLedger()
        target.importBackup(source.exportBackup())

        assertThat(target.hasCompletedOnboarding()).isTrue()
        assertThat(target.totalBalanceMinor()).isEqualTo(300L)
        assertThat(target.allOperations()).isEmpty()
    }

    @Test
    fun exportKeepsUnknownIconKeysAndColors() = runTest {
        val source = newLedger()
        source.completeOnboarding(0L, 0L)
        source.createCategory("Хобби", CategoryKind.EXPENSE, 0xFF123456.toInt(), "legacy_icon")

        val target = newLedger()
        target.importBackup(source.exportBackup())

        val hobby = target.categories(CategoryKind.EXPENSE).first { it.name == "Хобби" }
        assertThat(hobby.iconKey).isEqualTo("legacy_icon")
        assertThat(hobby.colorArgb).isEqualTo(0xFF123456.toInt())
    }

    @Test
    fun exportDoesNotStoreBalances() = runTest {
        val json = filledLedger().exportBackup()
        assertThat(json).doesNotContain("balance\"")
    }

    @Test
    fun invalidJson_isRejectedAndDataKept() = runTest {
        val ledger = filledLedger()
        val before = ledger.accountBalances()

        assertRejected(ledger, "это не json")
        assertRejected(ledger, "")
        assertRejected(ledger, "{}")

        assertThat(ledger.accountBalances()).isEqualTo(before)
        assertThat(ledger.allOperations()).hasSize(3)
    }

    @Test
    fun newerSchemaVersion_isRejectedAndDataKept() = runTest {
        val ledger = filledLedger()
        val before = ledger.accountBalances()
        val newer = ledger.exportBackup().replace("\"schemaVersion\":2", "\"schemaVersion\":99")

        val error = assertThrows(InvalidBackupException::class.java) {
            runBlocking { ledger.importBackup(newer) }
        }

        assertThat(error).hasMessageThat().contains("более новой")
        assertThat(ledger.accountBalances()).isEqualTo(before)
    }

    @Test
    fun operationWithUnknownAccount_isRejectedAndDataKept() = runTest {
        val ledger = filledLedger()
        val before = ledger.allOperations()
        val broken = ledger.exportBackup().replace("\"accountId\":1", "\"accountId\":77")

        assertRejected(ledger, broken)

        assertThat(ledger.allOperations()).isEqualTo(before)
    }

    @Test
    fun transferToSameAccount_isRejected() = runTest {
        val ledger = filledLedger()
        val broken = ledger.exportBackup().replace("\"toAccountId\":1", "\"toAccountId\":2")

        assertRejected(ledger, broken)
    }

    @Test
    fun zeroAmount_isRejected() = runTest {
        val ledger = filledLedger()
        val broken = ledger.exportBackup().replace("\"amountMinor\":1250", "\"amountMinor\":0")

        assertRejected(ledger, broken)
    }

    @Test
    fun fileWithoutAccounts_isRejected() = runTest {
        val json = """{"app":"dallet","schemaVersion":1,"accounts":[],"categories":[],"operations":[]}"""

        assertRejected(newLedger(), json)
    }

    @Test
    fun resetAll_returnsToFirstLaunchState() = runTest {
        val ledger = filledLedger()

        ledger.resetAll()

        assertThat(ledger.hasCompletedOnboarding()).isFalse()
        assertThat(ledger.accountBalances()).isEmpty()
        assertThat(ledger.allOperations()).isEmpty()
    }

    @Test
    fun afterReset_onboardingSeedsDefaultCategoriesAgain() = runTest {
        val ledger = filledLedger()
        ledger.resetAll()

        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)

        assertThat(ledger.categories(CategoryKind.EXPENSE).map { it.name }).contains("Еда")
        assertThat(ledger.categories(CategoryKind.INCOME).map { it.name }).contains("Зарплата")
        assertThat(ledger.allOperations()).isEmpty()
    }
}
