package com.dallet.app.ledger

import java.time.LocalDate

enum class OperationKind {
    INCOME,
    EXPENSE,
    TRANSFER
}

data class Operation(
    val id: Long,
    val kind: OperationKind,
    val amountMinor: Long,
    val date: LocalDate,
    val comment: String?,
    val accountId: Long?,
    val categoryId: Long?,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null
)
