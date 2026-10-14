package com.dallet.app.ledger

import java.time.LocalDate
import java.time.YearMonth

/**
 * Operations of one calendar day. Income and expense totals leave transfers out:
 * no money is spent or earned by moving it between accounts.
 */
data class HistoryDay(
    val date: LocalDate,
    val operations: List<Operation>,
    val incomeMinor: Long,
    val expenseMinor: Long
)

/** History grouped by day, newest day first, with totals over everything it contains. */
data class HistoryResult(
    val days: List<HistoryDay>,
    val count: Int,
    val incomeMinor: Long,
    val expenseMinor: Long
)

/**
 * Narrows the history. An empty filter lets everything through; all restrictions combine with AND.
 *
 * - [query] is matched case-insensitively as a substring of the comment, the category name,
 *   the account name (both accounts of a transfer) and the amount as shown to the user.
 * - [kind] keeps one kind of operation.
 * - [accountIds] keeps operations touching any of these accounts (a transfer counts by either side).
 * - [categoryIds] keeps operations of any of these categories; transfers have no category,
 *   so the restriction is ignored when only transfers are wanted.
 * - [period] keeps operations of a date range; presets are resolved against "today" at the
 *   moment of use, so they stay correct when the month or year changes. A custom period needs
 *   both [customFrom] and [customTo] (inclusive); without them it does not restrict.
 */
data class OperationFilter(
    val query: String = "",
    val kind: OperationKind? = null,
    val accountIds: Set<Long> = emptySet(),
    val categoryIds: Set<Long> = emptySet(),
    val period: HistoryPeriod = HistoryPeriod.ALL_TIME,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null
) {
    val isEmpty: Boolean
        get() = query.isBlank() && kind == null && accountIds.isEmpty() && categoryIds.isEmpty() &&
            period == HistoryPeriod.ALL_TIME
}

enum class HistoryPeriod {
    ALL_TIME,
    THIS_MONTH,
    LAST_MONTH,
    THIS_YEAR,
    CUSTOM
}

/** The first and last day (inclusive) the period covers, or null when it does not restrict. */
internal fun OperationFilter.dateRange(today: LocalDate): Pair<LocalDate, LocalDate>? =
    when (period) {
        HistoryPeriod.ALL_TIME -> null
        HistoryPeriod.THIS_MONTH -> monthRange(YearMonth.from(today))
        HistoryPeriod.LAST_MONTH -> monthRange(YearMonth.from(today).minusMonths(1))
        HistoryPeriod.THIS_YEAR -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
        HistoryPeriod.CUSTOM -> {
            val start = customFrom
            val end = customTo
            if (start == null || end == null) null else Pair(start, end)
        }
    }

private fun monthRange(month: YearMonth): Pair<LocalDate, LocalDate> =
    month.atDay(1) to month.atEndOfMonth()

internal fun OperationFilter.matches(
    operation: Operation,
    accountNames: Map<Long, String>,
    categoryNames: Map<Long, String>,
    range: Pair<LocalDate, LocalDate>? = null,
    categoryParents: Map<Long, Long> = emptyMap()
): Boolean {
    if (range != null && (operation.date.isBefore(range.first) || operation.date.isAfter(range.second))) {
        return false
    }
    if (kind != null && operation.kind != kind) return false
    if (accountIds.isNotEmpty()) {
        val touched = listOfNotNull(operation.accountId, operation.fromAccountId, operation.toAccountId)
        if (touched.none { it in accountIds }) return false
    }
    if (categoryIds.isNotEmpty() && kind != OperationKind.TRANSFER) {
        val categoryId = operation.categoryId ?: return false
        // Picking a category also picks its subcategories.
        val parentId = categoryParents[categoryId]
        if (categoryId !in categoryIds && (parentId == null || parentId !in categoryIds)) return false
    }
    return matchesText(operation, accountNames, categoryNames)
}

private fun OperationFilter.matchesText(
    operation: Operation,
    accountNames: Map<Long, String>,
    categoryNames: Map<Long, String>
): Boolean {
    val text = query.trim().lowercase()
    if (text.isEmpty()) return true
    val candidates = listOfNotNull(
        operation.comment,
        operation.categoryId?.let { categoryNames[it] },
        operation.accountId?.let { accountNames[it] },
        operation.fromAccountId?.let { accountNames[it] },
        operation.toAccountId?.let { accountNames[it] }
    )
    if (candidates.any { it.lowercase().contains(text) }) return true
    // A query without a digit (a lone "." or ",") must not match every amount.
    val amountText = text.replace(',', '.')
    return amountText.any { it.isDigit() } && Money.formatManat(operation.amountMinor).contains(amountText)
}

/** Groups [operations], which must already be sorted newest first, into days. */
internal fun groupHistory(operations: List<Operation>): HistoryResult {
    val days = operations
        .groupBy { it.date }
        .map { (date, items) ->
            HistoryDay(
                date = date,
                operations = items,
                incomeMinor = incomeOf(items),
                expenseMinor = expenseOf(items)
            )
        }
    return HistoryResult(
        days = days,
        count = operations.size,
        incomeMinor = incomeOf(operations),
        expenseMinor = expenseOf(operations)
    )
}

private fun incomeOf(operations: List<Operation>): Long =
    operations.filter { it.kind == OperationKind.INCOME }.sumOf { it.amountMinor }

private fun expenseOf(operations: List<Operation>): Long =
    operations.filter { it.kind == OperationKind.EXPENSE }.sumOf { it.amountMinor }
