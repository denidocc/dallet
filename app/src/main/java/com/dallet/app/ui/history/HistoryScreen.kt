package com.dallet.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.HistoryKindUi
import com.dallet.app.ui.HistoryRowUi
import com.dallet.app.ui.HistoryUiState

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onOperationClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (state.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.rows, key = { it.id }) { row ->
                    HistoryRow(row = row, onClick = { onOperationClick(row.id) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    row: HistoryRowUi,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = row.kindLabel, style = MaterialTheme.typography.labelMedium)
            Text(
                text = stringResource(R.string.amount_with_currency, row.amountLabel),
                color = row.amountColor(),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Text(text = row.title, style = MaterialTheme.typography.bodyLarge)
        if (row.subtitle.isNotBlank()) {
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryRowUi.amountColor() = when (kind) {
    HistoryKindUi.EXPENSE -> MaterialTheme.colorScheme.error
    HistoryKindUi.INCOME -> MaterialTheme.colorScheme.primary
    HistoryKindUi.TRANSFER -> MaterialTheme.colorScheme.onSurface
}
