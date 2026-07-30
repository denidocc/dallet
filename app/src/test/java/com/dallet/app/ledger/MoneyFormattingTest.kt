package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyFormattingTest {

    @Test
    fun formatsMinorUnitsAsManatsWithTwoDecimals() {
        assertThat(Money.formatManat(0)).isEqualTo("0.00")
        assertThat(Money.formatManat(1)).isEqualTo("0.01")
        assertThat(Money.formatManat(1050)).isEqualTo("10.50")
        assertThat(Money.formatManat(15_050)).isEqualTo("150.50")
    }

    @Test
    fun parsesManatInputToMinorUnits() {
        assertThat(Money.parseManat("0")).isEqualTo(0L)
        assertThat(Money.parseManat("10")).isEqualTo(1_000L)
        assertThat(Money.parseManat("10.5")).isEqualTo(1_050L)
        assertThat(Money.parseManat("10.50")).isEqualTo(1_050L)
        assertThat(Money.parseManat("11.70")).isEqualTo(1_170L)
    }
}
