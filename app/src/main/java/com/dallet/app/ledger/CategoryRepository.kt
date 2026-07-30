package com.dallet.app.ledger

interface CategoryRepository {
    suspend fun getAll(): List<Category>
    suspend fun replaceAll(categories: List<Category>)
}
