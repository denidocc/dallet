package com.dallet.app.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.Money
import com.dallet.app.ledger.QuantityUnit
import com.dallet.app.ui.SelectableOption
import com.dallet.app.ui.common.FormDateField
import com.dallet.app.ui.common.FormTimeField
import com.dallet.app.ui.common.SurfaceCard

class ShoppingListActions(
    val onSaveInfo: (String, String) -> Unit,
    val onNameChange: (String) -> Unit,
    val onQuantityChange: (String) -> Unit,
    val onUnitSelected: (QuantityUnit) -> Unit,
    val onPriceChange: (String) -> Unit,
    val onSuggestionSelected: (SuggestionUi) -> Unit,
    val onAddItem: () -> Unit,
    val onToggle: (Long, Boolean) -> Unit,
    val onUpdateItem: (Long, String, String, QuantityUnit, String) -> Unit,
    val onDeleteItem: (Long) -> Unit,
    val onSavePrices: () -> Unit,
    val onPrepareClose: () -> Unit,
    val onCloseAccount: (Long) -> Unit,
    val onCloseCategory: (Long) -> Unit,
    val onCloseDate: (String) -> Unit,
    val onCloseTime: (String) -> Unit,
    val onCloseCarryOver: (Boolean) -> Unit,
    val onConfirmClose: () -> Unit,
    val onDeleteList: () -> Unit,
    val onGoHome: () -> Unit,
    val onGoShopping: () -> Unit,
    val onOpenExpense: (Long) -> Unit
)

@Composable
fun ShoppingListScreen(
    state: ShoppingEditorUiState,
    closeForm: CloseFormUiState,
    actions: ShoppingListActions
) {
    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.missing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.shopping_list_missing))
        }
        return
    }

    var showDelete by remember { mutableStateOf(false) }
    var showClose by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShoppingItemUi?>(null) }
    var titleInput by remember(state.listId) { mutableStateOf(state.title) }
    var noteInput by remember(state.listId) { mutableStateOf(state.note) }

    val saveInfo by rememberUpdatedState {
        if (!state.readOnly && (titleInput != state.title || noteInput != state.note)) {
            actions.onSaveInfo(titleInput, noteInput)
        }
    }
    DisposableEffect(state.listId) {
        onDispose { saveInfo() }
    }

    LaunchedEffect(state.readOnly) {
        if (state.readOnly) showClose = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.readOnly) {
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (state.note.isNotBlank()) {
                                Text(
                                    text = state.note,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = titleInput,
                                onValueChange = { titleInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (!it.isFocused) saveInfo() },
                                label = { Text(stringResource(R.string.shopping_list_name)) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = noteInput,
                                onValueChange = { noteInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (!it.isFocused) saveInfo() },
                                label = { Text(stringResource(R.string.shopping_list_note)) },
                                minLines = 1,
                                maxLines = 3
                            )
                        }
                        state.closedLabel?.let { closed ->
                            val expense = stringResource(
                                if (state.hasExpense) R.string.shopping_expense_created
                                else R.string.shopping_no_expense
                            )
                            Text(
                                text = stringResource(R.string.shopping_closed_info, closed) +
                                    " · " + expense,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.shopping_delete)
                        )
                    }
                }
            }

            if (!state.readOnly) {
                item {
                    AddItemForm(state = state, actions = actions)
                }
            }

            state.message?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.items.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.shopping_items_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(state.items, key = { it.id }) { item ->
                SurfaceCard(onClick = if (state.readOnly) null else ({ editing = item })) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = item.checked,
                            onCheckedChange = { actions.onToggle(item.id, it) },
                            enabled = !state.readOnly
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.quantityLabel + " × " + item.priceLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = item.lineTotalLabel, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        BottomPanel(
            state = state,
            onSavePrices = actions.onSavePrices,
            onClose = {
                actions.onPrepareClose()
                showClose = true
            },
            onGoHome = actions.onGoHome,
            onGoShopping = actions.onGoShopping,
            onOpenExpense = actions.onOpenExpense
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.shopping_delete_list_title)) },
            text = { Text(stringResource(R.string.shopping_delete_list_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        actions.onDeleteList()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.shopping_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    editing?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { name, quantity, unit, price ->
                actions.onUpdateItem(item.id, name, quantity, unit, price)
                editing = null
            },
            onDelete = {
                actions.onDeleteItem(item.id)
                editing = null
            }
        )
    }

    if (showClose) {
        CloseDialog(
            state = state,
            form = closeForm,
            actions = actions,
            onDismiss = { showClose = false }
        )
    }
}

