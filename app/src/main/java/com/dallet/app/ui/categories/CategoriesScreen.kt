package com.dallet.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
        Button(
            onClick = onAddClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(stringResource(R.string.categories_add))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.rows, key = { it.id }) { row ->
                SurfaceCard(onClick = { onCategoryClick(row.id) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(row.colorArgb))
                        )
                        Column {
                            Text(text = row.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = row.iconKey,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryEditorScreen(
    state: CategoryEditorUiState,
    onNameChange: (String) -> Unit,
    onColorSelected: (Int) -> Unit,
    onIconSelected: (String) -> Unit,
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
        Text(text = stringResource(R.string.categories_color_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryStyleOptions.icons.take(5).forEach { icon ->
                FilterChip(
                    selected = state.iconKey == icon,
                    onClick = { onIconSelected(icon) },
                    label = { Text(icon) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryStyleOptions.icons.drop(5).forEach { icon ->
                FilterChip(
                    selected = state.iconKey == icon,
                    onClick = { onIconSelected(icon) },
                    label = { Text(icon) }
                )
            }
        }
        state.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = onSave,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.edit_save))
        }
        if (!state.isNew) {
            Button(
                onClick = onDelete,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.edit_delete))
            }
        }
    }
}
