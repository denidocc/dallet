package com.dallet.app.ui.budgets

import com.dallet.app.ui.common.AppButtonShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.BudgetRowUi
import com.dallet.app.ui.BudgetState
import com.dallet.app.ui.BudgetsUiState
import com.dallet.app.ui.LocalCurrency
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.moneyText

private val NearColor = Color(0xFFF9A825)

@Composable
private fun budgetColor(state: BudgetState): Color = when (state) {
    BudgetState.OK -> MaterialTheme.colorScheme.primary
    BudgetState.NEAR -> NearColor
    BudgetState.OVER -> MaterialTheme.colorScheme.error
}

@Composable
fun BudgetRowView(row: BudgetRowUi, onClick: (() -> Unit)?) {
    SurfaceCard(onClick = onClick) {
        Text(text = row.name, style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(row.fraction.coerceIn(0.02f, 1f))
                    .background(budgetColor(row.state), RoundedCornerShape(3.dp))
            )
        }
        Text(
            text = stringResource(
                R.string.budget_spent_of,
                moneyText(row.spentLabel),
                moneyText(row.limitLabel)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = if (row.state == BudgetState.OVER) {
                stringResource(R.string.budget_over_by, moneyText(row.remainingLabel))
            } else {
                stringResource(R.string.budget_left, moneyText(row.remainingLabel))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = budgetColor(row.state)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    state: BudgetsUiState,
    onSave: (Long, String, (Boolean) -> Unit) -> Unit,
    onRemove: (Long) -> Unit
) {
    var editing by remember { mutableStateOf<BudgetRowUi?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.budgets_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.budgets_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.budgets_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        state.rows.forEach { row ->
            BudgetRowView(row = row, onClick = { editing = row })
        }
        Button(shape = AppButtonShape, 
            onClick = { adding = true },
            enabled = state.available.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.budgets_add))
        }
    }

    editing?.let { row ->
        var input by remember(row) { mutableStateOf(row.limitInput) }
        var failed by remember(row) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(row.name) },
            text = {
                LimitField(input, failed) {
                    input = it
                    failed = false
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSave(row.categoryId, input) { ok ->
                            if (ok) editing = null else failed = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.shopping_save))
                }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = {
                            onRemove(row.categoryId)
                            editing = null
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.budgets_remove),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(onClick = { editing = null }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            }
        )
    }

    if (adding) {
        var selected by remember { mutableStateOf(state.available.firstOrNull()?.id) }
        var input by remember { mutableStateOf("") }
        var failed by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.budgets_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.available.forEach { option ->
                            FilterChip(
                                selected = option.id == selected,
                                onClick = { selected = option.id },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    LimitField(input, failed) {
                        input = it
                        failed = false
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = selected
                        if (id == null) {
                            failed = true
                        } else {
                            onSave(id, input) { ok ->
                                if (ok) adding = false else failed = true
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.shopping_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun LimitField(value: String, failed: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.budgets_limit)) },
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
