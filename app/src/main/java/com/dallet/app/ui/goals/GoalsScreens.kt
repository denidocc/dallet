package com.dallet.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.SelectableOption
import com.dallet.app.ui.add.OptionDropdown
import com.dallet.app.ui.common.AppButtonShape
import com.dallet.app.ui.common.OutlineAction
import com.dallet.app.ui.common.SegmentedChoice
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.moneyText
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor

@Composable
private fun GoalProgressBar(percent: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun GoalsScreen(
    state: GoalsListUi,
    onGoalClick: (Long) -> Unit,
    onCreate: (String, String) -> Unit,
    onMessageShown: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.goals_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        state.message?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        Button(
            shape = AppButtonShape,
            onClick = {
                onMessageShown()
                showCreate = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.goals_new))
        }
        if (state.loaded && state.goals.isEmpty()) {
            Text(
                text = stringResource(R.string.goals_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.goals.forEach { goal ->
            SurfaceCard(onClick = { onGoalClick(goal.id) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    val badge = when {
                        goal.archived -> stringResource(R.string.goals_archived)
                        goal.reached -> stringResource(R.string.goals_reached)
                        else -> "${goal.percent}%"
                    }
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GoalProgressBar(percent = goal.percent, modifier = Modifier.padding(vertical = 10.dp))
                Text(
                    text = moneyText(goal.savedLabel) + " / " + moneyText(goal.targetLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (showCreate) {
        GoalEditDialog(
            title = stringResource(R.string.goals_new),
            initialName = "",
            initialTarget = "",
            onSave = { name, target ->
                showCreate = false
                onCreate(name, target)
            },
            onDismiss = { showCreate = false }
        )
    }
}

class GoalDetailActions(
    val onDeposit: (DepositMode, String, Long?, Long?) -> Unit,
    val onWithdraw: (String, Long?) -> Unit,
    val onUpdate: (String, String) -> Unit,
    val onArchive: (Boolean) -> Unit,
    val onDelete: () -> Unit,
    val onDeleteEntry: (Long) -> Unit,
    val onMessageShown: () -> Unit
)

@Composable
fun GoalDetailScreen(state: GoalDetailUi, actions: GoalDetailActions) {
    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.missing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.goals_missing))
        }
        return
    }
    var showDeposit by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var entryToUndo by remember { mutableStateOf<GoalEntryUi?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = state.name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(end = 48.dp)
        )
        state.message?.let {
            Text(
                text = it,
                color = if (state.isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }
        SurfaceCard {
            Text(
                text = moneyText(state.savedLabel),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.goals_of_target, moneyText(state.targetLabel)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GoalProgressBar(percent = state.percent, modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = if (state.reached) {
                    stringResource(R.string.goals_reached_text)
                } else {
                    stringResource(R.string.goals_remaining, moneyText(state.remainingLabel), state.percent)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!state.archived) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    shape = AppButtonShape,
                    onClick = {
                        actions.onMessageShown()
                        showDeposit = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.goals_deposit))
                }
                OutlinedButton(
                    shape = AppButtonShape,
                    onClick = {
                        actions.onMessageShown()
                        showWithdraw = true
                    },
                    enabled = state.entries.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.goals_withdraw))
                }
            }
        }

        Text(
            text = stringResource(R.string.goals_history),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (state.entries.isEmpty()) {
            Text(
                text = stringResource(R.string.goals_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.entries.forEach { entry ->
            SurfaceCard(onClick = { entryToUndo = entry }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (entry.isDeposit) {
                                if (entry.asIncome) stringResource(R.string.goals_entry_income)
                                else stringResource(R.string.goals_entry_deposit)
                            } else {
                                stringResource(R.string.goals_entry_withdraw)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = entry.dateLabel + " · " + entry.accountName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = entry.amountLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (entry.isDeposit) incomeAmountColor() else expenseAmountColor()
                    )
                }
            }
        }

        OutlineAction(
            text = stringResource(R.string.goals_edit),
            onClick = { showEdit = true },
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlineAction(
            text = stringResource(if (state.archived) R.string.goals_unarchive else R.string.goals_archive),
            onClick = { actions.onArchive(!state.archived) }
        )
        OutlineAction(
            text = stringResource(R.string.goals_delete),
            onClick = { showDelete = true },
            danger = true
        )
    }

    if (showDeposit) {
        DepositDialog(
            accounts = state.accounts,
            categories = state.incomeCategories,
            onConfirm = { mode, amount, accountId, categoryId ->
                showDeposit = false
                actions.onDeposit(mode, amount, accountId, categoryId)
            },
            onDismiss = { showDeposit = false }
        )
    }
    if (showWithdraw) {
        WithdrawDialog(
            accounts = state.accounts,
            onConfirm = { amount, accountId ->
                showWithdraw = false
                actions.onWithdraw(amount, accountId)
            },
            onDismiss = { showWithdraw = false }
        )
    }
    if (showEdit) {
        GoalEditDialog(
            title = stringResource(R.string.goals_edit),
            initialName = state.name,
            initialTarget = state.targetInput,
            onSave = { name, target ->
                showEdit = false
                actions.onUpdate(name, target)
            },
            onDismiss = { showEdit = false }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.goals_delete_title)) },
            text = { Text(stringResource(R.string.goals_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        actions.onDelete()
                    }
                ) {
                    Text(stringResource(R.string.goals_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
    entryToUndo?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToUndo = null },
            title = { Text(stringResource(R.string.goals_undo_title)) },
            text = {
                Text(
                    stringResource(
                        if (entry.asIncome) R.string.goals_undo_text_income else R.string.goals_undo_text
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        entryToUndo = null
                        actions.onDeleteEntry(entry.id)
                    }
                ) {
                    Text(stringResource(R.string.goals_undo_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToUndo = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun GoalEditDialog(
    title: String,
    initialName: String,
    initialTarget: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var target by remember { mutableStateOf(initialTarget) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.goals_name)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.goals_target)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, target) }, enabled = name.isNotBlank() && target.isNotBlank()) {
                Text(stringResource(R.string.goals_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
private fun DepositDialog(
    accounts: List<SelectableOption>,
    categories: List<SelectableOption>,
    onConfirm: (DepositMode, String, Long?, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(DepositMode.FROM_ACCOUNT) }
    var amount by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var categoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goals_deposit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SegmentedChoice(
                    labels = listOf(
                        stringResource(R.string.goals_mode_account),
                        stringResource(R.string.goals_mode_income)
                    ),
                    selectedIndex = if (mode == DepositMode.FROM_ACCOUNT) 0 else 1,
                    onSelected = {
                        mode = if (it == 0) DepositMode.FROM_ACCOUNT else DepositMode.AS_INCOME
                    }
                )
                Text(
                    text = stringResource(
                        if (mode == DepositMode.FROM_ACCOUNT) R.string.goals_mode_account_hint
                        else R.string.goals_mode_income_hint
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.add_amount_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OptionDropdown(
                    label = stringResource(R.string.add_account_label),
                    options = accounts,
                    selectedId = accountId,
                    onSelected = { accountId = it }
                )
                if (mode == DepositMode.AS_INCOME) {
                    OptionDropdown(
                        label = stringResource(R.string.add_category_label),
                        options = categories,
                        selectedId = categoryId,
                        onSelected = { categoryId = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(mode, amount, accountId, categoryId) },
                enabled = amount.isNotBlank()
            ) {
                Text(stringResource(R.string.goals_deposit_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
private fun WithdrawDialog(
    accounts: List<SelectableOption>,
    onConfirm: (String, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goals_withdraw)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.goals_withdraw_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.add_amount_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OptionDropdown(
                    label = stringResource(R.string.goals_withdraw_to),
                    options = accounts,
                    selectedId = accountId,
                    onSelected = { accountId = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(amount, accountId) }, enabled = amount.isNotBlank()) {
                Text(stringResource(R.string.goals_withdraw_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
