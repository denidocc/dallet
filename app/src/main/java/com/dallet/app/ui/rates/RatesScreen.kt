package com.dallet.app.ui.rates

import com.dallet.app.ui.common.AppButtonShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.dallet.app.ui.common.SurfaceCard

@Composable
fun RatesScreen(
    main: Currency,
    rates: List<ExchangeRate>,
    onSave: (Map<Currency, String>, (Boolean) -> Unit) -> Unit
) {
    val inputs = remember(rates, main) {
        mutableStateMapOf<Currency, String>().apply {
            rates.forEach { put(it.currency, RateMath.format(it.scaled)) }
        }
    }
    var message by remember { mutableStateOf<Int?>(null) }
    val dates = rates.associate { it.currency to it.updatedOn }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.rates_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SurfaceCard {
            Text(
                text = stringResource(R.string.rates_calculator),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ConverterPanel(main = main, rates = rates, lockedTo = null, onApply = null)
        }
        SurfaceCard {
            Text(
                text = stringResource(R.string.rates_table),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.rates_table_hint, main.code),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.values().filter { it != main }.forEach { currency ->
                    OutlinedTextField(
                        value = inputs[currency].orEmpty(),
                        onValueChange = {
                            inputs[currency] = it
                            message = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("1 ${currency.code} =") },
                        suffix = { Text(main.code) },
                        supportingText = if (dates[currency] != null) {
                            { Text(stringResource(R.string.rates_updated, dates[currency].toString())) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
            message?.let { id ->
                Text(
                    text = stringResource(id),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (id == R.string.rates_saved) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Button(shape = AppButtonShape, 
                onClick = {
                    onSave(inputs.toMap()) { ok ->
                        message = if (ok) R.string.rates_saved else R.string.rates_save_error
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(stringResource(R.string.rates_save))
            }
        }
    }
}
