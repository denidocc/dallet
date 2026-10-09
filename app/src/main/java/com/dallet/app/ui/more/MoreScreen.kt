package com.dallet.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.DataUiState
import com.dallet.app.ui.common.SurfaceCard

@Composable
fun MoreScreen(
    dataState: DataUiState,
    onAccountsClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onShoppingClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onResetConfirmed: () -> Unit,
    onDataMessageShown: () -> Unit
) {
    var showImportDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { onDataMessageShown() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.more_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SurfaceCard(onClick = onAccountsClick) {
            Text(
                text = stringResource(R.string.more_accounts),
                style = MaterialTheme.typography.titleMedium
            )
        }
        SurfaceCard(onClick = onCategoriesClick) {
            Text(
                text = stringResource(R.string.more_categories),
                style = MaterialTheme.typography.titleMedium
            )
        }

        SurfaceCard(onClick = onShoppingClick) {
            Text(
                text = stringResource(R.string.more_shopping),
                style = MaterialTheme.typography.titleMedium
            )
        }
        SurfaceCard(onClick = onSecurityClick) {
            Text(
                text = stringResource(R.string.more_security),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            text = stringResource(R.string.more_data_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        dataState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dataState.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
        SurfaceCard(onClick = if (dataState.busy) null else onExportClick) {
            Text(
                text = stringResource(R.string.more_export),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.more_export_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SurfaceCard(onClick = if (dataState.busy) null else ({ showImportDialog = true })) {
            Text(
                text = stringResource(R.string.more_import),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.more_import_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SurfaceCard(onClick = if (dataState.busy) null else ({ showResetDialog = true })) {
            Text(
                text = stringResource(R.string.more_reset),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.more_reset_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.import_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.import_dialog_text))
                    TextButton(
                        onClick = {
                            showImportDialog = false
                            onExportClick()
                        }
                    ) {
                        Text(stringResource(R.string.dialog_export_first))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        onImportClick()
                    }
                ) {
                    Text(stringResource(R.string.import_choose_file))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showResetDialog) {
        val resetWord = stringResource(R.string.reset_word)
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.reset_dialog_text))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true
                    )
                    TextButton(
                        onClick = {
                            showResetDialog = false
                            onExportClick()
                        }
                    ) {
                        Text(stringResource(R.string.dialog_export_first))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetConfirmed()
                    },
                    enabled = typed.trim() == resetWord
                ) {
                    Text(
                        text = stringResource(R.string.reset_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}
