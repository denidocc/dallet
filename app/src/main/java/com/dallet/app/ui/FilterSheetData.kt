package com.dallet.app.ui

import com.dallet.app.ledger.HistoryPeriod
import com.dallet.app.ledger.OperationKind
import java.time.LocalDate

/** What the filters sheet shows and edits; the History and each side of a comparison use it. */
data class FilterSheetData(
    val kind: OperationKind? = null,
    val period: HistoryPeriod = HistoryPeriod.ALL_TIME,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val customPeriodLabel: String? = null,
    val selectedAccountIds: Set<Long> = emptySet(),
    val selectedCategoryIds: Set<Long> = emptySet(),
    val accountOptions: List<SelectableOption> = emptyList(),
    val categoryOptions: List<HistoryCategoryOption> = emptyList()
)

fun HistoryUiState.toSheetData() = FilterSheetData(
    kind = kind,
    period = period,
    customFrom = customFrom,
    customTo = customTo,
    customPeriodLabel = customPeriodLabel,
    selectedAccountIds = selectedAccountIds,
    selectedCategoryIds = selectedCategoryIds,
    accountOptions = accountOptions,
    categoryOptions = categoryOptions
)
