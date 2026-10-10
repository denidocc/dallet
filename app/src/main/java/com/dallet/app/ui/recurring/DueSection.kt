package com.dallet.app.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.DueItemUi
import com.dallet.app.ui.LocalCurrency
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.moneyText
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor

/** "To pay" block for the home screen: due recurring operations to confirm or skip. */
@Composable
fun DueSection(
    items: List<DueItemUi>,
    onConfirm: (DueItemUi, String, (Boolean) -> Unit) -> Unit,
    onSkip: (DueItemUi) -> Unit
) {
    if (items.isEmpty()) return
    var selected by remember { mutableStateOf<DueItemUi?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.due_title),
            style = MaterialTheme.typography.titleMedium
        )
        items.forEach { item ->
            SurfaceCard(onClick = if (item.isFirst) ({ selected = item }) else null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = listOf(item.dateLabel, item.accountName)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!item.isFirst) {
                            Text(
                                text = stringResource(R.string.due_after_previous),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = moneyText(item.amountInput),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.isIncome) incomeAmountColor() else expenseAmountColor()
                    )
                }
            }
        }
    }

    selected?.let { item ->
        var amount by remember(item) { mutableStateOf(item.amountInput) }
        var failed by remember(item) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(item.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = item.dateLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = {
                            amount = it
                            failed = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        label = { Text(stringResource(R.string.add_amount_label)) },
                        suffix = { Text(LocalCurrency.current.code) },
                        isError = failed,
                        supportingText = if (failed) {
                            { Text(stringResource(R.string.budgets_bad_limit)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(item, amount) { ok ->
                            if (ok) selected = null else failed = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.due_confirm))
                }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = {
                            onSkip(item)
                            selected = null
                        }
                    ) {
                        Text(stringResource(R.string.due_skip))
                    }
                    TextButton(onClick = { selected = null }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            }
        )
    }
}
