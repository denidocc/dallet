package com.dallet.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.Currency
import com.dallet.app.ui.DataUiState
import com.dallet.app.ui.OnboardingUiState
import com.dallet.app.ui.templateNameRes

private const val PAGE_WELCOME = 0
private const val PAGE_CURRENCY = 1
private const val PAGE_ACCOUNTS = 2
private const val PAGE_BALANCES = 3
private const val PAGE_COUNT = 4

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
    var page by rememberSaveable { mutableIntStateOf(PAGE_WELCOME) }
    val hasAccount = state.rows.any { it.enabled } || state.customName.isNotBlank()
    var showPickError by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(PAGE_COUNT) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            color = if (index <= page) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (page) {
                PAGE_WELCOME -> {
                    Text(
                        text = stringResource(R.string.onboarding_welcome_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.onboarding_welcome_text),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                PAGE_CURRENCY -> {
                    Text(
                        text = stringResource(R.string.onboarding_currency),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.onboarding_currency_hint),
                        style = MaterialTheme.typography.bodyLarge
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
                }

                PAGE_ACCOUNTS -> {
                    Text(
                        text = stringResource(R.string.onboarding_accounts),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.onboarding_accounts_hint),
                        style = MaterialTheme.typography.bodyLarge
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
                                text = templateNameRes(row.name)?.let { stringResource(it) } ?: row.name,
                                style = MaterialTheme.typography.bodyLarge
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
                    if (showPickError > 0 && !hasAccount) {
                        Text(
                            text = stringResource(R.string.onboarding_pick_one),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.accounts_opening_label),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.onboarding_balances_hint),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    state.rows.filter { it.enabled }.forEach { row ->
                        OutlinedTextField(
                            value = row.openingInput,
                            onValueChange = { onTemplateOpeningChange(row.name, it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(templateNameRes(row.name)?.let { stringResource(it) } ?: row.name) },
                            suffix = { Text(state.currency.code) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    if (state.customName.isNotBlank()) {
                        OutlinedTextField(
                            value = state.customOpening,
                            onValueChange = onCustomOpeningChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(state.customName.trim()) },
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
                }
            }
            if (page == PAGE_WELCOME) {
                dataState.message?.takeIf { dataState.isError }?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (page > PAGE_WELCOME) {
                OutlinedButton(
                    onClick = { page -= 1 },
                    enabled = !state.submitting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }
            Button(
                onClick = {
                    when {
                        page == PAGE_ACCOUNTS && !hasAccount -> showPickError += 1
                        page == PAGE_BALANCES -> onSubmit()
                        else -> page += 1
                    }
                },
                enabled = !state.submitting,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(
                        when (page) {
                            PAGE_WELCOME -> R.string.onboarding_start
                            PAGE_BALANCES -> R.string.onboarding_finish
                            else -> R.string.onboarding_next
                        }
                    )
                )
            }
        }
        if (page == PAGE_WELCOME) {
            TextButton(
                onClick = onRestoreClick,
                enabled = !dataState.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.onboarding_restore))
            }
        }
    }
}
