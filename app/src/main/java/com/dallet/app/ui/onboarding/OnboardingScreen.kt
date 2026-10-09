package com.dallet.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.Currency
import com.dallet.app.ui.DataUiState
import com.dallet.app.ui.OnboardingUiState

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    dataState: DataUiState,
    onCurrencySelected: (Currency) -> Unit,
    onTemplateToggled: (String, Boolean) -> Unit,
    onTemplateOpeningChange: (String, String) -> Unit,
    onCustomNameChange: (String) -> Unit,
    onCustomOpeningChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRestoreClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.onboarding_currency),
            style = MaterialTheme.typography.titleMedium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Currency.values().forEach { currency ->
                FilterChip(
                    selected = currency == state.currency,
                    onClick = { onCurrencySelected(currency) },
                    label = { Text(currency.code) }
                )
            }
        }
        Text(
            text = stringResource(R.string.onboarding_accounts),
            style = MaterialTheme.typography.titleMedium
        )
        state.rows.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = row.enabled,
                    onCheckedChange = { onTemplateToggled(row.name, it) }
                )
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (row.enabled) {
                OutlinedTextField(
                    value = row.openingInput,
                    onValueChange = { onTemplateOpeningChange(row.name, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.accounts_opening_label)) },
                    suffix = { Text(state.currency.code) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }
        OutlinedTextField(
            value = state.customName,
            onValueChange = onCustomNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onboarding_custom_account)) },
            singleLine = true
        )
        if (state.customName.isNotBlank()) {
            OutlinedTextField(
                value = state.customOpening,
                onValueChange = onCustomOpeningChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.accounts_opening_label)) },
                suffix = { Text(state.currency.code) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Button(
            onClick = onSubmit,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboarding_continue))
        }
        dataState.message?.takeIf { dataState.isError }?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        TextButton(
            onClick = onRestoreClick,
            enabled = !dataState.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboarding_restore))
        }
    }
}
