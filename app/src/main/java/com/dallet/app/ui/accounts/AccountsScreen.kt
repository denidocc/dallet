package com.dallet.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.AccountEditorUiState
import com.dallet.app.ui.AccountsUiState
import com.dallet.app.ui.common.SurfaceCard

@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onAddClick: () -> Unit,
    onAccountClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.accounts_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(
            onClick = onAddClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.accounts_add))
        }
        LazyColumn(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.rows, key = { it.id }) { row ->
                SurfaceCard(onClick = { onAccountClick(row.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = row.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(
                                    R.string.accounts_opening,
                                    row.openingBalanceLabel
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.amount_with_currency, row.balanceLabel),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccountEditorScreen(
    state: AccountEditorUiState,
    onNameChange: (String) -> Unit,
    onOpeningChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (state.isNew) {
                stringResource(R.string.accounts_new_title)
            } else {
                stringResource(R.string.accounts_edit_title)
            },
            style = MaterialTheme.typography.titleLarge
        )
        OutlinedTextField(
            value = state.nameInput,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.accounts_name_label)) },
            singleLine = true
        )
        OutlinedTextField(
            value = state.openingInput,
            onValueChange = onOpeningChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.accounts_opening_label)) },
            suffix = { Text(stringResource(R.string.currency_tmt)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        state.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = onSave,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.edit_save))
        }
        if (!state.isNew) {
            Button(
                onClick = onDelete,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.edit_delete))
            }
        }
    }
}
