package com.dallet.app.ledger

enum class CategoryKind {
    INCOME,
    EXPENSE
}

data class Category(
    val id: Long,
    val name: String,
    val kind: CategoryKind,
    val colorArgb: Int,
    val iconKey: String
)
