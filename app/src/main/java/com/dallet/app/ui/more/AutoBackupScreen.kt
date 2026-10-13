package com.dallet.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.dallet.app.backup.AutoBackupState
import com.dallet.app.ui.common.SurfaceCard
import java.text.DateFormat
import java.util.Date

@Composable
fun AutoBackupScreen(
    state: AutoBackupState,
    busy: Boolean,
    onPickFolder: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRunNow: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.autobackup_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.autobackup_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SurfaceCard(onClick = onPickFolder) {
            Text(stringResource(R.string.autobackup_folder), style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.folderName ?: stringResource(R.string.autobackup_folder_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SurfaceCard(onClick = { showPassword = true }) {
            Text(stringResource(R.string.autobackup_password), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    if (state.hasPassword) R.string.autobackup_password_set
                    else R.string.autobackup_password_none
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.autobackup_enable), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.autobackup_enable_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = state.folderUri != null
                )
            }
        }
        if (state.lastError) {
            Text(
                text = stringResource(R.string.autobackup_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (state.lastAtMillis > 0L) {
            Text(
                text = stringResource(
                    R.string.autobackup_last,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(state.lastAtMillis))
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = onRunNow,
            enabled = state.folderUri != null && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.autobackup_now))
        }
    }

    if (showPassword) {
        NewPasswordDialog(
            title = stringResource(R.string.autobackup_password),
            hint = stringResource(R.string.autobackup_password_hint),
            confirmLabel = stringResource(R.string.export_password_save),
            allowEmpty = true,
            onConfirm = {
                showPassword = false
                onPasswordChange(it)
            },
            onCancel = { showPassword = false }
        )
    }
}
