package com.dallet.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.AccountRowUi
import com.dallet.app.ui.ExpenseRowUi
import com.dallet.app.ui.HomeUiState

@Composable
fun HomeScreen(state: HomeUiState) {
    when {
        state.loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_total_label),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = stringResource(
                        R.string.amount_with_currency,
                        state.totalBalanceLabel
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.home_accounts_label),
                    style = MaterialTheme.typography.labelMedium
                )
                state.accountRows.forEach { row ->
                    AccountBalanceRow(row)
                }
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.home_recent_expenses),
                    style = MaterialTheme.typography.labelMedium
                )
                if (state.recentExpenses.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_recent_empty),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    state.recentExpenses.forEach { row ->
                        ExpenseRow(row)
                    }
                }
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountBalanceRow(row: AccountRowUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = row.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.amount_with_currency, row.balanceLabel),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ExpenseRow(row: ExpenseRowUi) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = row.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.amount_with_currency, row.amountLabel),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        if (row.subtitle.isNotBlank()) {
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
