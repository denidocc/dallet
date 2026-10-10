package com.dallet.app.ledger

/** Monthly spending limit of an expense category and how much of it is used. */
data class BudgetStatus(
    val category: Category,
    val limitMinor: Long,
    val spentMinor: Long
) {
    val fraction: Float
        get() = if (limitMinor <= 0) 0f else spentMinor.toFloat() / limitMinor.toFloat()

    val isOver: Boolean
        get() = spentMinor > limitMinor

    val isNear: Boolean
        get() = !isOver && fraction >= NEAR_THRESHOLD

    val remainingMinor: Long
        get() = limitMinor - spentMinor

    companion object {
        const val NEAR_THRESHOLD = 0.8f
    }
}
