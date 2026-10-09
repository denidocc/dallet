package com.dallet.app.ledger

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Rate of one unit of [currency] in the main currency, scaled by [RateMath.SCALE]. */
data class ExchangeRate(
    val currency: Currency,
    val scaled: Long,
    val updatedOn: LocalDate
)

object RateMath {
    const val SCALE = 10_000L
    private const val DIGITS = 4

    fun parse(text: String): Long? {
        val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > DIGITS) return null
        val scaled = value.multiply(BigDecimal(SCALE))
        return if (scaled.compareTo(BigDecimal(Long.MAX_VALUE / 2)) > 0) null else scaled.toLong()
    }

    fun format(scaled: Long): String =
        BigDecimal(scaled).divide(BigDecimal(SCALE)).stripTrailingZeros().toPlainString()

    fun parseAmount(text: String): BigDecimal? {
        val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
        return if (value.signum() < 0 || value.stripTrailingZeros().scale() > DIGITS) null else value
    }

    /** Result with up to four decimals, or null when a needed rate is missing. */
    fun convert(
        amount: BigDecimal,
        from: Currency,
        to: Currency,
        main: Currency,
        rates: Map<Currency, Long>
    ): BigDecimal? {
        if (from == to) return amount.setScale(DIGITS, RoundingMode.HALF_UP)
        val fromRate = rateOf(from, main, rates) ?: return null
        val toRate = rateOf(to, main, rates) ?: return null
        return amount.multiply(fromRate).divide(toRate, DIGITS, RoundingMode.HALF_UP)
    }

    fun displayFour(value: BigDecimal): String = value.setScale(DIGITS, RoundingMode.HALF_UP).toPlainString()

    fun displayTwo(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun rateOf(currency: Currency, main: Currency, rates: Map<Currency, Long>): BigDecimal? =
        if (currency == main) {
            BigDecimal.ONE
        } else {
            rates[currency]?.let { BigDecimal(it).divide(BigDecimal(SCALE)) }
        }
}
