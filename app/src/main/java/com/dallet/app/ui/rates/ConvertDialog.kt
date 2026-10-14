package com.dallet.app.ui.rates

import com.dallet.app.ui.common.AppButtonShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.Currency
import com.dallet.app.ledger.ExchangeRate
import com.dallet.app.ledger.RateMath

/**
 * Calculator: amount in [from] converted to [to]. When [lockedTo] is set the target
 * currency is fixed; [onApply] receives the result rounded to two decimals.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConverterPanel(
    main: Currency,
    rates: List<ExchangeRate>,
    lockedTo: Currency?,
    onApply: ((String) -> Unit)?
) {
    val rateMap = rates.associate { it.currency to it.scaled }
    val firstOther = Currency.values().firstOrNull { it != main && rateMap.containsKey(it) }
        ?: Currency.values().first { it != main }
    var amountText by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(firstOther) }
    var chosenTo by remember { mutableStateOf(main) }
    val to = lockedTo ?: chosenTo

    val amount = RateMath.parseAmount(amountText)
    val result = amount?.let { RateMath.convert(it, from, to, main, rateMap) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.rates_amount)) },
            suffix = { Text(from.code) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Text(
            text = stringResource(R.string.rates_from),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Currency.values().forEach { option ->
                FilterChip(
                    selected = option == from,
                    onClick = { from = option },
                    label = { Text(option.code) }
                )
            }
        }
        if (lockedTo == null) {
            Text(
                text = stringResource(R.string.rates_to),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.values().forEach { option ->
                    FilterChip(
                        selected = option == chosenTo,
                        onClick = { chosenTo = option },
                        label = { Text(option.code) }
                    )
                }
            }
        }
        when {
            amountText.isBlank() -> Unit
            amount == null -> Text(
                text = stringResource(R.string.rates_bad_amount),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            result == null -> Text(
                text = stringResource(R.string.rates_missing),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            else -> Text(
                text = "= ${RateMath.displayFour(result)} ${to.code}",
                style = MaterialTheme.typography.titleLarge
            )
        }
        if (onApply != null) {
            Button(shape = AppButtonShape, 
                onClick = { result?.let { onApply(RateMath.displayTwo(it)) } },
                enabled = result != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.rates_apply))
            }
        }
    }
}

@Composable
fun ConvertDialog(
    main: Currency,
    rates: List<ExchangeRate>,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rates_convert_title)) },
        text = {
            ConverterPanel(
                main = main,
                rates = rates,
                lockedTo = main,
                onApply = onApply
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
