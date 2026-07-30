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

data class AnalyticsSummary(
    val period: AnalyticsPeriod,
    val fromInclusive: LocalDate,
    val toInclusive: LocalDate,
    val incomeTotalMinor: Long,
    val expenseTotalMinor: Long,
    val expensesByCategory: List<CategoryExpenseTotal>
)
