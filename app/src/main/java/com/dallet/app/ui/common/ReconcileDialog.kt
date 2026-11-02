package com.dallet.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.Money
import com.dallet.app.ui.LocalCurrency

/**
 * Works out the amount from the real balance: the user types what the account holds now and the
 * difference to the balance in the app becomes the amount of an expense or an income.
 */
@Composable
fun ReconcileDialog(
    balanceMinor: Long,
    isExpense: Boolean,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var actualText by remember { mutableStateOf("") }
    val actual = if (actualText.isBlank()) null else runCatching { Money.parseManat(actualText) }.getOrNull()
    val diff = actual?.let { if (isExpense) balanceMinor - it else it - balanceMinor }
    val code = LocalCurrency.current.code
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reconcile_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.reconcile_in_app, Money.formatManat(balanceMinor), code),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = actualText,
                    onValueChange = { actualText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reconcile_actual)) },
                    suffix = { Text(code) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                when {
                    actualText.isBlank() -> Unit
                    diff == null -> Text(
                        text = stringResource(R.string.rates_bad_amount),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    diff > 0L -> Text(
                        text = stringResource(
                            if (isExpense) R.string.reconcile_diff_expense else R.string.reconcile_diff_income,
                            Money.formatManat(diff),
                            code
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    diff == 0L -> Text(
                        text = stringResource(R.string.reconcile_equal),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    else -> Text(
                        text = stringResource(
                            if (isExpense) R.string.reconcile_wrong_expense else R.string.reconcile_wrong_income
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { diff?.let { onApply(Money.formatManat(it)) } },
                enabled = diff != null && diff > 0L
            ) {
                Text(stringResource(R.string.reconcile_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
