package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class ComparisonPresetsTest {

    private val september = OperationFilter(
        period = HistoryPeriod.CUSTOM,
        customFrom = LocalDate.of(2026, 9, 1),
        customTo = LocalDate.of(2026, 9, 30),
        accountIds = setOf(1L),
        categoryIds = setOf(10L, 11L)
    )

    private val october = OperationFilter(
        period = HistoryPeriod.CUSTOM,
        customFrom = LocalDate.of(2026, 10, 1),
        customTo = LocalDate.of(2026, 10, 31),
        accountIds = setOf(2L),
        categoryIds = setOf(12L)
    )

    @Test
    fun periodVsPeriod_giveBTheCategoriesAndAccountsOfA_keepingItsOwnDates() {
        val (a, b) = periodVsPeriod(september, october)

        assertThat(a).isEqualTo(september)
        assertThat(b.categoryIds).containsExactly(10L, 11L)
        assertThat(b.accountIds).containsExactly(1L)
        assertThat(b.period).isEqualTo(HistoryPeriod.CUSTOM)
        assertThat(b.customFrom).isEqualTo(LocalDate.of(2026, 10, 1))
        assertThat(b.customTo).isEqualTo(LocalDate.of(2026, 10, 31))
    }

    @Test
    fun categoryVsCategory_giveBThePeriodAndAccountsOfA_keepingItsOwnCategories() {
        val (a, b) = categoryVsCategory(september, october)

        assertThat(a).isEqualTo(september)
        assertThat(b.categoryIds).containsExactly(12L)
        assertThat(b.accountIds).containsExactly(1L)
        assertThat(b.period).isEqualTo(HistoryPeriod.CUSTOM)
        assertThat(b.customFrom).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(b.customTo).isEqualTo(LocalDate.of(2026, 9, 30))
    }

    @Test
    fun categoryVsCategory_withPresetPeriod_copiesThePreset() {
        val a = OperationFilter(period = HistoryPeriod.THIS_MONTH, categoryIds = setOf(1L))
        val b = OperationFilter(categoryIds = setOf(2L))

        val result = categoryVsCategory(a, b)

        assertThat(result.second.period).isEqualTo(HistoryPeriod.THIS_MONTH)
        assertThat(result.second.categoryIds).containsExactly(2L)
    }

    @Test
    fun bothPresets_dropTheTextAndKindOfBothSides() {
        val noisyA = september.copy(query = "tea", kind = OperationKind.EXPENSE)
        val noisyB = october.copy(query = "bread", kind = OperationKind.INCOME)

        val periods = periodVsPeriod(noisyA, noisyB)
        val categories = categoryVsCategory(noisyA, noisyB)

        assertThat(periods.first.query).isEmpty()
        assertThat(periods.second.query).isEmpty()
        assertThat(categories.first.query).isEmpty()
        assertThat(categories.second.query).isEmpty()
        assertThat(periods.second.kind).isEqualTo(OperationKind.EXPENSE)
        assertThat(categories.second.kind).isEqualTo(OperationKind.EXPENSE)
    }
}
