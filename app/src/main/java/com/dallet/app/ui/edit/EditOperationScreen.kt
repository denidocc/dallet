package com.dallet.app.ui.edit

import com.dallet.app.ui.common.ConfirmDeleteDialog
import com.dallet.app.ui.common.OutlineAction
import com.dallet.app.ui.common.AppButtonShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.ExchangeRate
import com.dallet.app.ledger.OperationKind
import com.dallet.app.ui.EditOperationUiState
import com.dallet.app.ui.PurchaseUi
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.SelectableOption
import com.dallet.app.ui.common.AmountCard
import com.dallet.app.ui.common.FormDateField
import com.dallet.app.ui.common.FormScrollColumn
import com.dallet.app.ui.common.FormSection
import com.dallet.app.ui.common.FormTimeField

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
    onDelete: () -> Unit,
    onOpenPurchase: (Long) -> Unit
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val lockedTitle = state.amountLockedBy
        AmountCard(
            value = state.amountInput,
            onChange = onAmountChange,
            rates = rates,
            enabled = lockedTitle == null,
            supporting = lockedTitle?.let { stringResource(R.string.edit_amount_from_purchase, it) },
            canConvert = state.kind != OperationKind.TRANSFER
        )

        state.purchase?.let { purchase ->
            PurchaseCard(purchase = purchase, onOpen = { onOpenPurchase(purchase.listId) })
        }

        FormSection {
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
        state.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        Button(shape = AppButtonShape, 
            onClick = onSave,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.edit_save))
        }
        var confirmDelete by remember { mutableStateOf(false) }
        OutlineAction(
            text = stringResource(R.string.edit_delete),
            onClick = { confirmDelete = true },
            enabled = !state.submitting,
            danger = true
        )
        if (confirmDelete) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.delete_operation_title),
                text = stringResource(R.string.delete_operation_text),
                onConfirm = {
                    confirmDelete = false
                    onDelete()
                },
                onDismiss = { confirmDelete = false }
            )
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

@Composable
private fun PurchaseCard(purchase: PurchaseUi, onOpen: () -> Unit) {
    SurfaceCard(onClick = onOpen) {
        Text(
            text = stringResource(R.string.edit_purchase_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = purchase.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        if (purchase.items.isEmpty()) {
            Text(
                text = stringResource(R.string.edit_purchase_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        purchase.items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.totalLabel.isNotEmpty()) {
                    Text(text = item.totalLabel, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Text(
            text = stringResource(R.string.edit_purchase_open),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
