package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class DebtsTest {
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

    private suspend fun balanceOf(ledger: Ledger, accountId: Long): Long =
        ledger.accountBalances().first { it.account.id == accountId }.balanceMinor

    @Test
    fun lendingTakesMoneyFromTheAccount() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)

        val id = ledger.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, "на ремонт")

        assertThat(balanceOf(ledger, cash)).isEqualTo(80_000L)
        val summary = ledger.debtSummary(id)!!
        assertThat(summary.debt.person).isEqualTo("Али")
        assertThat(summary.remainingMinor).isEqualTo(20_000L)
        assertThat(summary.settled).isFalse()
    }

    @Test
    fun borrowingPutsMoneyOnTheAccount() = runTest {
        val ledger = ledgerWithAccounts()
        val card = ledger.accountBalances().last().account.id

        ledger.createDebt("Банк", DebtDirection.BORROWED, 30_000L, card, day, null)

        assertThat(balanceOf(ledger, card)).isEqualTo(80_000L)
    }

    @Test
    fun repaymentOfALoanBringsMoneyBack() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val id = ledger.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, null)

        ledger.repayDebt(id, 5_000L, cash, day)

        assertThat(balanceOf(ledger, cash)).isEqualTo(85_000L)
        val summary = ledger.debtSummary(id)!!
        assertThat(summary.remainingMinor).isEqualTo(15_000L)
        assertThat(summary.percent).isEqualTo(25)
    }

    @Test
    fun repayingWhatYouBorrowedTakesMoneyAway() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val id = ledger.createDebt("Сосед", DebtDirection.BORROWED, 10_000L, cash, day, null)

        ledger.repayDebt(id, 4_000L, cash, day)

        assertThat(balanceOf(ledger, cash)).isEqualTo(100_000L + 10_000L - 4_000L)
    }

    @Test
    fun debtWithoutAccountLeavesBalancesAlone() = runTest {
        val ledger = ledgerWithAccounts()
        val before = ledger.totalBalanceMinor()

        val id = ledger.createDebt("Старый долг", DebtDirection.LENT, 9_000L, null, day, null)
        ledger.repayDebt(id, 1_000L, null, day)

        assertThat(ledger.totalBalanceMinor()).isEqualTo(before)
        assertThat(ledger.debtSummary(id)!!.remainingMinor).isEqualTo(8_000L)
    }

    @Test
    fun cannotRepayMoreThanIsLeft() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val id = ledger.createDebt("Али", DebtDirection.LENT, 1_000L, cash, day, null)

        assertThrows(DebtOverpayException::class.java) {
            runBlocking { ledger.repayDebt(id, 1_001L, cash, day) }
        }
    }

    @Test
    fun fullRepaymentSettlesTheDebt() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val id = ledger.createDebt("Али", DebtDirection.LENT, 1_000L, cash, day, null)

        ledger.repayDebt(id, 1_000L, cash, day)

        val summary = ledger.debtSummary(id)!!
        assertThat(summary.settled).isTrue()
        assertThat(summary.remainingMinor).isEqualTo(0L)
        assertThat(summary.percent).isEqualTo(100)
    }

    @Test
    fun undoingAPaymentRestoresTheBalanceAndTheDebt() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val id = ledger.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, null)
        val payment = ledger.repayDebt(id, 5_000L, cash, day)

        ledger.deleteDebtPayment(payment)

        assertThat(balanceOf(ledger, cash)).isEqualTo(80_000L)
        assertThat(ledger.debtSummary(id)!!.remainingMinor).isEqualTo(20_000L)
    }

    @Test
    fun theIssueCannotBeUndoneAsAPayment() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val id = ledger.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, null)
        val issue = ledger.debtEntries(id).first()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.deleteDebtPayment(issue.id) }
        }
    }

    @Test
    fun deletingTheDebtRestoresTheBalances() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val before = ledger.totalBalanceMinor()
        val id = ledger.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, null)
        ledger.repayDebt(id, 5_000L, cash, day)

        ledger.deleteDebt(id)

        assertThat(ledger.debts()).isEmpty()
        assertThat(ledger.totalBalanceMinor()).isEqualTo(before)
    }

    @Test
    fun accountUsedByADebtCannotBeDeleted() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        ledger.createDebt("Али", DebtDirection.LENT, 100L, cash, day, null)

        assertThrows(AccountHasOperationsException::class.java) {
            runBlocking { ledger.deleteAccount(cash) }
        }
    }

    @Test
    fun debtNeedsAPersonAndAPositiveAmount() = runTest {
        val ledger = ledgerWithAccounts()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.createDebt(" ", DebtDirection.LENT, 100L, null, day, null) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.createDebt("Али", DebtDirection.LENT, 0L, null, day, null) }
        }
    }

    @Test
    fun debtsDoNotCountAsIncomeOrExpense() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        ledger.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, null)
        ledger.createDebt("Банк", DebtDirection.BORROWED, 5_000L, cash, day, null)

        assertThat(ledger.allOperations()).isEmpty()
    }

    @Test
    fun backupRoundTripKeepsDebtsAndBalances() = runTest {
        val source = ledgerWithAccounts()
        val cash = cashId(source)
        val id = source.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, "ремонт")
        source.repayDebt(id, 5_000L, null, day)

        val target = newLedger()
        target.importBackup(source.exportBackup())

        assertThat(target.debts()).isEqualTo(source.debts())
        assertThat(target.debtEntries(id)).isEqualTo(source.debtEntries(id))
        assertThat(target.accountBalances()).isEqualTo(source.accountBalances())
    }

    @Test
    fun backupWhereRepaymentsExceedTheIssueIsRejected() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val id = ledger.createDebt("Али", DebtDirection.LENT, 2_000L, cash, day, null)
        ledger.repayDebt(id, 1_500L, cash, day)
        val broken = ledger.exportBackup().replace("\"amountMinor\":1500", "\"amountMinor\":2500")

        assertThrows(InvalidBackupException::class.java) {
            runBlocking { newLedger().importBackup(broken) }
        }
    }

    @Test
    fun writingOffALoanIsAnExpenseWithoutMovingBalances() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val id = ledger.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, null)
        ledger.repayDebt(id, 5_000L, cash, day)
        val before = ledger.totalBalanceMinor()

        ledger.writeOffDebt(id, day, "Списанные долги", "Прощённые долги")

        assertThat(ledger.totalBalanceMinor()).isEqualTo(before)
        val summary = ledger.debtSummary(id)!!
        assertThat(summary.settled).isTrue()
        assertThat(summary.writtenOffMinor).isEqualTo(15_000L)
        val expense = ledger.allOperations().single()
        assertThat(expense.kind).isEqualTo(OperationKind.EXPENSE)
        assertThat(expense.amountMinor).isEqualTo(15_000L)
        assertThat(ledger.categories(CategoryKind.EXPENSE).map { it.name }).contains("Списанные долги")
    }

    @Test
    fun aForgivenDebtIsAnIncomeWithoutMovingBalances() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val id = ledger.createDebt("Сосед", DebtDirection.BORROWED, 8_000L, cash, day, null)
        val before = ledger.totalBalanceMinor()

        ledger.writeOffDebt(id, day, "Списанные долги", "Прощённые долги")

        assertThat(ledger.totalBalanceMinor()).isEqualTo(before)
        val income = ledger.allOperations().single()
        assertThat(income.kind).isEqualTo(OperationKind.INCOME)
        assertThat(income.amountMinor).isEqualTo(8_000L)
    }

    @Test
    fun undoingAWriteOffRemovesItsOperation() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val id = ledger.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, null)
        val entry = ledger.writeOffDebt(id, day, "Списанные долги", "Прощённые долги")

        ledger.deleteDebtPayment(entry)

        assertThat(ledger.allOperations()).isEmpty()
        assertThat(ledger.debtSummary(id)!!.remainingMinor).isEqualTo(20_000L)
        assertThat(balanceOf(ledger, cash)).isEqualTo(80_000L)
    }

    @Test
    fun deletingTheDebtRemovesItsWriteOffOperation() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val before = ledger.totalBalanceMinor()
        val id = ledger.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, null)
        ledger.writeOffDebt(id, day, "Списанные долги", "Прощённые долги")

        ledger.deleteDebt(id)

        assertThat(ledger.allOperations()).isEmpty()
        assertThat(ledger.totalBalanceMinor()).isEqualTo(before)
    }

    @Test
    fun nothingLeftMeansNothingToWriteOff() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val id = ledger.createDebt("Али", DebtDirection.LENT, 1_000L, cash, day, null)
        ledger.repayDebt(id, 1_000L, cash, day)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.writeOffDebt(id, day, "Списанные долги", "Прощённые долги") }
        }
    }

    @Test
    fun writeOffSurvivesABackupRoundTrip() = runTest {
        val source = ledgerWithAccounts()
        val cash = cashId(source)
        val id = source.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, null)
        source.writeOffDebt(id, day, "Списанные долги", "Прощённые долги")

        val target = newLedger()
        target.importBackup(source.exportBackup())

        assertThat(target.debts()).isEqualTo(source.debts())
        assertThat(target.debtEntries(id)).isEqualTo(source.debtEntries(id))
        assertThat(target.accountBalances()).isEqualTo(source.accountBalances())
    }

    @Test
    fun analyticsCountsGoalsAndDebtsOfThePeriod() = runTest {
        val ledger = ledgerWithAccounts()
        val cash = cashId(ledger)
        val goal = ledger.createGoal("Отпуск", 40_000L, day)
        ledger.depositToGoal(goal, cash, 10_000L, day)
        ledger.depositToGoal(goal, cash, 2_000L, day.minusMonths(2))
        val lent = ledger.createDebt("Али", DebtDirection.LENT, 20_000L, cash, day, null)
        ledger.repayDebt(lent, 5_000L, cash, day)
        ledger.createDebt("Банк", DebtDirection.BORROWED, 7_000L, cash, day, null)

        val report = ledger.savingsAndDebts(AnalyticsPeriod.MONTH, day)

        assertThat(report.goalsDepositedMinor).isEqualTo(10_000L)
        assertThat(report.depositedByGoal.single().name).isEqualTo("Отпуск")
        assertThat(report.lentMinor).isEqualTo(20_000L)
        assertThat(report.returnedToMeMinor).isEqualTo(5_000L)
        assertThat(report.borrowedMinor).isEqualTo(7_000L)
        assertThat(report.owedToMeMinor).isEqualTo(15_000L)
        assertThat(report.iOweMinor).isEqualTo(7_000L)
        assertThat(report.owedToMeByPerson.single().name).isEqualTo("Али")
        assertThat(report.goalProgress.single().savedMinor).isEqualTo(12_000L)
    }
}
