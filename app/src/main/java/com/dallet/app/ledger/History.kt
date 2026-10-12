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
