package com.dallet.app.ui.report

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.dallet.app.ledger.CategoryExpenseTotal
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
        incompleteNote: String?
    ): ByteArray {
        val document = PdfDocument()
        val writer = PageWriter(document)
        val current = report.comparison.current
        val previous = report.comparison.previous

        writer.line("Отчёт: $title", titlePaint, gap = 26f)
        writer.line("Dallet · создан $createdOn", mutedPaint, gap = 14f)
        if (incompleteNote != null) {
            writer.line(incompleteNote, mutedPaint, gap = 14f)
        }
        writer.space(14f)

        writer.heading("Итоги")
        writer.summaryRow(
            label = "Доходы",
            value = money(current.incomeTotalMinor),
            valueColor = GREEN,
            change = percentChange(current.incomeTotalMinor, previous.incomeTotalMinor)
        )
        writer.summaryRow(
            label = "Расходы",
            value = money(current.expenseTotalMinor),
            valueColor = RED,
            change = percentChange(current.expenseTotalMinor, previous.expenseTotalMinor)
        )
        val balance = current.incomeTotalMinor - current.expenseTotalMinor
        writer.summaryRow(
            label = "Итого за период",
            value = (if (balance < 0) "−" else "") + money(abs(balance)),
            valueColor = INK,
            change = null,
            bold = true
        )
        writer.space(10f)

        writer.categoryBlock(
            "Расходы по категориям",
            current.expensesByCategory,
            current.expenseTotalMinor
        )
        writer.categoryBlock(
            "Доходы по категориям",
            current.incomesByCategory,
            current.incomeTotalMinor
        )

        writer.heading("Операции")
        if (report.rows.isEmpty()) {
            writer.line("Операций за период нет", mutedPaint, gap = 14f)
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
                    kind = ReportCsv.kindLabel(row.kind),
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

    private fun money(minor: Long): String = Money.formatManat(minor) + " ТМТ"

    private class PageWriter(private val document: PdfDocument) {
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
                    change > 0 -> "+$change% к прошлому периоду"
                    change < 0 -> "−${abs(change)}% к прошлому периоду"
                    else -> "без изменений к прошлому периоду"
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
            canvas.drawText("Дата", MARGIN, y + 10f, mutedPaint)
            canvas.drawText("Вид", MARGIN + 66f, y + 10f, mutedPaint)
            canvas.drawText("Счёт · категория", MARGIN + 126f, y + 10f, mutedPaint)
            val head = "Сумма"
            canvas.drawText(head, CONTENT_RIGHT - mutedPaint.measureText(head), y + 10f, mutedPaint)
            y += 16f
        }

        fun tableRow(
            date: String,
            kind: String,
            description: String,
            comment: String,
            amount: String,
            amountColor: Int
        ) {
            val hasComment = comment.isNotBlank()
            ensure(if (hasComment) 34f else 20f)
            canvas.drawText(date, MARGIN, y + 11f, bodyPaint)
            canvas.drawText(kind, MARGIN + 66f, y + 11f, bodyPaint)
            canvas.drawText(fit(description, bodyPaint, 230f), MARGIN + 126f, y + 11f, bodyPaint)
            val amountPaint = paint(11f, bold = true, color = amountColor)
            canvas.drawText(
                amount,
                CONTENT_RIGHT - amountPaint.measureText(amount),
                y + 11f,
                amountPaint
            )
            y += 15f
            if (hasComment) {
                canvas.drawText(fit(comment, mutedPaint, 330f), MARGIN + 126f, y + 9f, mutedPaint)
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
