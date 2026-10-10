package com.dallet.app.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import androidx.compose.material3.TextButton
import com.dallet.app.ui.rates.ConvertDialog
import com.dallet.app.ledger.ExchangeRate
import com.dallet.app.ui.LocalCurrency
import com.dallet.app.ui.AddExpenseUiState
import com.dallet.app.ui.AddOperationKind
import com.dallet.app.ui.SelectableOption
import com.dallet.app.ui.common.FormDateField
import com.dallet.app.ui.common.FormTimeField
import com.dallet.app.ui.common.FormScrollColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    state: AddExpenseUiState,
    onKindSelected: (AddOperationKind) -> Unit,
    onAccountSelected: (Long) -> Unit,
    onFromAccountSelected: (Long) -> Unit,
    onToAccountSelected: (Long) -> Unit,
    onCategorySelected: (Long) -> Unit,
    onAmountChange: (String) -> Unit,
    rates: List<ExchangeRate>,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    FormScrollColumn {
        Text(
            text = stringResource(R.string.add_operation_title),
            style = MaterialTheme.typography.titleLarge
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.kind == AddOperationKind.EXPENSE,
                onClick = { onKindSelected(AddOperationKind.EXPENSE) },
                label = { Text(stringResource(R.string.add_kind_expense)) }
            )
            FilterChip(
                selected = state.kind == AddOperationKind.INCOME,
                onClick = { onKindSelected(AddOperationKind.INCOME) },
                label = { Text(stringResource(R.string.add_kind_income)) }
            )
            FilterChip(
                selected = state.kind == AddOperationKind.TRANSFER,
                onClick = { onKindSelected(AddOperationKind.TRANSFER) },
                label = { Text(stringResource(R.string.add_kind_transfer)) }
            )
        }

        when (state.kind) {
            AddOperationKind.EXPENSE, AddOperationKind.INCOME -> {
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
            AddOperationKind.TRANSFER -> {
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

        OutlinedTextField(
            value = state.amountInput,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.add_amount_label)) },
            suffix = { Text(LocalCurrency.current.code) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        if (state.kind != AddOperationKind.TRANSFER) {
            var showConvert by remember { mutableStateOf(false) }
            TextButton(onClick = { showConvert = true }) {
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
        state.errorMessage?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = onSubmit,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.add_save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionDropdown(
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
