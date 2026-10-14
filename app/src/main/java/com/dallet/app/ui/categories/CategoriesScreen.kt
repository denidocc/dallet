package com.dallet.app.ui.categories

import com.dallet.app.ui.common.ConfirmDeleteDialog
import com.dallet.app.ui.common.AppButtonShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dallet.app.ui.SelectableOption
import com.dallet.app.ui.common.OutlineAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.CategoryKind
import com.dallet.app.ui.CategoriesUiState
import com.dallet.app.ui.CategoryEditorUiState
import com.dallet.app.ui.common.SurfaceCard

private const val ICON_COLUMNS = 4

@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onKindSelected: (CategoryKind) -> Unit,
    onAddClick: () -> Unit,
    onCategoryClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.categories_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.selectedKind == CategoryKind.EXPENSE,
                onClick = { onKindSelected(CategoryKind.EXPENSE) },
                label = { Text(stringResource(R.string.add_kind_expense)) }
            )
            FilterChip(
                selected = state.selectedKind == CategoryKind.INCOME,
                onClick = { onKindSelected(CategoryKind.INCOME) },
                label = { Text(stringResource(R.string.add_kind_income)) }
            )
        }
        Button(shape = AppButtonShape,
            onClick = onAddClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(stringResource(R.string.categories_add))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.rows, key = { it.id }) { row ->
                SurfaceCard(
                    onClick = { onCategoryClick(row.id) },
                    modifier = Modifier.padding(start = if (row.isChild) 28.dp else 0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(row.colorArgb)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = categoryIcon(row.iconKey),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(text = row.name, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorScreen(
    state: CategoryEditorUiState,
    onNameChange: (String) -> Unit,
    onColorSelected: (Int) -> Unit,
    onIconSelected: (String) -> Unit,
    onParentSelected: (Long?) -> Unit,
    onAddSubcategory: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (state.isNew) {
                stringResource(R.string.categories_new_title)
            } else {
                stringResource(R.string.categories_edit_title)
            },
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = when (state.kind) {
                CategoryKind.EXPENSE -> stringResource(R.string.add_kind_expense)
                CategoryKind.INCOME -> stringResource(R.string.add_kind_income)
            },
            style = MaterialTheme.typography.labelMedium
        )
        OutlinedTextField(
            value = state.nameInput,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.categories_name_label)) },
            singleLine = true
        )
        if (!state.hasChildren) {
            ParentChoice(
                options = state.parentOptions,
                selectedId = state.parentId,
                onSelected = onParentSelected
            )
        } else {
            Text(
                text = stringResource(R.string.categories_has_children_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(text = stringResource(R.string.categories_color_label))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryStyleOptions.colors.forEach { color ->
                val selected = state.colorArgb == color
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onColorSelected(color) }
                )
            }
        }
        Text(text = stringResource(R.string.categories_icon_label))
        CategoryIconGrid(
            selectedKey = state.iconKey,
            onIconSelected = onIconSelected
        )
        state.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        Button(shape = AppButtonShape,
            onClick = onSave,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.edit_save))
        }
        if (!state.isNew && state.parentId == null) {
            OutlineAction(
                text = stringResource(R.string.categories_add_sub),
                onClick = onAddSubcategory
            )
        }
        if (!state.isNew) {
            var confirmDelete by remember { mutableStateOf(false) }
            OutlineAction(
                text = stringResource(R.string.edit_delete),
                onClick = { confirmDelete = true },
                enabled = !state.submitting,
                danger = true
            )
            if (confirmDelete) {
                ConfirmDeleteDialog(
                    title = stringResource(R.string.delete_category_title),
                    text = stringResource(R.string.delete_category_text),
                    onConfirm = {
                        confirmDelete = false
                        onDelete()
                    },
                    onDismiss = { confirmDelete = false }
                )
            }
        }
    }
}

@Composable
private fun CategoryIconGrid(
    selectedKey: String,
    onIconSelected: (String) -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    CategoryStyleOptions.icons.chunked(ICON_COLUMNS).forEach { rowKeys ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowKeys.forEach { key ->
                val selected = selectedKey == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(shape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            shape = shape
                        )
                        .clickable { onIconSelected(key) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(key),
                        contentDescription = key,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            repeat(ICON_COLUMNS - rowKeys.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentChoice(
    options: List<SelectableOption>,
    selectedId: Long?,
    onSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(R.string.categories_parent_none)
    val selectedLabel = options.firstOrNull { it.id == selectedId }?.label ?: none
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.categories_parent_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(none) },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
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
