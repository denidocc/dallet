package com.dallet.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.DataUiState
import com.dallet.app.ui.OnboardingUiState

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    dataState: DataUiState,
    onCashChange: (String) -> Unit,
    onCardChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRestoreClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
        OutlinedTextField(
            value = state.cashInput,
            onValueChange = onCashChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onboarding_cash_label)) },
            suffix = { Text(stringResource(R.string.currency_tmt)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = state.cardInput,
            onValueChange = onCardChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onboarding_card_label)) },
            suffix = { Text(stringResource(R.string.currency_tmt)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
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
