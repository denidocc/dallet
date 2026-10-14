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
    val iconKey: String,
    /** The top-level category this one belongs to; null for a top-level category. */
    val parentId: Long? = null
)
