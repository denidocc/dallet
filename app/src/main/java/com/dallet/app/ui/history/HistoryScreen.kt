package com.dallet.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.moneyText
import com.dallet.app.ui.HistoryDayUi
import com.dallet.app.ui.HistoryKindUi
import com.dallet.app.ui.HistoryRowUi
import com.dallet.app.ui.HistoryUiState
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOperationClick: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearFilter: () -> Unit
) {
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
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.history_search_hint)) },
                trailingIcon = {
                    if (state.isFiltered) {
                        IconButton(onClick = onClearFilter) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.history_reset_filter)
                            )
                        }
                    }
                }
            )
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
                TextButton(onClick = onClearFilter) {
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
