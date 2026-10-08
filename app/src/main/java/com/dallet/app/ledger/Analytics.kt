package com.dallet.app.ledger

import java.time.LocalDate

enum class AnalyticsPeriod {
    WEEK,
    MONTH,
    YEAR
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

/** Moves [asOf] by [offset] whole periods (negative = into the past). */
fun shiftAsOf(period: AnalyticsPeriod, asOf: LocalDate, offset: Long): LocalDate =
    when (period) {
        AnalyticsPeriod.WEEK -> asOf.plusWeeks(offset)
        AnalyticsPeriod.MONTH -> asOf.plusMonths(offset)
        AnalyticsPeriod.YEAR -> asOf.plusYears(offset)
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
    val incomeFlow: List<ExpenseFlowPoint> = emptyList()
)
