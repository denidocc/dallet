package com.dallet.app.ui.recurring

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import com.dallet.app.ledger.Frequency
import com.dallet.app.ledger.OperationKind
import com.dallet.app.ui.LocalCurrency
import com.dallet.app.ui.RecurringEditorUiState
import com.dallet.app.ui.RecurringUiState
import com.dallet.app.ui.add.OptionDropdown
import com.dallet.app.ui.common.FormDateField
import com.dallet.app.ui.common.FormScrollColumn
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.moneyText
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor

@Composable
fun RecurringScreen(
    state: RecurringUiState,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.recurring_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(end = 48.dp, bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.recurring_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.rules.isEmpty()) {
            Text(
                text = stringResource(R.string.recurring_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        state.rules.forEach { rule ->
            SurfaceCard(onClick = { onEdit(rule.id) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = rule.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = rule.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = moneyText(rule.amountLabel),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rule.isIncome) incomeAmountColor() else expenseAmountColor()
                    )
                }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.recurring_add))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditorScreen(
    state: RecurringEditorUiState,
    onKindSelected: (OperationKind) -> Unit,
    onAccountSelected: (Long) -> Unit,
    onCategorySelected: (Long) -> Unit,
    onAmountChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onFrequencySelected: (Frequency) -> Unit,
    onStartDateChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    FormScrollColumn {
        Text(
            text = stringResource(
                if (state.isNew) R.string.recurring_new_title else R.string.recurring_edit_title
            ),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(end = 48.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.kind == OperationKind.EXPENSE,
                onClick = { onKindSelected(OperationKind.EXPENSE) },
                label = { Text(stringResource(R.string.add_kind_expense)) }
            )
            FilterChip(
                selected = state.kind == OperationKind.INCOME,
                onClick = { onKindSelected(OperationKind.INCOME) },
                label = { Text(stringResource(R.string.add_kind_income)) }
            )
        }
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
            suffix = { Text(LocalCurrency.current.code) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = state.commentInput,
            onValueChange = onCommentChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.add_comment_label)) }
        )
        Text(
            text = stringResource(R.string.recurring_frequency),
            style = MaterialTheme.typography.titleMedium
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Frequency.values().forEach { option ->
                FilterChip(
                    selected = option == state.frequency,
                    onClick = { onFrequencySelected(option) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    Frequency.WEEKLY -> R.string.freq_weekly
                                    Frequency.MONTHLY -> R.string.freq_monthly
                                    Frequency.YEARLY -> R.string.freq_yearly
                                }
                            )
                        )
                    }
                )
            }
        }
        FormDateField(
            dateInput = state.startDateInput,
            onDateSelected = onStartDateChange,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.recurring_start_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Button(
            onClick = onSave,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.add_save))
        }
        if (!state.isNew) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.edit_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
