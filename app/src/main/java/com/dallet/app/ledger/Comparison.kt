package com.dallet.app.ledger

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What one side (A or B) of a comparison adds up to. Transfers are counted as operations only. */
data class ComparisonSide(
    val from: LocalDate,
    val to: LocalDate,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val operationCount: Int,
    /** False while the side's last day is still ahead of today. */
    val isComplete: Boolean
) {
    val days: Long get() = maxOf(1L, ChronoUnit.DAYS.between(from, to) + 1)
    val averageIncomePerDayMinor: Long get() = roundedDivide(incomeMinor, days)
    val averageExpensePerDayMinor: Long get() = roundedDivide(expenseMinor, days)
}

/** One category with its amount on each side. */
data class ComparisonCategoryRow(
    val categoryId: Long,
    val categoryName: String,
    val kind: CategoryKind,
    val aMinor: Long,
    val bMinor: Long
) {
    val diffMinor: Long get() = aMinor - bMinor

    /** Change from B to A in percent, or null when B has nothing to measure against. */
    val changePercent: Int? get() = percentChange(aMinor, bMinor)
}

/** Side A measured against side B: B is the base, so a rise from B to A is a positive change. */
data class Comparison(
    val a: ComparisonSide,
    val b: ComparisonSide,
    val categories: List<ComparisonCategoryRow>
) {
    val expenseDiffMinor: Long get() = a.expenseMinor - b.expenseMinor
    val incomeDiffMinor: Long get() = a.incomeMinor - b.incomeMinor
    val expenseChangePercent: Int? get() = percentChange(a.expenseMinor, b.expenseMinor)
    val incomeChangePercent: Int? get() = percentChange(a.incomeMinor, b.incomeMinor)
}

private fun roundedDivide(total: Long, days: Long): Long = (total + days / 2) / days

/**
 * "Period against period": side B becomes a copy of side [a] moved to the window before it, of
 * the same length (October against September). When [a] has no dates ("all time") there is no
 * window before it and B keeps A's period. The text query plays no part in a comparison.
 */
fun periodVsPeriod(a: OperationFilter, today: LocalDate): Pair<OperationFilter, OperationFilter> {
    val first = a.copy(query = "")
    val range = first.dateRange(today)
    if (range == null || range.second.isBefore(range.first)) return first to first
    val before = DateWindow(range.first, range.second).shifted(-1)
    return first to first.copy(
        period = HistoryPeriod.CUSTOM,
        customFrom = before.from,
        customTo = before.to
    )
}

/**
 * "Category against category": side B becomes a copy of side [a] (same period, accounts and
 * kind) with no categories chosen yet, ready for the user to pick the ones to compare with.
 */
fun categoryVsCategory(a: OperationFilter): Pair<OperationFilter, OperationFilter> {
    val first = a.copy(query = "")
    return first to first.copy(categoryIds = emptySet())
}

internal fun buildComparison(
    a: OperationFilter,
    b: OperationFilter,
    operations: List<Operation>,
    accountNames: Map<Long, String>,
    categories: List<Category>,
    today: LocalDate
): Comparison {
    val categoryNames = categories.associate { it.id to it.name }
    val earliest = operations.minOfOrNull { it.date } ?: today

    fun found(filter: OperationFilter): Pair<Pair<LocalDate, LocalDate>, List<Operation>> {
        // Without dates a side covers everything from the first operation up to today.
        val range = filter.dateRange(today) ?: (earliest to today)
        return range to operations.filter { filter.matches(it, accountNames, categoryNames, range) }
    }

    val (rangeA, foundA) = found(a)
    val (rangeB, foundB) = found(b)

    fun side(range: Pair<LocalDate, LocalDate>, items: List<Operation>) = ComparisonSide(
        from = range.first,
        to = range.second,
        incomeMinor = items.filter { it.kind == OperationKind.INCOME }.sumOf { it.amountMinor },
        expenseMinor = items.filter { it.kind == OperationKind.EXPENSE }.sumOf { it.amountMinor },
        operationCount = items.size,
        isComplete = !range.second.isAfter(today)
    )

    return Comparison(
        a = side(rangeA, foundA),
        b = side(rangeB, foundB),
        categories = categoryRows(foundA, foundB, categories)
    )
}

private fun categoryRows(
    foundA: List<Operation>,
    foundB: List<Operation>,
    categories: List<Category>
): List<ComparisonCategoryRow> {
    fun totals(items: List<Operation>): Map<Long, Long> = items
        .filter { it.kind != OperationKind.TRANSFER && it.categoryId != null }
        .groupBy { it.categoryId!! }
        .mapValues { (_, ops) -> ops.sumOf { it.amountMinor } }

    val totalsA = totals(foundA)
    val totalsB = totals(foundB)
    return categories
        .filter { it.id in totalsA || it.id in totalsB }
        .map { category ->
            ComparisonCategoryRow(
                categoryId = category.id,
                categoryName = category.name,
                kind = category.kind,
                aMinor = totalsA[category.id] ?: 0L,
                bMinor = totalsB[category.id] ?: 0L
            )
        }
        .sortedWith(
            compareBy<ComparisonCategoryRow> { it.kind }
                .thenByDescending { maxOf(it.aMinor, it.bMinor) }
                .thenBy { it.categoryName }
        )
}
