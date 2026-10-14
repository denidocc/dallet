package com.dallet.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dallet.app.R

/** An account that would go below zero after the pending money movement. */
data class OverdraftPrompt(
    val accountName: String,
    val balanceAfterLabel: String
)

@Composable
fun OverdraftDialog(
    prompt: OverdraftPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.overdraft_title)) },
        text = {
            Text(stringResource(R.string.overdraft_text, prompt.accountName, prompt.balanceAfterLabel))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.overdraft_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
