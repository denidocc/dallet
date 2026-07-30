package com.dallet.app.ledger

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    fun formatManat(minor: Long): String {
        val value = BigDecimal.valueOf(minor, 2)
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString()
    }

    fun parseManat(input: String): Long {
        val normalized = input.trim().replace(',', '.')
        require(normalized.isNotEmpty()) { "empty amount" }
        val value = BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP)
        return value.movePointRight(2).longValueExact()
    }
}
