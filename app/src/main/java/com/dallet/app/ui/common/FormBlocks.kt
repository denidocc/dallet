package com.dallet.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.ExchangeRate
import com.dallet.app.ui.LocalCurrency
import com.dallet.app.ui.rates.ConvertDialog

/** A card that groups related form fields under a small caption. */
@Composable
fun FormSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    SurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

/** Equal-width choices in one rounded track; the selected one is filled. */
@Composable
fun SegmentedChoice(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelected(index) }
                    .padding(vertical = 10.dp)
            )
        }
    }
}

/** The amount as a large number on its own card, with the optional currency converter. */
@Composable
fun AmountCard(
    value: String,
    onChange: (String) -> Unit,
    rates: List<ExchangeRate>,
    enabled: Boolean = true,
    supporting: String? = null,
    canConvert: Boolean = true
) {
    var showConvert by remember { mutableStateOf(false) }
    SurfaceCard {
        Text(
            text = stringResource(R.string.add_amount_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium,
            suffix = {
                Text(
                    text = LocalCurrency.current.code,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                disabledTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (canConvert && enabled) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showConvert = true }) {
                    Text(stringResource(R.string.rates_convert))
                }
            }
        }
    }
    if (showConvert) {
        ConvertDialog(
            main = LocalCurrency.current,
            rates = rates,
            onApply = {
                onChange(it)
                showConvert = false
            },
            onDismiss = { showConvert = false }
        )
    }
}
