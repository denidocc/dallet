package com.dallet.app.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.ExchangeRate
import com.dallet.app.ui.AddExpenseUiState
import com.dallet.app.ui.AddOperationKind
import com.dallet.app.ui.SelectableOption
import com.dallet.app.ui.common.AmountCard
import com.dallet.app.ui.common.FormDateField
import com.dallet.app.ui.common.FormScrollColumn
import com.dallet.app.ui.common.FormSection
import com.dallet.app.ui.common.FormTimeField
import com.dallet.app.ui.common.SegmentedChoice

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
    val kinds = listOf(AddOperationKind.EXPENSE, AddOperationKind.INCOME, AddOperationKind.TRANSFER)
    FormScrollColumn {
        Text(
            text = stringResource(R.string.add_operation_title),
            style = MaterialTheme.typography.titleLarge
        )
        SegmentedChoice(
            labels = listOf(
                stringResource(R.string.add_kind_expense),
                stringResource(R.string.add_kind_income),
                stringResource(R.string.add_kind_transfer)
            ),
            selectedIndex = kinds.indexOf(state.kind),
            onSelected = { onKindSelected(kinds[it]) }
        )

        AmountCard(
            value = state.amountInput,
            onChange = onAmountChange,
            rates = rates,
            canConvert = state.kind != AddOperationKind.TRANSFER
        )

        when (state.kind) {
            AddOperationKind.EXPENSE, AddOperationKind.INCOME -> {
                FormSection {
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
            }
            AddOperationKind.TRANSFER -> {
                FormSection {
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
        }

        FormSection {
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
        }
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
