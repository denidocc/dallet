package com.dallet.app.ui.more

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import java.text.Collator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.DataUiState
import com.dallet.app.ui.common.SurfaceCard

private const val SOURCE_URL = "https://github.com/denidocc/dallet"
private const val PRIVACY_URL = "https://github.com/denidocc/dallet/blob/main/PRIVACY.md"

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // Нет браузера: ссылка просто не откроется.
    }
}

@Composable
fun MoreScreen(
    dataState: DataUiState,
    onAccountsClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onBudgetsClick: () -> Unit,
    onRecurringClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRatesClick: () -> Unit,
    onShoppingClick: () -> Unit,
    onGoalsClick: () -> Unit,
    onDebtsClick: () -> Unit,
    onFaqClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onAutoBackupClick: () -> Unit,
    onResetConfirmed: () -> Unit,
    onDataMessageShown: () -> Unit
) {
    var showImportDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

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
        val collator = remember { Collator.getInstance() }
        fun sorted(items: List<MoreEntry>) = items.sortedWith { x, y -> collator.compare(x.title, y.title) }

        MoreGroup(
            title = stringResource(R.string.more_group_money),
            items = sorted(
                listOf(
                    MoreEntry(stringResource(R.string.budgets_title), onClick = onBudgetsClick),
                    MoreEntry(stringResource(R.string.debts_title), onClick = onDebtsClick),
                    MoreEntry(stringResource(R.string.recurring_title), onClick = onRecurringClick),
                    MoreEntry(stringResource(R.string.more_shopping), onClick = onShoppingClick),
                    MoreEntry(stringResource(R.string.goals_title), onClick = onGoalsClick)
                )
            )
        )
        MoreGroup(
            title = stringResource(R.string.more_group_lists),
            items = sorted(
                listOf(
                    MoreEntry(stringResource(R.string.more_categories), onClick = onCategoriesClick),
                    MoreEntry(stringResource(R.string.more_accounts), onClick = onAccountsClick),
                    MoreEntry(stringResource(R.string.more_rates), onClick = onRatesClick)
                )
            )
        )

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
        MoreCards(
            sorted(
                listOf(
                    MoreEntry(
                        stringResource(R.string.autobackup_title),
                        hint = stringResource(R.string.autobackup_card_hint),
                        onClick = onAutoBackupClick
                    ),
                    MoreEntry(
                        stringResource(R.string.more_export),
                        hint = stringResource(R.string.more_export_hint),
                        onClick = if (dataState.busy) null else onExportClick
                    ),
                    MoreEntry(
                        stringResource(R.string.more_import),
                        hint = stringResource(R.string.more_import_hint),
                        onClick = if (dataState.busy) null else ({ showImportDialog = true })
                    )
                )
            ) + MoreEntry(
                stringResource(R.string.more_reset),
                hint = stringResource(R.string.more_reset_hint),
                danger = true,
                onClick = if (dataState.busy) null else ({ showResetDialog = true })
            )
        )

        MoreGroup(
            title = stringResource(R.string.more_group_app),
            items = sorted(
                listOf(
                    MoreEntry(stringResource(R.string.more_security), onClick = onSecurityClick),
                    MoreEntry(stringResource(R.string.more_settings), onClick = onSettingsClick)
                )
            )
        )
        MoreGroup(
            title = stringResource(R.string.more_group_help),
            items = sorted(
                listOf(
                    MoreEntry(stringResource(R.string.faq_title), onClick = onFaqClick),
                    MoreEntry(
                        stringResource(R.string.feedback_title),
                        hint = stringResource(R.string.feedback_card_hint),
                        onClick = onFeedbackClick
                    )
                )
            )
        )

        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        SurfaceCard {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.about_version, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SurfaceCard(onClick = { openLink(context, SOURCE_URL) }) {
            Text(
                text = stringResource(R.string.about_source),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.about_license),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SurfaceCard(onClick = { openLink(context, PRIVACY_URL) }) {
            Text(
                text = stringResource(R.string.about_privacy),
                style = MaterialTheme.typography.titleMedium
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

private data class MoreEntry(
    val title: String,
    val hint: String? = null,
    val danger: Boolean = false,
    val onClick: (() -> Unit)?
)

@Composable
private fun MoreGroup(title: String, items: List<MoreEntry>) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp)
    )
    MoreCards(items)
}

@Composable
private fun MoreCards(items: List<MoreEntry>) {
    items.forEach { item ->
        SurfaceCard(onClick = item.onClick) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (item.danger) MaterialTheme.colorScheme.error else Color.Unspecified
            )
            item.hint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
