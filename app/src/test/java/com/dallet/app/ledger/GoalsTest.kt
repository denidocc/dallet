package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class GoalsTest {
    private val day = LocalDate.of(2026, 10, 14)

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    private suspend fun ledgerWithAccounts(): Ledger {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 100_000L, cardOpeningMinor = 50_000L)
        return ledger
    }

    private suspend fun cashId(ledger: Ledger): Long = ledger.accountBalances().first().account.id

    @Test
    fun newGoalStartsEmpty() = runTest {
        val ledger = ledgerWithAccounts()
        val id = ledger.createGoal("  Ноутбук ", 1_200_000L, day)

        val summary = ledger.goalSummary(id)!!
        assertThat(summary.goal.name).isEqualTo("Ноутбук")
        assertThat(summary.savedMinor).isEqualTo(0L)
        assertThat(summary.remainingMinor).isEqualTo(1_200_000L)
        assertThat(summary.percent).isEqualTo(0)
        assertThat(summary.reached).isFalse()
    }

    @Test
    fun goalNeedsNameAndPositiveTarget() = runTest {
        val ledger = ledgerWithAccounts()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.createGoal(" ", 100L, day) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.createGoal("Отпуск", 0L, day) } }
    }

    @Test
    fun depositFromAccountLowersTheBalanceAndRaisesProgress() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val before = ledger.totalBalanceMinor()
        val goal = ledger.createGoal("Отпуск", 40_000L, day)

        ledger.depositToGoal(goal, cash, 10_000L, day)

        assertThat(ledger.accountBalances().first { it.account.id == cash }.balanceMinor).isEqualTo(90_000L)
        assertThat(ledger.totalBalanceMinor()).isEqualTo(before - 10_000L)
        val summary = ledger.goalSummary(goal)!!
        assertThat(summary.savedMinor).isEqualTo(10_000L)
        assertThat(summary.percent).isEqualTo(25)
        assertThat(ledger.allOperations()).isEmpty()
    }

    @Test
    fun depositAsIncomeCountsAsIncomeButKeepsTheBalance() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val salary = ledger.categories(CategoryKind.INCOME).first()
        val before = ledger.totalBalanceMinor()
        val goal = ledger.createGoal("Подарок", 5_000L, day)

        ledger.depositToGoalAsIncome(goal, cash, salary.id, 2_000L, day, null)

        assertThat(ledger.totalBalanceMinor()).isEqualTo(before)
        assertThat(ledger.goalSummary(goal)!!.savedMinor).isEqualTo(2_000L)
        val income = ledger.allOperations().single()
        assertThat(income.kind).isEqualTo(OperationKind.INCOME)
        assertThat(income.amountMinor).isEqualTo(2_000L)
        assertThat(income.comment).isEqualTo("Подарок")
    }

    @Test
    fun withdrawReturnsMoneyToTheAccount() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val goal = ledger.createGoal("Отпуск", 40_000L, day)
        ledger.depositToGoal(goal, cash, 10_000L, day)

        ledger.withdrawFromGoal(goal, cash, 4_000L, day)

        assertThat(ledger.accountBalances().first { it.account.id == cash }.balanceMinor).isEqualTo(94_000L)
        assertThat(ledger.goalSummary(goal)!!.savedMinor).isEqualTo(6_000L)
    }

    @Test
    fun cannotWithdrawMoreThanSaved() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val goal = ledger.createGoal("Отпуск", 40_000L, day)
        ledger.depositToGoal(goal, cash, 1_000L, day)

        assertThrows(GoalFundsException::class.java) {
            runBlocking { ledger.withdrawFromGoal(goal, cash, 1_001L, day) }
        }
    }

    @Test
    fun deletingTheGoalReturnsTheMoney() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val before = ledger.totalBalanceMinor()
        val goal = ledger.createGoal("Отпуск", 40_000L, day)
        ledger.depositToGoal(goal, cash, 10_000L, day)

        ledger.deleteGoal(goal)

        assertThat(ledger.goals()).isEmpty()
        assertThat(ledger.totalBalanceMinor()).isEqualTo(before)
    }

    @Test
    fun undoingADepositAsIncomeRemovesTheIncomeToo() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val salary = ledger.categories(CategoryKind.INCOME).first()
        val goal = ledger.createGoal("Подарок", 5_000L, day)
        ledger.depositToGoalAsIncome(goal, cash, salary.id, 2_000L, day, "премия")
        val entry = ledger.goalEntries(goal).single()

        ledger.deleteGoalEntry(entry.id)

        assertThat(ledger.goalEntries(goal)).isEmpty()
        assertThat(ledger.allOperations()).isEmpty()
        assertThat(ledger.goalSummary(goal)!!.savedMinor).isEqualTo(0L)
    }

    @Test
    fun cannotUndoADepositThatLaterWithdrawalsRelyOn() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val goal = ledger.createGoal("Отпуск", 40_000L, day)
        val deposit = ledger.depositToGoal(goal, cash, 10_000L, day)
        ledger.withdrawFromGoal(goal, cash, 8_000L, day)

        assertThrows(GoalFundsException::class.java) { runBlocking { ledger.deleteGoalEntry(deposit) } }
    }

    @Test
    fun deletingTheIncomeKeepsTheMoneyInTheGoal() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val salary = ledger.categories(CategoryKind.INCOME).first()
        val before = ledger.totalBalanceMinor()
        val goal = ledger.createGoal("Подарок", 5_000L, day)
        ledger.depositToGoalAsIncome(goal, cash, salary.id, 2_000L, day, null)

        ledger.deleteOperation(ledger.allOperations().single().id)

        assertThat(ledger.goalSummary(goal)!!.savedMinor).isEqualTo(2_000L)
        assertThat(ledger.totalBalanceMinor()).isEqualTo(before - 2_000L)
        assertThat(ledger.goalEntries(goal).single().operationId).isNull()
    }

    @Test
    fun accountWithGoalEntriesCannotBeDeleted() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val goal = ledger.createGoal("Отпуск", 40_000L, day)
        ledger.depositToGoal(goal, cash, 100L, day)

        assertThrows(AccountHasOperationsException::class.java) {
            runBlocking { ledger.deleteAccount(cash) }
        }
    }

    @Test
    fun archivedGoalRefusesNewMoney() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val goal = ledger.createGoal("Отпуск", 40_000L, day)
        ledger.setGoalArchived(goal, true)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.depositToGoal(goal, cash, 100L, day) }
        }
    }

    @Test
    fun reachedGoalReportsFullProgress() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val goal = ledger.createGoal("Книга", 3_000L, day)
        ledger.depositToGoal(goal, cash, 4_000L, day)

        val summary = ledger.goalSummary(goal)!!
        assertThat(summary.reached).isTrue()
        assertThat(summary.percent).isEqualTo(100)
        assertThat(summary.remainingMinor).isEqualTo(0L)
    }

    @Test
    fun backupRoundTripKeepsGoalsAndBalances() = runTest {
        val source = ledgerWithAccounts()
        val cash = cashId(source)
        val goal = source.createGoal("Отпуск", 40_000L, day)
        source.depositToGoal(goal, cash, 10_000L, day)
        source.withdrawFromGoal(goal, cash, 2_500L, day)

        val target = newLedger()
        target.importBackup(source.exportBackup())

        assertThat(target.goals()).isEqualTo(source.goals())
        assertThat(target.goalEntries(goal)).isEqualTo(source.goalEntries(goal))
        assertThat(target.accountBalances()).isEqualTo(source.accountBalances())
    }

    @Test
    fun backupWithoutGoalsStillImports() = runTest {
        val ledger = ledgerWithAccounts()
        val old = ledger.exportBackup()
            .replace("\"schemaVersion\":4", "\"schemaVersion\":2")
            .replace(",\"goals\":{\"goals\":[],\"entries\":[]}", "")

        val target = newLedger()
        target.importBackup(old)

        assertThat(target.goals()).isEmpty()
    }

    @Test
    fun backupWithNegativeGoalIsRejected() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val goal = ledger.createGoal("Отпуск", 40_000L, day)
        ledger.depositToGoal(goal, cash, 1_000L, day)
        val broken = ledger.exportBackup().replace("\"amountMinor\":1000", "\"amountMinor\":-1000")

        assertThrows(InvalidBackupException::class.java) {
            runBlocking { newLedger().importBackup(broken) }
        }
    }
}
