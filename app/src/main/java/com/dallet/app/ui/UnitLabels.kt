package com.dallet.app.ui

import androidx.annotation.StringRes
import com.dallet.app.R
import com.dallet.app.ledger.QuantityUnit

@StringRes
fun QuantityUnit.labelRes(): Int = when (this) {
    QuantityUnit.PIECE -> R.string.unit_piece
    QuantityUnit.KILOGRAM -> R.string.unit_kilogram
    QuantityUnit.GRAM -> R.string.unit_gram
    QuantityUnit.LITER -> R.string.unit_liter
    QuantityUnit.MILLILITER -> R.string.unit_milliliter
    QuantityUnit.PACK -> R.string.unit_pack
}

@StringRes
fun templateNameRes(name: String): Int? = when (name) {
    "Наличные" -> R.string.onboarding_cash_label
    "ЗП карта" -> R.string.onboarding_card_label
    "Накопления" -> R.string.tpl_savings
    "Кредитная карта" -> R.string.tpl_credit
    "Долги" -> R.string.tpl_debts
    else -> null
}
