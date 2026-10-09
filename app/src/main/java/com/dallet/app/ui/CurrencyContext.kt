package com.dallet.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.dallet.app.ledger.Currency

val LocalCurrency = compositionLocalOf { Currency.DEFAULT }

@Composable
fun moneyText(label: String): String = "$label ${LocalCurrency.current.code}"
