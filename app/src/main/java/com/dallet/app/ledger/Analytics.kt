package com.dallet.app.ledger

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class AnalyticsPeriod {
    WEEK,
    MONTH,
    YEAR,
    /** Two dates picked by the user; see [DateWindow]. */
    CUSTOM
}

/** A run of whole days, first and last day both inclusive. */
data class DateWindow(val from: LocalDate, val to: LocalDate) {
    init {
        require(!to.isBefore(from)) { "window must not end before it starts" }
    }

    val days: Long get() = ChronoUnit.DAYS.between(from, to) + 1

    /** The window [windows] lengths later (negative = earlier), same length. */
    fun shifted(windows: Long): DateWindow {
        val delta = days * windows
        return DateWindow(from.plusDays(delta), to.plusDays(delta))
    }
}

data class CategoryExpenseTotal(
    val categoryId: Long,
    val categoryName: String,
    val colorArgb: Int,
    val amountMinor: Long
)

data class ExpenseFlowPoint(
    val bucketStart: LocalDate,
    val label: String,
    val amountMinor: Long
)

data class AccountPeriodTotal(
    val accountId: Long,
    val accountName: String,
    val incomeMinor: Long,
    val expenseMinor: Long
)

data class AnalyticsComparison(
    val current: AnalyticsSummary,
    val previous: AnalyticsSummary,
    val currentIsComplete: Boolean
)

/** Percent change from [previous] to [current], or null when there is no previous amount. */
fun percentChange(current: Long, previous: Long): Int? {
    if (previous <= 0L) return null
    return Math.round((current - previous) * 100.0 / previous).toInt()
}

/**
 * Moves [asOf] by [offset] whole periods (negative = into the past).
 * A custom period has no calendar unit: shift its [DateWindow] instead; [asOf] is returned as is.
 */
fun shiftAsOf(period: AnalyticsPeriod, asOf: LocalDate, offset: Long): LocalDate =
    when (period) {
        AnalyticsPeriod.WEEK -> asOf.plusWeeks(offset)
        AnalyticsPeriod.MONTH -> asOf.plusMonths(offset)
        AnalyticsPeriod.YEAR -> asOf.plusYears(offset)
        AnalyticsPeriod.CUSTOM -> asOf
    }

data class AnalyticsSummary(
    val period: AnalyticsPeriod,
    val fromInclusive: LocalDate,
    val toInclusive: LocalDate,
    val incomeTotalMinor: Long,
    val expenseTotalMinor: Long,
    val expensesByCategory: List<CategoryExpenseTotal>,
    val expenseFlow: List<ExpenseFlowPoint>,
    val incomesByCategory: List<CategoryExpenseTotal> = emptyList(),
    val incomeFlow: List<ExpenseFlowPoint> = emptyList(),
    val byAccount: List<AccountPeriodTotal> = emptyList()
)

data class NamedAmount(
    val id: Long,
    val name: String,
    val amountMinor: Long
)

/** What happened with goals and debts in one period, and where they stand at the end of it. */
data class SavingsDebtsSummary(
    val fromInclusive: LocalDate,
    val toInclusive: LocalDate,
    val goalsDepositedMinor: Long,
    val goalsWithdrawnMinor: Long,
    val depositedByGoal: List<NamedAmount>,
    val goalFlow: List<ExpenseFlowPoint>,
    val goalProgress: List<GoalSummary>,
    val lentMinor: Long,
    val returnedToMeMinor: Long,
    val borrowedMinor: Long,
    val iRepaidMinor: Long,
    val writtenOffLentMinor: Long,
    val forgivenMinor: Long,
    val owedToMeMinor: Long,
    val iOweMinor: Long,
    val owedToMeByPerson: List<NamedAmount>,
    val iOweByPerson: List<NamedAmount>
)
