package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class ReportTest {

    private fun newLedger(): Ledger = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    private fun row(
        kind: OperationKind = OperationKind.EXPENSE,
        amountMinor: Long = 1_250L,
        account: String = "Наличные",
        category: String = "Еда",
        comment: String? = null,
        date: LocalDate = LocalDate.of(2026, 7, 1)
    ) = ReportRow(date, kind, account, category, amountMinor, comment)

    @Test
    fun csv_startsWithBomAndHeader() {
        val csv = ReportCsv.encode(emptyList())

        assertThat(csv).startsWith("﻿")
        assertThat(csv.removePrefix("﻿").lines().first())
            .isEqualTo("Дата;Вид;Счёт;Категория;Сумма;Комментарий")
    }

    @Test
    fun csv_expenseIsNegative_incomeIsPositive() {
        val csv = ReportCsv.encode(
            listOf(
                row(kind = OperationKind.EXPENSE, amountMinor = 1_250L),
                row(kind = OperationKind.INCOME, amountMinor = 10_000L, category = "Зарплата")
            )
        )
        val lines = csv.removePrefix("﻿").trim().lines()

        assertThat(lines[1]).isEqualTo("2026-07-01;Расход;Наличные;Еда;-12.50;")
        assertThat(lines[2]).isEqualTo("2026-07-01;Доход;Наличные;Зарплата;100.00;")
    }

    @Test
    fun csv_transferAmountIsUnsigned() {
        val csv = ReportCsv.encode(
            listOf(
                row(
                    kind = OperationKind.TRANSFER,
                    amountMinor = 500L,
                    account = "ЗП карта → Наличные",
                    category = ""
                )
            )
        )

        assertThat(csv.removePrefix("﻿").trim().lines()[1])
            .isEqualTo("2026-07-01;Перевод;ЗП карта → Наличные;;5.00;")
    }

    @Test
    fun csv_escapesSeparatorsQuotesAndNewlines() {
        val csv = ReportCsv.encode(
            listOf(row(comment = "хлеб; \"молоко\"\nи сыр"))
        )

        assertThat(csv).contains(";\"хлеб; \"\"молоко\"\"\nи сыр\"")
    }

    @Test
    fun csv_usesCrLfLineBreaks() {
        val csv = ReportCsv.encode(listOf(row(), row()))

        assertThat(csv.removePrefix("﻿").split("\r\n")).hasSize(4)
    }

    @Test
    fun periodReport_containsOnlyPeriodOperationsSortedByDate() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account
        val food = ledger.categories(CategoryKind.EXPENSE).first { it.name == "Еда" }
        val salary = ledger.categories(CategoryKind.INCOME).first { it.name == "Зарплата" }

        ledger.addExpense(cash.id, food.id, 900L, LocalDate.of(2026, 7, 20), "поздний")
        ledger.addIncome(card.id, salary.id, 5_000L, LocalDate.of(2026, 7, 5), null)
        ledger.addExpense(cash.id, food.id, 100L, LocalDate.of(2026, 6, 30), "другой месяц")

        val report = ledger.periodReport(
            AnalyticsPeriod.MONTH,
            asOf = LocalDate.of(2026, 7, 15),
            today = LocalDate.of(2026, 7, 15)
        )

        assertThat(report.rows.map { it.date })
            .containsExactly(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 20)).inOrder()
        assertThat(report.rows[0].categoryName).isEqualTo("Зарплата")
        assertThat(report.rows[0].accountName).isEqualTo(Ledger.CARD_NAME)
        assertThat(report.rows[1].comment).isEqualTo("поздний")
    }

    @Test
    fun periodReport_transferShowsBothAccounts() = runTest {
        val ledger = newLedger()
        ledger.completeOnboarding(cashOpeningMinor = 0L, cardOpeningMinor = 0L)
        val cash = ledger.accountBalances().first { it.account.name == Ledger.CASH_NAME }.account
        val card = ledger.accountBalances().first { it.account.name == Ledger.CARD_NAME }.account

        ledger.addTransfer(card.id, cash.id, 700L, LocalDate.of(2026, 7, 3), null)

        val report = ledger.periodReport(
            AnalyticsPeriod.MONTH,
            asOf = LocalDate.of(2026, 7, 15),
            today = LocalDate.of(2026, 7, 15)
        )

        assertThat(report.rows).hasSize(1)
        assertThat(report.rows[0].kind).isEqualTo(OperationKind.TRANSFER)
        assertThat(report.rows[0].accountName).isEqualTo("${Ledger.CARD_NAME} → ${Ledger.CASH_NAME}")
        assertThat(report.rows[0].categoryName).isEmpty()
    }
}
