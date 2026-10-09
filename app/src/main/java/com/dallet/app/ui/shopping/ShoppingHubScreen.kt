package com.dallet.app.ui.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dallet.app.ui.common.SurfaceCard

@Composable
fun ShoppingHubScreen(
    state: ShoppingHubUiState,
    onTabSelected: (HubTab) -> Unit,
    onNewList: () -> Unit,
    onListClick: (Long) -> Unit,
    onProductClick: (Long) -> Unit,
    onDeleteProduct: (Long) -> Unit
) {
    var productToDelete by remember { mutableStateOf<ProductRowUi?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.shopping_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.tab == HubTab.LISTS,
                onClick = { onTabSelected(HubTab.LISTS) },
                label = { Text(stringResource(R.string.shopping_tab_lists)) }
            )
            FilterChip(
                selected = state.tab == HubTab.ARCHIVE,
                onClick = { onTabSelected(HubTab.ARCHIVE) },
                label = { Text(stringResource(R.string.shopping_tab_archive)) }
            )
            FilterChip(
                selected = state.tab == HubTab.PRODUCTS,
                onClick = { onTabSelected(HubTab.PRODUCTS) },
                label = { Text(stringResource(R.string.shopping_tab_products)) }
            )
        }

        when (state.tab) {
            HubTab.LISTS -> {
                SurfaceCard(onClick = onNewList) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Text(
                            text = stringResource(R.string.shopping_new_list),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                if (state.lists.isEmpty()) {
                    EmptyText(stringResource(R.string.shopping_lists_empty))
                }
                state.lists.forEach { row -> ListRow(row, onClick = { onListClick(row.id) }) }
            }
            HubTab.ARCHIVE -> {
                if (state.archived.isEmpty()) {
                    EmptyText(stringResource(R.string.shopping_archive_empty))
                }
                state.archived.forEach { row -> ListRow(row, onClick = { onListClick(row.id) }) }
            }
            HubTab.PRODUCTS -> {
                if (state.products.isEmpty()) {
                    EmptyText(stringResource(R.string.shopping_products_empty))
                }
                state.products.forEach { product ->
                    SurfaceCard(onClick = { onProductClick(product.id) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${product.name} · ${product.unitLabel}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = product.priceLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { productToDelete = product }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.shopping_delete)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    productToDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text(stringResource(R.string.shopping_delete_product_title)) },
            text = {
                Text("${product.name}: " + stringResource(R.string.shopping_delete_product_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProduct(product.id)
                        productToDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.shopping_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ListRow(row: ShoppingListRowUi, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(text = row.totalLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}
