package com.dallet.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.common.SurfaceCard

@Composable
fun MoreScreen(
    onAccountsClick: () -> Unit,
    onCategoriesClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
    }
}
