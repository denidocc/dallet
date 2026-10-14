package com.dallet.app.ui.recurring

import com.dallet.app.ui.common.ConfirmDeleteDialog
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import com.dallet.app.ui.common.AppButtonShape
import com.dallet.app.ui.common.OutlineAction
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
import com.dallet.app.ledger.Money
import com.dallet.app.ui.common.SegmentedChoice
import com.dallet.app.ui.payoffPlan
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
                        rule.progress?.let { progress ->
                            Text(
                                text = progress,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = moneyText(rule.amountLabel),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rule.isIncome) incomeAmountColor() else expenseAmountColor()
                    )
                }
            }
        }
        Button(shape = AppButtonShape, onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
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
    onPayoffChange: (Boolean) -> Unit,
    onPayoffMonthsSelected: (Int?) -> Unit,
    onEndDateChange: (String) -> Unit,
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
        SegmentedChoice(
            labels = listOf(
                stringResource(R.string.recurring_mode_payment),
                stringResource(R.string.recurring_mode_payoff)
            ),
            selectedIndex = if (state.payoff) 1 else 0,
            onSelected = { onPayoffChange(it == 1) }
        )
        OutlinedTextField(
            value = state.amountInput,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    stringResource(
                        if (state.payoff) R.string.recurring_total_label else R.string.add_amount_label
                    )
                )
            },
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
        if (state.payoff) {
            Text(
                text = stringResource(R.string.recurring_mode_payoff),
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 6, 9, 12, 24).forEach { months ->
                    FilterChip(
                        selected = state.payoffMonths == months,
                        onClick = { onPayoffMonthsSelected(months) },
                        label = { Text(stringResource(R.string.recurring_months, months)) }
                    )
                }
                FilterChip(
                    selected = state.payoffMonths == null,
                    onClick = { onPayoffMonthsSelected(null) },
                    label = { Text(stringResource(R.string.recurring_pick_date)) }
                )
            }
            if (state.payoffMonths == null) {
                FormDateField(
                    dateInput = state.endDateInput,
                    onDateSelected = onEndDateChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val plan = state.payoffPlan()
            Text(
                text = if (plan == null) {
                    stringResource(R.string.recurring_plan_invalid)
                } else if (plan.lastPaymentMinor == plan.paymentMinor) {
                    stringResource(
                        R.string.recurring_plan,
                        plan.count,
                        Money.formatManat(plan.paymentMinor) + " " + LocalCurrency.current.code
                    )
                } else {
                    stringResource(
                        R.string.recurring_plan_last,
                        plan.count,
                        Money.formatManat(plan.paymentMinor) + " " + LocalCurrency.current.code,
                        Money.formatManat(plan.lastPaymentMinor) + " " + LocalCurrency.current.code
                    )
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (plan == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary
            )
        }
        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Button(shape = AppButtonShape, 
            onClick = onSave,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.add_save))
        }
        if (!state.isNew) {
            var confirmDelete by remember { mutableStateOf(false) }
            OutlineAction(
                text = stringResource(R.string.edit_delete),
                onClick = { confirmDelete = true },
                danger = true
            )
            if (confirmDelete) {
                ConfirmDeleteDialog(
                    title = stringResource(R.string.delete_recurring_title),
                    text = stringResource(R.string.delete_recurring_text),
                    onConfirm = {
                        confirmDelete = false
                        onDelete()
                    },
                    onDismiss = { confirmDelete = false }
                )
            }
        }
    }
}
