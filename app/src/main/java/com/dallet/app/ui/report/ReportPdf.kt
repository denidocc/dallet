package com.dallet.app.ui.report

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.dallet.app.AppStrings
import com.dallet.app.R
import com.dallet.app.ledger.CategoryExpenseTotal
import com.dallet.app.ledger.DebtDirection
import com.dallet.app.ledger.Money
import com.dallet.app.ledger.OperationKind
import com.dallet.app.ledger.PeriodReport
import com.dallet.app.ledger.ReportCsv
import com.dallet.app.ledger.percentChange
import java.io.ByteArrayOutputStream
import kotlin.math.abs

object ReportPdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_RIGHT = PAGE_WIDTH - MARGIN

    private const val INK = 0xFF111111.toInt()
    private const val MUTED = 0xFF6B6B70.toInt()
    private const val LINE = 0xFFD8D8DB.toInt()
    private const val GREEN = 0xFF2E7D32.toInt()
    private const val RED = 0xFFC62828.toInt()

    private fun paint(size: Float, bold: Boolean = false, color: Int = INK) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                if (bold) Typeface.BOLD else Typeface.NORMAL
            )
        }

    private val titlePaint = paint(20f, bold = true)
    private val headingPaint = paint(13f, bold = true)
    private val bodyPaint = paint(11f)
    private val boldPaint = paint(11f, bold = true)
    private val mutedPaint = paint(10f, color = MUTED)
    private val linePaint = Paint().apply {
        color = LINE
        strokeWidth = 0.8f
    }

    fun render(
        report: PeriodReport,
        title: String,
        createdOn: String,
        incompleteNote: String?,
        currencyCode: String,
        strings: AppStrings
    ): ByteArray {
        this.currencyCode = currencyCode
        val document = PdfDocument()
        val writer = PageWriter(document, strings)
        val current = report.comparison.current
        val previous = report.comparison.previous

        writer.line(strings.get(R.string.pdf_report_title, title), titlePaint, gap = 26f)
        writer.line(strings.get(R.string.pdf_created, createdOn), mutedPaint, gap = 14f)
        if (incompleteNote != null) {
            writer.line(incompleteNote, mutedPaint, gap = 14f)
        }
        writer.space(14f)

        writer.heading(strings.get(R.string.pdf_totals))
        writer.summaryRow(
            label = strings.get(R.string.analytics_income_label),
            value = money(current.incomeTotalMinor),
            valueColor = GREEN,
            change = percentChange(current.incomeTotalMinor, previous.incomeTotalMinor)
        )
        writer.summaryRow(
            label = strings.get(R.string.analytics_expense_label),
            value = money(current.expenseTotalMinor),
            valueColor = RED,
            change = percentChange(current.expenseTotalMinor, previous.expenseTotalMinor)
        )
        val balance = current.incomeTotalMinor - current.expenseTotalMinor
        writer.summaryRow(
            label = strings.get(R.string.pdf_total_period),
            value = (if (balance < 0) "−" else "") + money(abs(balance)),
            valueColor = INK,
            change = null,
            bold = true
        )
        writer.space(10f)

        writer.categoryBlock(
            strings.get(R.string.analytics_by_category),
            current.expensesByCategory,
            current.expenseTotalMinor
        )
        writer.categoryBlock(
            strings.get(R.string.analytics_income_by_category),
            current.incomesByCategory,
            current.incomeTotalMinor
        )

        if (report.goals.isNotEmpty()) {
            writer.heading(strings.get(R.string.pdf_goals))
            report.goals.forEach { item ->
                writer.pairRow(
                    label = item.goal.name,
                    value = strings.get(
                        R.string.pdf_goal_value,
                        money(item.savedMinor),
                        money(item.goal.targetMinor),
                        item.percent
                    )
                )
            }
            writer.space(12f)
        }
        if (report.debts.isNotEmpty()) {
            writer.heading(strings.get(R.string.pdf_debts))
            report.debts.forEach { item ->
                val direction = strings.get(
                    if (item.debt.direction == DebtDirection.LENT) R.string.analytics_debts_owed_to_me
                    else R.string.analytics_debts_i_owe
                )
                writer.pairRow(
                    label = item.debt.person + " · " + direction,
                    value = money(item.remainingMinor)
                )
            }
            writer.space(12f)
        }

        writer.heading(strings.get(R.string.pdf_operations))
        if (report.rows.isEmpty()) {
            writer.line(strings.get(R.string.pdf_no_operations), mutedPaint, gap = 14f)
        } else {
            writer.tableHeader()
            report.rows.forEach { row ->
                val description = listOf(row.accountName, row.categoryName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                val color = when (row.kind) {
                    OperationKind.INCOME -> GREEN
                    OperationKind.EXPENSE -> RED
                    OperationKind.TRANSFER -> INK
                }
                writer.tableRow(
                    date = row.date.toString(),
                    time = ReportCsv.timeLabel(row.time),
                    kind = strings.get(
                        when (row.kind) {
                            OperationKind.EXPENSE -> R.string.add_kind_expense
                            OperationKind.INCOME -> R.string.add_kind_income
                            OperationKind.TRANSFER -> R.string.add_kind_transfer
                        }
                    ),
                    description = description,
                    comment = row.comment.orEmpty(),
                    amount = ReportCsv.signedAmount(row),
                    amountColor = color
                )
            }
        }

        writer.finish()
        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    private var currencyCode: String = "ТМТ"

    private fun money(minor: Long): String = Money.formatManat(minor) + " " + currencyCode

    private class PageWriter(
        private val document: PdfDocument,
        private val strings: AppStrings
    ) {
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = MARGIN

        init {
            startPage()
        }

        private fun startPage() {
            page?.let { document.finishPage(it) }
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val newPage = document.startPage(info)
            page = newPage
            canvas = newPage.canvas
            y = MARGIN
        }

        private fun ensure(space: Float) {
            if (y + space > PAGE_HEIGHT - MARGIN) startPage()
        }

        fun finish() {
            page?.let { document.finishPage(it) }
            page = null
        }

        fun space(height: Float) {
            y += height
        }

        fun line(text: String, paint: Paint, gap: Float) {
            ensure(gap)
            canvas.drawText(
                fit(text, paint, CONTENT_RIGHT - MARGIN),
                MARGIN,
                y + paint.textSize,
                paint
            )
            y += gap
        }

        fun heading(text: String) {
            ensure(34f)
            canvas.drawText(text, MARGIN, y + headingPaint.textSize, headingPaint)
            y += 20f
            canvas.drawLine(MARGIN, y, CONTENT_RIGHT, y, linePaint)
            y += 10f
        }

        fun summaryRow(
            label: String,
            value: String,
            valueColor: Int,
            change: Int?,
            bold: Boolean = false
        ) {
            ensure(36f)
            val labelPaint = if (bold) boldPaint else bodyPaint
            canvas.drawText(label, MARGIN, y + 11f, labelPaint)
            val valuePaint = paint(11f, bold = true, color = valueColor)
            canvas.drawText(
                value,
                CONTENT_RIGHT - valuePaint.measureText(value),
                y + 11f,
                valuePaint
            )
            if (change != null) {
                val text = when {
                    change > 0 -> strings.get(R.string.pdf_change_up, change)
                    change < 0 -> strings.get(R.string.pdf_change_down, abs(change))
                    else -> strings.get(R.string.pdf_change_same)
                }
                canvas.drawText(
                    text,
                    CONTENT_RIGHT - mutedPaint.measureText(text),
                    y + 24f,
                    mutedPaint
                )
                y += 14f
            }
            y += 22f
        }

        fun pairRow(label: String, value: String) {
            ensure(20f)
            val valueWidth = bodyPaint.measureText(value)
            canvas.drawText(
                fit(label, bodyPaint, CONTENT_RIGHT - MARGIN - valueWidth - 12f),
                MARGIN,
                y + 11f,
                bodyPaint
            )
            canvas.drawText(value, CONTENT_RIGHT - valueWidth, y + 11f, bodyPaint)
            y += 20f
        }

        fun categoryBlock(title: String, rows: List<CategoryExpenseTotal>, total: Long) {
            if (rows.isEmpty()) return
            heading(title)
            rows.forEach { row ->
                ensure(20f)
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = row.colorArgb }
                canvas.drawCircle(MARGIN + 4f, y + 7f, 4f, dot)
                canvas.drawText(
                    fit(row.categoryName, bodyPaint, 260f),
                    MARGIN + 16f,
                    y + 11f,
                    bodyPaint
                )
                val share = if (total > 0L) {
                    Math.round(row.amountMinor * 100.0 / total).toInt()
                } else {
                    0
                }
                val shareText = "$share%"
                canvas.drawText(
                    shareText,
                    400f - mutedPaint.measureText(shareText),
                    y + 11f,
                    mutedPaint
                )
                val amount = money(row.amountMinor)
                canvas.drawText(
                    amount,
                    CONTENT_RIGHT - bodyPaint.measureText(amount),
                    y + 11f,
                    bodyPaint
                )
                y += 20f
            }
            y += 12f
        }

        fun tableHeader() {
            ensure(20f)
            canvas.drawText(strings.get(R.string.add_date_label), MARGIN, y + 10f, mutedPaint)
            canvas.drawText(strings.get(R.string.add_time_label), MARGIN + 62f, y + 10f, mutedPaint)
            canvas.drawText(strings.get(R.string.pdf_col_kind), MARGIN + 100f, y + 10f, mutedPaint)
            canvas.drawText(strings.get(R.string.pdf_col_account_category), MARGIN + 156f, y + 10f, mutedPaint)
            val head = strings.get(R.string.add_amount_label)
            canvas.drawText(head, CONTENT_RIGHT - mutedPaint.measureText(head), y + 10f, mutedPaint)
            y += 16f
        }

        fun tableRow(
            date: String,
            time: String,
            kind: String,
            description: String,
            comment: String,
            amount: String,
            amountColor: Int
        ) {
            val hasComment = comment.isNotBlank()
            ensure(if (hasComment) 34f else 20f)
            canvas.drawText(date, MARGIN, y + 11f, bodyPaint)
            canvas.drawText(time, MARGIN + 62f, y + 11f, bodyPaint)
            canvas.drawText(kind, MARGIN + 100f, y + 11f, bodyPaint)
            canvas.drawText(fit(description, bodyPaint, 200f), MARGIN + 156f, y + 11f, bodyPaint)
            val amountPaint = paint(11f, bold = true, color = amountColor)
            canvas.drawText(
                amount,
                CONTENT_RIGHT - amountPaint.measureText(amount),
                y + 11f,
                amountPaint
            )
            y += 15f
            if (hasComment) {
                canvas.drawText(fit(comment, mutedPaint, 300f), MARGIN + 156f, y + 9f, mutedPaint)
                y += 13f
            }
            canvas.drawLine(MARGIN, y + 2f, CONTENT_RIGHT, y + 2f, linePaint)
            y += 6f
        }

        private fun fit(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            val count = paint.breakText(text, true, maxWidth - paint.measureText("…"), null)
            return text.take(count).trimEnd() + "…"
        }
    }
}
