package com.dallet.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.AppLanguage
import com.dallet.app.ledger.Currency
import com.dallet.app.ledger.ThemeMode
import com.dallet.app.ui.common.SurfaceCard

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currency: Currency,
    themeMode: ThemeMode,
    onCurrencyChange: (Currency) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    var pendingCurrency by remember { mutableStateOf<Currency?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SurfaceCard {
            Text(
                text = stringResource(R.string.settings_currency),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_currency_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Currency.values().forEach { option ->
                    FilterChip(
                        selected = option == currency,
                        onClick = { if (option != currency) pendingCurrency = option },
                        label = { Text(option.code) }
                    )
                }
            }
        }
        SurfaceCard {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                AppLanguage.values().forEach { option ->
                    FilterChip(
                        selected = option == language,
                        onClick = { if (option != language) onLanguageChange(option) },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        AppLanguage.RU -> R.string.lang_ru
                                        AppLanguage.EN -> R.string.lang_en
                                    }
                                )
                            )
                        }
                    )
                }
            }
        }
        SurfaceCard {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                ThemeMode.values().forEach { option ->
                    FilterChip(
                        selected = option == themeMode,
                        onClick = { onThemeModeChange(option) },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                                        ThemeMode.LIGHT -> R.string.settings_theme_light
                                        ThemeMode.DARK -> R.string.settings_theme_dark
                                    }
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    pendingCurrency?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingCurrency = null },
            title = { Text(stringResource(R.string.settings_currency_dialog_title)) },
            text = { Text(stringResource(R.string.settings_currency_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCurrencyChange(target)
                        pendingCurrency = null
                    }
                ) {
                    Text(stringResource(R.string.settings_currency_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCurrency = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}
