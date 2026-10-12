package com.dallet.app.ledger

import java.time.LocalDate

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
 */
data class OperationFilter(
    val query: String = "",
    val kind: OperationKind? = null,
    val accountIds: Set<Long> = emptySet(),
    val categoryIds: Set<Long> = emptySet()
) {
    val isEmpty: Boolean
        get() = query.isBlank() && kind == null && accountIds.isEmpty() && categoryIds.isEmpty()
}

internal fun OperationFilter.matches(
    operation: Operation,
    accountNames: Map<Long, String>,
    categoryNames: Map<Long, String>
): Boolean {
    if (kind != null && operation.kind != kind) return false
    if (accountIds.isNotEmpty()) {
        val touched = listOfNotNull(operation.accountId, operation.fromAccountId, operation.toAccountId)
        if (touched.none { it in accountIds }) return false
    }
    if (categoryIds.isNotEmpty() && kind != OperationKind.TRANSFER) {
        val categoryId = operation.categoryId
        if (categoryId == null || categoryId !in categoryIds) return false
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
    val amountText = text.replace(',', '.')
    val candidates = listOfNotNull(
        operation.comment,
        operation.categoryId?.let { categoryNames[it] },
        operation.accountId?.let { accountNames[it] },
        operation.fromAccountId?.let { accountNames[it] },
        operation.toAccountId?.let { accountNames[it] }
    )
    return candidates.any { it.lowercase().contains(text) } ||
        Money.formatManat(operation.amountMinor).contains(amountText)
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
