package com.dallet.app.ui.debts

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.DebtDirection
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
private fun DebtProgressBar(percent: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
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
fun DebtsScreen(
    state: DebtsListUi,
    accounts: List<SelectableOption>,
    onDebtClick: (Long) -> Unit,
    onCreate: (String, DebtDirection, String, Long?, String) -> Unit,
    onMessageShown: () -> Unit
) {
    var tab by remember { mutableStateOf(DebtDirection.LENT) }
    var showCreate by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.debts_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        state.message?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.debts_total_owed_to_me), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = moneyText(state.owedToMeTotalLabel),
                    style = MaterialTheme.typography.titleMedium,
                    color = incomeAmountColor()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.debts_total_i_owe), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = moneyText(state.iOweTotalLabel),
                    style = MaterialTheme.typography.titleMedium,
                    color = expenseAmountColor()
                )
            }
        }
        SegmentedChoice(
            labels = listOf(
                stringResource(R.string.debts_tab_lent),
                stringResource(R.string.debts_tab_borrowed)
            ),
            selectedIndex = if (tab == DebtDirection.LENT) 0 else 1,
            onSelected = { tab = if (it == 0) DebtDirection.LENT else DebtDirection.BORROWED }
        )
        Button(
            shape = AppButtonShape,
            onClick = {
                onMessageShown()
                showCreate = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.debts_new))
        }
        val rows = if (tab == DebtDirection.LENT) state.owedToMe else state.iOwe
        if (state.loaded && rows.isEmpty()) {
            Text(
                text = stringResource(
                    if (tab == DebtDirection.LENT) R.string.debts_empty_lent else R.string.debts_empty_borrowed
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        rows.forEach { row ->
            SurfaceCard(onClick = { onDebtClick(row.id) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.person,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (row.settled) stringResource(R.string.debts_settled)
                        else moneyText(row.remainingLabel),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                DebtProgressBar(percent = row.percent, modifier = Modifier.padding(vertical = 10.dp))
                Text(
                    text = stringResource(R.string.debts_of_total, moneyText(row.totalLabel)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (showCreate) {
        NewDebtDialog(
            initialDirection = tab,
            accounts = accounts,
            onConfirm = { person, direction, amount, accountId, note ->
                showCreate = false
                onCreate(person, direction, amount, accountId, note)
            },
            onDismiss = { showCreate = false }
        )
    }
}

class DebtDetailActions(
    val onRepay: (String, Long?) -> Unit,
    val onUpdate: (String, String) -> Unit,
    val onWriteOff: () -> Unit,
    val onDelete: () -> Unit,
    val onUndoPayment: (Long) -> Unit,
    val onMessageShown: () -> Unit
)

@Composable
fun DebtDetailScreen(state: DebtDetailUi, actions: DebtDetailActions) {
    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.missing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.debts_missing))
        }
        return
    }
    var showRepay by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showWriteOff by remember { mutableStateOf(false) }
    var paymentToUndo by remember { mutableStateOf<DebtEntryUi?>(null) }
    val lent = state.direction == DebtDirection.LENT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = state.person,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(end = 48.dp)
        )
        Text(
            text = stringResource(if (lent) R.string.debts_kind_lent else R.string.debts_kind_borrowed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.note.isNotBlank()) {
            Text(
                text = state.note,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.message?.let {
            Text(
                text = it,
                color = if (state.isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }
        SurfaceCard {
            Text(
                text = if (state.settled) stringResource(R.string.debts_settled)
                else moneyText(state.remainingLabel),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.debts_of_total, moneyText(state.totalLabel)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DebtProgressBar(percent = state.percent, modifier = Modifier.padding(top = 12.dp))
        }
        if (!state.settled) {
            Button(
                shape = AppButtonShape,
                onClick = {
                    actions.onMessageShown()
                    showRepay = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (lent) R.string.debts_repay_lent else R.string.debts_repay_borrowed))
            }
        }

        Text(
            text = stringResource(R.string.debts_history),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        state.entries.forEach { entry ->
            SurfaceCard(onClick = if (entry.isIssue) null else ({ paymentToUndo = entry })) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                entry.isWriteOff && lent -> stringResource(R.string.debts_entry_writeoff_lent)
                                entry.isWriteOff -> stringResource(R.string.debts_entry_writeoff_borrowed)
                                entry.isIssue && lent -> stringResource(R.string.debts_entry_issue_lent)
                                entry.isIssue -> stringResource(R.string.debts_entry_issue_borrowed)
                                lent -> stringResource(R.string.debts_entry_payment_lent)
                                else -> stringResource(R.string.debts_entry_payment_borrowed)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = entry.dateLabel + " · " +
                                (entry.accountName ?: stringResource(R.string.debts_no_balance)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = moneyText(entry.amountLabel),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        OutlineAction(
            text = stringResource(R.string.debts_edit),
            onClick = { showEdit = true },
            modifier = Modifier.padding(top = 8.dp)
        )
        if (!state.settled) {
            OutlineAction(
                text = stringResource(R.string.debts_writeoff),
                onClick = { showWriteOff = true }
            )
        }
        OutlineAction(
            text = stringResource(R.string.debts_delete),
            onClick = { showDelete = true },
            danger = true
        )
    }

    if (showRepay) {
        RepayDialog(
            lent = lent,
            initialAmount = state.remainingInput,
            accounts = state.accounts,
            onConfirm = { amount, accountId ->
                showRepay = false
                actions.onRepay(amount, accountId)
            },
            onDismiss = { showRepay = false }
        )
    }
    if (showEdit) {
        EditDebtDialog(
            initialPerson = state.person,
            initialNote = state.note,
            onSave = { person, note ->
                showEdit = false
                actions.onUpdate(person, note)
            },
            onDismiss = { showEdit = false }
        )
    }
    if (showWriteOff) {
        AlertDialog(
            onDismissRequest = { showWriteOff = false },
            title = { Text(stringResource(R.string.debts_writeoff)) },
            text = {
                Text(
                    stringResource(
                        if (lent) R.string.debts_writeoff_text_lent else R.string.debts_writeoff_text_borrowed,
                        moneyText(state.remainingLabel)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWriteOff = false
                        actions.onWriteOff()
                    }
                ) {
                    Text(stringResource(R.string.debts_writeoff_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWriteOff = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.debts_delete_title)) },
            text = { Text(stringResource(R.string.debts_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        actions.onDelete()
                    }
                ) {
                    Text(stringResource(R.string.debts_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
    paymentToUndo?.let { entry ->
        AlertDialog(
            onDismissRequest = { paymentToUndo = null },
            title = { Text(stringResource(R.string.debts_undo_title)) },
            text = { Text(stringResource(R.string.debts_undo_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        paymentToUndo = null
                        actions.onUndoPayment(entry.id)
                    }
                ) {
                    Text(stringResource(R.string.debts_undo_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToUndo = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun AccountChoice(
    label: String,
    accounts: List<SelectableOption>,
    accountId: Long?,
    onAccountChange: (Long) -> Unit,
    keepBalance: Boolean,
    onKeepBalanceChange: (Boolean) -> Unit
) {
    if (!keepBalance) {
        OptionDropdown(
            label = label,
            options = accounts,
            selectedId = accountId,
            onSelected = onAccountChange
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = keepBalance, onCheckedChange = onKeepBalanceChange)
        Text(
            text = stringResource(R.string.debts_keep_balance),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun NewDebtDialog(
    initialDirection: DebtDirection,
    accounts: List<SelectableOption>,
    onConfirm: (String, DebtDirection, String, Long?, String) -> Unit,
    onDismiss: () -> Unit
) {
    var direction by remember { mutableStateOf(initialDirection) }
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var keepBalance by remember { mutableStateOf(accounts.isEmpty()) }
    val lent = direction == DebtDirection.LENT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debts_new)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SegmentedChoice(
                    labels = listOf(
                        stringResource(R.string.debts_tab_lent),
                        stringResource(R.string.debts_tab_borrowed)
                    ),
                    selectedIndex = if (lent) 0 else 1,
                    onSelected = {
                        direction = if (it == 0) DebtDirection.LENT else DebtDirection.BORROWED
                    }
                )
                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it },
                    label = { Text(stringResource(R.string.debts_person)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.add_amount_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                AccountChoice(
                    label = stringResource(
                        if (lent) R.string.debts_account_lent else R.string.debts_account_borrowed
                    ),
                    accounts = accounts,
                    accountId = accountId,
                    onAccountChange = { accountId = it },
                    keepBalance = keepBalance,
                    onKeepBalanceChange = { keepBalance = it }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.debts_note)) },
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(person, direction, amount, if (keepBalance) null else accountId, note)
                },
                enabled = person.isNotBlank() && amount.isNotBlank()
            ) {
                Text(stringResource(R.string.goals_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
private fun RepayDialog(
    lent: Boolean,
    initialAmount: String,
    accounts: List<SelectableOption>,
    onConfirm: (String, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var keepBalance by remember { mutableStateOf(accounts.isEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (lent) R.string.debts_repay_lent else R.string.debts_repay_borrowed))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.add_amount_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                AccountChoice(
                    label = stringResource(
                        if (lent) R.string.debts_repay_account_lent else R.string.debts_repay_account_borrowed
                    ),
                    accounts = accounts,
                    accountId = accountId,
                    onAccountChange = { accountId = it },
                    keepBalance = keepBalance,
                    onKeepBalanceChange = { keepBalance = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(amount, if (keepBalance) null else accountId) },
                enabled = amount.isNotBlank()
            ) {
                Text(stringResource(R.string.goals_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
private fun EditDebtDialog(
    initialPerson: String,
    initialNote: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var person by remember { mutableStateOf(initialPerson) }
    var note by remember { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debts_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it },
                    label = { Text(stringResource(R.string.debts_person)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.debts_note)) },
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(person, note) }, enabled = person.isNotBlank()) {
                Text(stringResource(R.string.goals_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
