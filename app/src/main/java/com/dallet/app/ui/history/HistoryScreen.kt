package com.dallet.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.OperationKind
import com.dallet.app.ui.moneyText
import com.dallet.app.ui.HistoryDayUi
import com.dallet.app.ui.HistoryKindUi
import com.dallet.app.ui.HistoryRowUi
import com.dallet.app.ui.HistoryUiState
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor

/** Everything the history screen can do to the filter. */
class HistoryFilterActions(
    val onQueryChange: (String) -> Unit,
    val onKindSelected: (OperationKind?) -> Unit,
    val onAccountToggled: (Long) -> Unit,
    val onCategoryToggled: (Long) -> Unit,
    val onClearRestrictions: () -> Unit,
    val onClearFilter: () -> Unit
)

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOperationClick: (Long) -> Unit,
    actions: HistoryFilterActions
) {
    var showFilters by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (state.hasOperations || state.isFiltered) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions.onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.history_search_hint)) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { actions.onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.history_clear_search)
                                )
                            }
                        }
                    }
                )
                OutlinedButton(onClick = { showFilters = true }) {
                    Text(
                        if (state.restrictionCount > 0) {
                            stringResource(R.string.history_filters_count, state.restrictionCount)
                        } else {
                            stringResource(R.string.history_filters)
                        }
                    )
                }
            }
            ActiveRestrictions(state = state, actions = actions)
        }
        if (state.isFiltered && state.days.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.history_found_summary,
                    state.foundCount,
                    moneyText(state.foundExpenseLabel),
                    moneyText(state.foundIncomeLabel)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (!state.hasOperations) {
            SurfaceCard {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (state.days.isEmpty()) {
            SurfaceCard {
                Text(
                    text = stringResource(R.string.history_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = actions.onClearFilter) {
                    Text(stringResource(R.string.history_reset_filter))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.days.forEach { day ->
                    item(key = "day-${day.dateKey}") { DayHeader(day = day) }
                    items(day.rows, key = { it.id }) { row ->
                        SurfaceCard(onClick = { onOperationClick(row.id) }) {
                            HistoryRow(row = row)
                        }
                    }
                }
            }
        }
    }
    if (showFilters) {
        HistoryFilterSheet(
            state = state,
            onKindSelected = actions.onKindSelected,
            onAccountToggled = actions.onAccountToggled,
            onCategoryToggled = actions.onCategoryToggled,
            onReset = actions.onClearRestrictions,
            onDismiss = { showFilters = false }
        )
    }
}

/** The restrictions that are on, as chips that can be removed one by one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveRestrictions(state: HistoryUiState, actions: HistoryFilterActions) {
    if (state.restrictionCount == 0) return
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.kind?.let { kind ->
            RestrictionChip(label = stringResource(kindLabelRes(kind))) {
                actions.onKindSelected(null)
            }
        }
        state.accountOptions.filter { it.id in state.selectedAccountIds }.forEach { option ->
            RestrictionChip(label = option.label) { actions.onAccountToggled(option.id) }
        }
        state.categoryOptions.filter { it.id in state.selectedCategoryIds }.forEach { option ->
            RestrictionChip(label = option.label) { actions.onCategoryToggled(option.id) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestrictionChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

private fun kindLabelRes(kind: OperationKind): Int = when (kind) {
    OperationKind.EXPENSE -> R.string.add_kind_expense
    OperationKind.INCOME -> R.string.add_kind_income
    OperationKind.TRANSFER -> R.string.add_kind_transfer
}

@Composable
private fun DayHeader(day: HistoryDayUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = day.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            day.incomeLabel?.let {
                Text(
                    text = "+" + moneyText(it),
                    style = MaterialTheme.typography.labelLarge,
                    color = incomeAmountColor()
                )
            }
            day.expenseLabel?.let {
                Text(
                    text = "−" + moneyText(it),
                    style = MaterialTheme.typography.labelLarge,
                    color = expenseAmountColor()
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(row: HistoryRowUi) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = row.kindLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = moneyText(row.amountLabel),
                color = row.amountColor(),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            text = row.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (row.subtitle.isNotBlank()) {
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun HistoryRowUi.amountColor() = when (kind) {
    HistoryKindUi.EXPENSE -> expenseAmountColor()
    HistoryKindUi.INCOME -> incomeAmountColor()
    HistoryKindUi.TRANSFER -> MaterialTheme.colorScheme.onSurface
}
