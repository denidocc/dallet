package com.dallet.app.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.AddExpenseUiState
import com.dallet.app.ui.SelectableOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    state: AddExpenseUiState,
    onAccountSelected: (Long) -> Unit,
    onCategorySelected: (Long) -> Unit,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.add_expense_title),
            style = MaterialTheme.typography.titleLarge
        )
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
        OutlinedTextField(
            value = state.amountInput,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.add_amount_label)) },
            suffix = { Text(stringResource(R.string.currency_tmt)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = state.dateInput,
            onValueChange = onDateChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.add_date_label)) },
            supportingText = { Text(stringResource(R.string.add_date_hint)) },
            singleLine = true
        )
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
