package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class RecurringTest {

    private class Env(val ledger: Ledger, val accountId: Long, val expenseId: Long, val incomeId: Long)

    private suspend fun env(): Env {
        val ledger = Ledger(
            accountRepository = InMemoryAccountRepository(),
            categoryRepository = InMemoryCategoryRepository(),
            operationRepository = InMemoryOperationRepository()
        )
        ledger.completeOnboarding(listOf(NewAccount("Cash", 100_000L)), Currency.TMT)
        return Env(
            ledger,
            ledger.accountBalances().first().account.id,
            ledger.categories(CategoryKind.EXPENSE).first().id,
            ledger.categories(CategoryKind.INCOME).first().id
        )
    }

    private fun rule(frequency: Frequency, start: LocalDate) = RecurringRule(
        id = 1, kind = OperationKind.EXPENSE, accountId = 1, categoryId = 1, amountMinor = 100,
        comment = null, frequency = frequency, startDate = start, nextDue = start
    )

    @Test
    fun schedule_weeklyAddsSevenDays() {
        val next = RecurringSchedule.occurrenceAfter(
            rule(Frequency.WEEKLY, LocalDate.of(2026, 3, 2)), LocalDate.of(2026, 3, 2)
        )
        assertThat(next).isEqualTo(LocalDate.of(2026, 3, 9))
    }

    @Test
    fun schedule_monthlyKeepsAnchorDayAcrossShortMonths() {
        val rule = rule(Frequency.MONTHLY, LocalDate.of(2026, 1, 31))

        val feb = RecurringSchedule.occurrenceAfter(rule, LocalDate.of(2026, 1, 31))
        val mar = RecurringSchedule.occurrenceAfter(rule, feb)

        assertThat(feb).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(mar).isEqualTo(LocalDate.of(2026, 3, 31))
    }

    @Test
    fun schedule_yearlyHandlesLeapDay() {
        val rule = rule(Frequency.YEARLY, LocalDate.of(2024, 2, 29))

        val next = RecurringSchedule.occurrenceAfter(rule, LocalDate.of(2024, 2, 29))

        assertThat(next).isEqualTo(LocalDate.of(2025, 2, 28))
    }

    @Test
    fun dueDates_listsAllMissedUpToToday() {
        val rule = rule(Frequency.MONTHLY, LocalDate.of(2026, 1, 10))

        val dates = RecurringSchedule.dueDates(rule, LocalDate.of(2026, 3, 15))

        assertThat(dates).containsExactly(
            LocalDate.of(2026, 1, 10),
            LocalDate.of(2026, 2, 10),
            LocalDate.of(2026, 3, 10)
        ).inOrder()
    }

    @Test
    fun dueItems_markOnlyEarliestPerRuleAsFirst() = runTest {
        val env = env()
        env.ledger.addRecurring(
            OperationKind.EXPENSE, env.accountId, env.expenseId, 500L, null,
            Frequency.MONTHLY, LocalDate.of(2026, 1, 10)
        )

        val items = env.ledger.dueItems(LocalDate.of(2026, 3, 15))

        assertThat(items.map { it.isFirst }).containsExactly(true, false, false).inOrder()
    }

    @Test
    fun confirmDue_createsOperationOnDueDateAndAdvances() = runTest {
        val env = env()
        val id = env.ledger.addRecurring(
            OperationKind.EXPENSE, env.accountId, env.expenseId, 500L, "Rent",
            Frequency.MONTHLY, LocalDate.of(2026, 1, 10)
        )

        env.ledger.confirmDue(id, LocalDate.of(2026, 1, 10), 650L)

        val operation = env.ledger.allOperations().single()
        assertThat(operation.amountMinor).isEqualTo(650L)
        assertThat(operation.date).isEqualTo(LocalDate.of(2026, 1, 10))
        assertThat(operation.comment).isEqualTo("Rent")
        assertThat(env.ledger.recurringRules().single().nextDue).isEqualTo(LocalDate.of(2026, 2, 10))
        assertThat(env.ledger.totalBalanceMinor()).isEqualTo(100_000L - 650L)
    }

    @Test
    fun confirmDue_incomeAddsMoney() = runTest {
        val env = env()
        val id = env.ledger.addRecurring(
            OperationKind.INCOME, env.accountId, env.incomeId, 2_000L, null,
            Frequency.WEEKLY, LocalDate.of(2026, 3, 2)
        )

        env.ledger.confirmDue(id, LocalDate.of(2026, 3, 2), 2_000L)

        assertThat(env.ledger.totalBalanceMinor()).isEqualTo(102_000L)
    }

    @Test
    fun confirmDue_rejectsNonEarliestDate() = runTest {
        val env = env()
        val id = env.ledger.addRecurring(
            OperationKind.EXPENSE, env.accountId, env.expenseId, 500L, null,
            Frequency.MONTHLY, LocalDate.of(2026, 1, 10)
        )

        val failure = runCatching { env.ledger.confirmDue(id, LocalDate.of(2026, 2, 10), 500L) }

        assertThat(failure.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(env.ledger.allOperations()).isEmpty()
    }

    @Test
    fun skipDue_advancesWithoutOperation() = runTest {
        val env = env()
        val id = env.ledger.addRecurring(
            OperationKind.EXPENSE, env.accountId, env.expenseId, 500L, null,
            Frequency.MONTHLY, LocalDate.of(2026, 1, 10)
        )

        env.ledger.skipDue(id, LocalDate.of(2026, 1, 10))

        assertThat(env.ledger.allOperations()).isEmpty()
        assertThat(env.ledger.recurringRules().single().nextDue).isEqualTo(LocalDate.of(2026, 2, 10))
    }

    @Test
    fun addRecurring_rejectsMismatchedCategoryKind() = runTest {
        val env = env()

        val failure = runCatching {
            env.ledger.addRecurring(
                OperationKind.EXPENSE, env.accountId, env.incomeId, 500L, null,
                Frequency.MONTHLY, LocalDate.of(2026, 1, 10)
            )
        }

        assertThat(failure.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun updateRecurring_keepsNextDueWhenScheduleUnchanged() = runTest {
        val env = env()
        val id = env.ledger.addRecurring(
            OperationKind.EXPENSE, env.accountId, env.expenseId, 500L, null,
            Frequency.MONTHLY, LocalDate.of(2026, 1, 10)
        )
        env.ledger.skipDue(id, LocalDate.of(2026, 1, 10))

        env.ledger.updateRecurring(
            id, OperationKind.EXPENSE, env.accountId, env.expenseId, 900L, "New",
            Frequency.MONTHLY, LocalDate.of(2026, 1, 10)
        )

        val rule = env.ledger.recurringRules().single()
        assertThat(rule.amountMinor).isEqualTo(900L)
        assertThat(rule.nextDue).isEqualTo(LocalDate.of(2026, 2, 10))
    }

    @Test
    fun recurring_travelsInBackupAndResetClearsIt() = runTest {
        val env = env()
        env.ledger.addRecurring(
            OperationKind.EXPENSE, env.accountId, env.expenseId, 500L, "Rent",
            Frequency.MONTHLY, LocalDate.of(2026, 1, 10)
        )
        val json = env.ledger.exportBackup()

        env.ledger.resetAll()
        assertThat(env.ledger.recurringRules()).isEmpty()

        env.ledger.importBackup(json)
        val rule = env.ledger.recurringRules().single()
        assertThat(rule.comment).isEqualTo("Rent")
        assertThat(rule.frequency).isEqualTo(Frequency.MONTHLY)
        assertThat(rule.nextDue).isEqualTo(LocalDate.of(2026, 1, 10))
    }
}
