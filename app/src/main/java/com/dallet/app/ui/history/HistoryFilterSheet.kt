package com.dallet.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.CategoryKind
import com.dallet.app.ledger.HistoryPeriod
import com.dallet.app.ledger.OperationKind
import com.dallet.app.ui.HistoryUiState
import com.dallet.app.ui.common.DateRangePickerDialog
import java.time.LocalDate

/** Bottom sheet with the restrictions of the history filter: kind, accounts and categories. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryFilterSheet(
    state: HistoryUiState,
    onKindSelected: (OperationKind?) -> Unit,
    onAccountToggled: (Long) -> Unit,
    onCategoryToggled: (Long) -> Unit,
    onPeriodSelected: (HistoryPeriod) -> Unit,
    onCustomPeriod: (LocalDate, LocalDate) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var showRangePicker by remember { mutableStateOf(false) }
    if (showRangePicker) {
        DateRangePickerDialog(
            initialFrom = state.customFrom,
            initialTo = state.customTo,
            onConfirm = { from, to ->
                showRangePicker = false
                onCustomPeriod(from, to)
            },
            onDismiss = { showRangePicker = false }
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.history_filters),
                style = MaterialTheme.typography.titleLarge
            )

            SectionTitle(stringResource(R.string.history_filter_kind))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.kind == null,
                    onClick = { onKindSelected(null) },
                    label = { Text(stringResource(R.string.history_filter_any)) }
                )
                FilterChip(
                    selected = state.kind == OperationKind.EXPENSE,
                    onClick = { onKindSelected(OperationKind.EXPENSE) },
                    label = { Text(stringResource(R.string.add_kind_expense)) }
                )
                FilterChip(
                    selected = state.kind == OperationKind.INCOME,
                    onClick = { onKindSelected(OperationKind.INCOME) },
                    label = { Text(stringResource(R.string.add_kind_income)) }
                )
                FilterChip(
                    selected = state.kind == OperationKind.TRANSFER,
                    onClick = { onKindSelected(OperationKind.TRANSFER) },
                    label = { Text(stringResource(R.string.add_kind_transfer)) }
                )
            }

            SectionTitle(stringResource(R.string.history_filter_period))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.period == HistoryPeriod.ALL_TIME,
                    onClick = { onPeriodSelected(HistoryPeriod.ALL_TIME) },
                    label = { Text(stringResource(R.string.history_period_all)) }
                )
                FilterChip(
                    selected = state.period == HistoryPeriod.THIS_MONTH,
                    onClick = { onPeriodSelected(HistoryPeriod.THIS_MONTH) },
                    label = { Text(stringResource(R.string.history_period_this_month)) }
                )
                FilterChip(
                    selected = state.period == HistoryPeriod.LAST_MONTH,
                    onClick = { onPeriodSelected(HistoryPeriod.LAST_MONTH) },
                    label = { Text(stringResource(R.string.history_period_last_month)) }
                )
                FilterChip(
                    selected = state.period == HistoryPeriod.THIS_YEAR,
                    onClick = { onPeriodSelected(HistoryPeriod.THIS_YEAR) },
                    label = { Text(stringResource(R.string.history_period_this_year)) }
                )
                FilterChip(
                    selected = state.period == HistoryPeriod.CUSTOM,
                    onClick = { showRangePicker = true },
                    label = {
                        Text(
                            state.customPeriodLabel.takeIf { state.period == HistoryPeriod.CUSTOM }
                                ?: stringResource(R.string.history_period_custom)
                        )
                    }
                )
            }

            SectionTitle(stringResource(R.string.history_filter_accounts))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accountOptions.forEach { option ->
                    FilterChip(
                        selected = option.id in state.selectedAccountIds,
                        onClick = { onAccountToggled(option.id) },
                        label = { Text(option.label) }
                    )
                }
            }

            // Transfers have no category, so there is nothing to choose for them.
            if (state.kind != OperationKind.TRANSFER) {
                SectionTitle(stringResource(R.string.history_filter_categories))
                val categories = state.categoryOptions.filter { option ->
                    when (state.kind) {
                        OperationKind.EXPENSE -> option.kind == CategoryKind.EXPENSE
                        OperationKind.INCOME -> option.kind == CategoryKind.INCOME
                        else -> true
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { option ->
                        FilterChip(
                            selected = option.id in state.selectedCategoryIds,
                            onClick = { onCategoryToggled(option.id) },
                            label = { Text(option.label) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.history_filter_reset))
                }
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.history_filter_done))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
