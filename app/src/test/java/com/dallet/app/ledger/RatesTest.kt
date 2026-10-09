package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RatesTest {

    private fun ledger() = Ledger(
        accountRepository = InMemoryAccountRepository(),
        categoryRepository = InMemoryCategoryRepository(),
        operationRepository = InMemoryOperationRepository()
    )

    @Test
    fun parse_acceptsUpToFourDecimalsAndComma() {
        assertThat(RateMath.parse("19.5")).isEqualTo(195_000L)
        assertThat(RateMath.parse("0,0123")).isEqualTo(123L)
        assertThat(RateMath.parse("1.23456")).isNull()
        assertThat(RateMath.parse("0")).isNull()
        assertThat(RateMath.parse("-2")).isNull()
        assertThat(RateMath.parse("abc")).isNull()
    }

    @Test
    fun format_dropsTrailingZeros() {
        assertThat(RateMath.format(195_000L)).isEqualTo("19.5")
        assertThat(RateMath.format(123L)).isEqualTo("0.0123")
        assertThat(RateMath.format(20_000L)).isEqualTo("2")
    }

    @Test
    fun convert_toMainUsesRate() {
        val rates = mapOf(Currency.USD to 20_000L)

        val result = RateMath.convert(
            BigDecimal("10.5"), Currency.USD, Currency.TMT, Currency.TMT, rates
        )

        assertThat(RateMath.displayFour(result!!)).isEqualTo("21.0000")
    }

    @Test
    fun convert_crossRateGoesThroughMain() {
        val rates = mapOf(Currency.USD to 20_000L, Currency.EUR to 40_000L)

        val result = RateMath.convert(
            BigDecimal("10"), Currency.USD, Currency.EUR, Currency.TMT, rates
        )

        assertThat(RateMath.displayFour(result!!)).isEqualTo("5.0000")
    }

    @Test
    fun convert_missingRateIsNull() {
        val result = RateMath.convert(
            BigDecimal("10"), Currency.USD, Currency.TMT, Currency.TMT, emptyMap()
        )

        assertThat(result).isNull()
    }

    @Test
    fun displayTwo_roundsHalfUp() {
        assertThat(RateMath.displayTwo(BigDecimal("1.2350"))).isEqualTo("1.24")
        assertThat(RateMath.displayTwo(BigDecimal("1.2349"))).isEqualTo("1.23")
    }

    @Test
    fun ledger_savesRatesAndKeepsDateWhenUnchanged() = runTest {
        val ledger = ledger()
        val first = LocalDate.of(2026, 1, 1)
        val second = LocalDate.of(2026, 2, 1)

        ledger.saveRates(mapOf(Currency.USD to 20_000L, Currency.EUR to 30_000L), first)
        ledger.saveRates(mapOf(Currency.USD to 20_000L, Currency.EUR to 31_000L), second)

        val saved = ledger.rates().associateBy { it.currency }
        assertThat(saved.getValue(Currency.USD).updatedOn).isEqualTo(first)
        assertThat(saved.getValue(Currency.EUR).updatedOn).isEqualTo(second)
    }

    @Test
    fun ledger_rejectsRateForMainCurrency() = runTest {
        val ledger = ledger()

        val failure = runCatching {
            ledger.saveRates(mapOf(Currency.TMT to 10_000L), LocalDate.of(2026, 1, 1))
        }

        assertThat(failure.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun changingMainCurrencyClearsRates() = runTest {
        val ledger = ledger()
        ledger.saveRates(mapOf(Currency.USD to 20_000L), LocalDate.of(2026, 1, 1))

        ledger.setMainCurrency(Currency.EUR)

        assertThat(ledger.rates()).isEmpty()
    }

    @Test
    fun resetClearsRates() = runTest {
        val ledger = ledger()
        ledger.saveRates(mapOf(Currency.USD to 20_000L), LocalDate.of(2026, 1, 1))

        ledger.resetAll()

        assertThat(ledger.rates()).isEmpty()
    }
}
