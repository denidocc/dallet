package com.dallet.app.ledger

import java.time.LocalDate

data class ReportRow(
    val date: LocalDate,
    val kind: OperationKind,
    val accountName: String,
    val categoryName: String,
    val amountMinor: Long,
    val comment: String?
)

data class PeriodReport(
    val comparison: AnalyticsComparison,
    val rows: List<ReportRow>
)

object ReportCsv {
    private const val BOM = "﻿"
    private const val SEPARATOR = ";"
    private const val LINE_BREAK = "\r\n"
    private val HEADER = listOf("Дата", "Вид", "Счёт", "Категория", "Сумма", "Комментарий")

    fun encode(rows: List<ReportRow>): String {
        val builder = StringBuilder(BOM)
        builder.append(HEADER.joinToString(SEPARATOR)).append(LINE_BREAK)
        rows.forEach { row ->
            val fields = listOf(
                row.date.toString(),
                kindLabel(row.kind),
                row.accountName,
                row.categoryName,
                signedAmount(row),
                row.comment.orEmpty()
            )
            builder.append(fields.joinToString(SEPARATOR) { escape(it) }).append(LINE_BREAK)
        }
        return builder.toString()
    }

    fun kindLabel(kind: OperationKind): String = when (kind) {
        OperationKind.INCOME -> "Доход"
        OperationKind.EXPENSE -> "Расход"
        OperationKind.TRANSFER -> "Перевод"
    }

    fun signedAmount(row: ReportRow): String {
        val formatted = Money.formatManat(row.amountMinor)
        return if (row.kind == OperationKind.EXPENSE) "-$formatted" else formatted
    }

    private fun escape(field: String): String {
        val needsQuotes = field.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }
}