@Composable
private fun AddItemForm(
    state: ShoppingEditorUiState,
    actions: ShoppingListActions
) {
    SurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.nameInput,
                onValueChange = actions.onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.shopping_add_name)) },
                singleLine = true
            )
            if (state.suggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    state.suggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { actions.onSuggestionSelected(suggestion) }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = suggestion.name + " · " + suggestion.unit.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            suggestion.lastPriceMinor?.let { price ->
                                Text(
                                    text = Money.formatManat(price),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.quantityInput,
                    onValueChange = actions.onQuantityChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.shopping_qty)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                UnitPicker(unit = state.unit, onSelected = actions.onUnitSelected)
                OutlinedTextField(
                    value = state.priceInput,
                    onValueChange = actions.onPriceChange,
                    modifier = Modifier.weight(1.3f),
                    label = { Text(stringResource(R.string.shopping_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            Button(
                onClick = actions.onAddItem,
                enabled = state.nameInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.shopping_add))
            }
        }
    }
}

@Composable
private fun UnitPicker(
    unit: QuantityUnit,
    onSelected: (QuantityUnit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(56.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text(unit.label)
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QuantityUnit.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomPanel(
    state: ShoppingEditorUiState,
    onSavePrices: () -> Unit,
    onClose: () -> Unit,
    onGoHome: () -> Unit,
    onGoShopping: () -> Unit,
    onOpenExpense: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.shopping_total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.amount_with_currency, state.totalLabel),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.shopping_checked_total, state.checkedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.amount_with_currency, state.checkedTotalLabel),
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (!state.readOnly) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onSavePrices, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shopping_save_prices))
                }
                Button(
                    onClick = onClose,
                    enabled = state.checkedCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.shopping_close))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shopping_go_home))
                }
                OutlinedButton(onClick = onGoShopping, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shopping_go_shopping))
                }
                state.operationId?.let { operationId ->
                    TextButton(
                        onClick = { onOpenExpense(operationId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.shopping_open_expense))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditItemDialog(
    item: ShoppingItemUi,
    onDismiss: () -> Unit,
    onSave: (String, String, QuantityUnit, String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var quantity by remember(item.id) { mutableStateOf(item.quantityInput) }
    var unit by remember(item.id) { mutableStateOf(item.unit) }
    var price by remember(item.id) { mutableStateOf(item.priceInput) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_edit_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.shopping_add_name)) },
                    singleLine = true
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.shopping_qty)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    UnitPicker(unit = unit, onSelected = { unit = it })
                }
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(stringResource(R.string.shopping_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, quantity, unit, price) }) {
                Text(stringResource(R.string.shopping_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.shopping_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        }
    )
}

@Composable
private fun CloseDialog(
    state: ShoppingEditorUiState,
    form: CloseFormUiState,
    actions: ShoppingListActions,
    onDismiss: () -> Unit
) {
    val uncheckedCount = state.items.size - state.checkedCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_close_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.shopping_close_summary,
                        state.checkedCount,
                        state.checkedTotalLabel
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                OptionPicker(
                    label = stringResource(R.string.shopping_account),
                    options = form.accounts,
                    selectedId = form.selectedAccountId,
                    onSelected = actions.onCloseAccount
                )
                OptionPicker(
                    label = stringResource(R.string.shopping_category),
                    options = form.categories,
                    selectedId = form.selectedCategoryId,
                    onSelected = actions.onCloseCategory
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormDateField(
                        dateInput = form.dateInput,
                        onDateSelected = actions.onCloseDate,
                        modifier = Modifier.weight(1.4f)
                    )
                    FormTimeField(
                        timeInput = form.timeInput,
                        onTimeSelected = actions.onCloseTime,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (uncheckedCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.shopping_carry_over),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = form.carryOver,
                            onCheckedChange = actions.onCloseCarryOver
                        )
                    }
                }
                form.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onConfirmClose, enabled = !form.submitting) {
                Text(stringResource(R.string.shopping_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun OptionPicker(
    label: String,
    options: List<SelectableOption>,
    selectedId: Long?,
    onSelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId }?.label ?: "—"
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "$label: $selected", maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
