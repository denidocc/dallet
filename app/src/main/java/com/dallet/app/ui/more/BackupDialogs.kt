package com.dallet.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dallet.app.R

const val MIN_BACKUP_PASSWORD = 6

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

/**
 * Asks for a new password (typed twice). An empty password is accepted only when
 * [allowEmpty] is set; callers treat it as "no password".
 */
@Composable
fun NewPasswordDialog(
    title: String,
    hint: String,
    confirmLabel: String,
    allowEmpty: Boolean,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = password.isNotEmpty() && password.length < MIN_BACKUP_PASSWORD
    val mismatch = repeat.isNotEmpty() && repeat != password
    val valid = if (password.isEmpty() && repeat.isEmpty()) {
        allowEmpty
    } else {
        password.length >= MIN_BACKUP_PASSWORD && password == repeat
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, style = MaterialTheme.typography.bodyMedium)
                PasswordField(password, { password = it }, stringResource(R.string.backup_password))
                PasswordField(repeat, { repeat = it }, stringResource(R.string.backup_password_repeat))
                if (tooShort) {
                    Text(
                        text = stringResource(R.string.backup_password_short, MIN_BACKUP_PASSWORD),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (mismatch) {
                    Text(
                        text = stringResource(R.string.backup_password_mismatch),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

/** Asks for a password before a backup file is saved; empty saves a plain file. */
@Composable
fun ExportPasswordDialog(onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    NewPasswordDialog(
        title = stringResource(R.string.export_password_title),
        hint = stringResource(R.string.export_password_hint),
        confirmLabel = stringResource(R.string.export_password_save),
        allowEmpty = true,
        onConfirm = onConfirm,
        onCancel = onCancel
    )
}

/** Asks for the password of an encrypted backup file. */
@Composable
fun ImportPasswordDialog(wrong: Boolean, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.import_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.import_password_text),
                    style = MaterialTheme.typography.bodyMedium
                )
                PasswordField(password, { password = it }, stringResource(R.string.backup_password))
                if (wrong) {
                    Text(
                        text = stringResource(R.string.import_password_wrong),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.import_password_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
