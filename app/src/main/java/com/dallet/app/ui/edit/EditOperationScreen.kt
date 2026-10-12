package com.dallet.app.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import androidx.compose.material3.TextButton
import com.dallet.app.ui.rates.ConvertDialog
import com.dallet.app.ledger.ExchangeRate
import com.dallet.app.ui.LocalCurrency
import com.dallet.app.ledger.OperationKind
import com.dallet.app.ui.EditOperationUiState
import com.dallet.app.ui.SelectableOption
import com.dallet.app.ui.common.FormDateField
import com.dallet.app.ui.common.FormTimeField
import com.dallet.app.ui.common.FormScrollColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditOperationScreen(
    state: EditOperationUiState,
    onAccountSelected: (Long) -> Unit,
    onFromAccountSelected: (Long) -> Unit,
    onToAccountSelected: (Long) -> Unit,
    onCategorySelected: (Long) -> Unit,
    onAmountChange: (String) -> Unit,
    rates: List<ExchangeRate>,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    if (state.loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    FormScrollColumn {
        Text(
            text = stringResource(R.string.edit_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = when (state.kind) {
                OperationKind.EXPENSE -> stringResource(R.string.add_kind_expense)
                OperationKind.INCOME -> stringResource(R.string.add_kind_income)
                OperationKind.TRANSFER -> stringResource(R.string.add_kind_transfer)
            },
            style = MaterialTheme.typography.labelMedium
        )

        when (state.kind) {
            OperationKind.EXPENSE, OperationKind.INCOME -> {
                OptionDropdown(
                    label = stringResource(R.string.add_account_label),
                    options = state.accounts,
                    selectedId = state.selectedAccountId,
                    onSelected = onAccountSelected
                )
                OptionDropdown(
                    label = stringResource(R.string.add_category_label),
                    options = state.categories,
                    selectedId = state.selectedCategoryId,
                    onSelected = onCategorySelected
                )
            }
            OperationKind.TRANSFER -> {
                OptionDropdown(
                    label = stringResource(R.string.add_from_account_label),
                    options = state.accounts,
                    selectedId = state.selectedFromAccountId,
                    onSelected = onFromAccountSelected
                )
                OptionDropdown(
                    label = stringResource(R.string.add_to_account_label),
                    options = state.accounts,
                    selectedId = state.selectedToAccountId,
                    onSelected = onToAccountSelected
                )
            }
        }

        val lockedTitle = state.amountLockedBy
        OutlinedTextField(
            value = state.amountInput,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.add_amount_label)) },
            suffix = { Text(LocalCurrency.current.code) },
            singleLine = true,
            enabled = state.amountLockedBy == null,
            supportingText = if (lockedTitle != null) {
                { Text(stringResource(R.string.edit_amount_from_purchase, lockedTitle)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        if (state.kind != OperationKind.TRANSFER && state.amountLockedBy == null) {
            var showConvert by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showConvert = true }) {
                Text(stringResource(R.string.rates_convert))
            }
            if (showConvert) {
                ConvertDialog(
                    main = LocalCurrency.current,
                    rates = rates,
                    onApply = {
                        onAmountChange(it)
                        showConvert = false
                    },
                    onDismiss = { showConvert = false }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormDateField(
                dateInput = state.dateInput,
                onDateSelected = onDateChange,
                modifier = Modifier.weight(1.4f)
            )
            FormTimeField(
                timeInput = state.timeInput,
                onTimeSelected = onTimeChange,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = state.commentInput,
            onValueChange = onCommentChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.add_comment_label)) },
            singleLine = true
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionDropdown(
    label: String,
    options: List<SelectableOption>,
    selectedId: Long?,
    onSelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.id == selectedId }?.label.orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
