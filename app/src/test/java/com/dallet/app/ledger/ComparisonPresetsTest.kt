package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class ComparisonPresetsTest {

    private val today = LocalDate.of(2026, 10, 12)

    private val october = OperationFilter(
        period = HistoryPeriod.CUSTOM,
        customFrom = LocalDate.of(2026, 10, 1),
        customTo = LocalDate.of(2026, 10, 31),
        accountIds = setOf(1L),
        categoryIds = setOf(10L, 11L),
        kind = OperationKind.EXPENSE
    )

    // --- period against period ------------------------------------------------------------

    @Test
    fun periodVsPeriod_bIsACopyOfA_shiftedToThePreviousWindowOfTheSameLength() {
        val (a, b) = periodVsPeriod(october, today)

        assertThat(a).isEqualTo(october)
        assertThat(b.categoryIds).containsExactly(10L, 11L)
        assertThat(b.accountIds).containsExactly(1L)
        assertThat(b.kind).isEqualTo(OperationKind.EXPENSE)
        assertThat(b.period).isEqualTo(HistoryPeriod.CUSTOM)
        // October has 31 days, so the window before it is 31 days ending on September 30.
        assertThat(b.customFrom).isEqualTo(LocalDate.of(2026, 8, 31))
        assertThat(b.customTo).isEqualTo(LocalDate.of(2026, 9, 30))
    }

    @Test
    fun periodVsPeriod_worksForAPresetPeriod() {
        val thisMonth = OperationFilter(period = HistoryPeriod.THIS_MONTH, accountIds = setOf(2L))

        val (a, b) = periodVsPeriod(thisMonth, today)

        assertThat(a.period).isEqualTo(HistoryPeriod.THIS_MONTH)
        assertThat(b.period).isEqualTo(HistoryPeriod.CUSTOM)
        assertThat(b.customFrom).isEqualTo(LocalDate.of(2026, 8, 31))
        assertThat(b.customTo).isEqualTo(LocalDate.of(2026, 9, 30))
        assertThat(b.accountIds).containsExactly(2L)
    }

    @Test
    fun periodVsPeriod_withNoDatesOnA_leavesTheDatesAlone() {
        val allTime = OperationFilter(accountIds = setOf(3L))

        val (a, b) = periodVsPeriod(allTime, today)

        assertThat(b.period).isEqualTo(HistoryPeriod.ALL_TIME)
        assertThat(b.accountIds).containsExactly(3L)
        assertThat(a).isEqualTo(allTime)
    }

    @Test
    fun periodVsPeriod_dropsTheTextOfBothSides() {
        val (a, b) = periodVsPeriod(october.copy(query = "tea"), today)

        assertThat(a.query).isEmpty()
        assertThat(b.query).isEmpty()
    }

    // --- category against category ----------------------------------------------------------

    @Test
    fun categoryVsCategory_bIsACopyOfA_withoutCategories() {
        val (a, b) = categoryVsCategory(october)

        assertThat(a).isEqualTo(october)
        assertThat(a.categoryIds).containsExactly(10L, 11L)
        assertThat(b.categoryIds).isEmpty()
        assertThat(b.accountIds).containsExactly(1L)
        assertThat(b.kind).isEqualTo(OperationKind.EXPENSE)
        assertThat(b.period).isEqualTo(HistoryPeriod.CUSTOM)
        assertThat(b.customFrom).isEqualTo(LocalDate.of(2026, 10, 1))
        assertThat(b.customTo).isEqualTo(LocalDate.of(2026, 10, 31))
    }

    @Test
    fun categoryVsCategory_keepsAPresetPeriodAsAPreset() {
        val thisMonth = OperationFilter(period = HistoryPeriod.THIS_MONTH, categoryIds = setOf(1L))

        val (_, b) = categoryVsCategory(thisMonth)

        assertThat(b.period).isEqualTo(HistoryPeriod.THIS_MONTH)
        assertThat(b.categoryIds).isEmpty()
    }

    @Test
    fun categoryVsCategory_dropsTheTextOfBothSides() {
        val (a, b) = categoryVsCategory(october.copy(query = "bread"))

        assertThat(a.query).isEmpty()
        assertThat(b.query).isEmpty()
    }
}
