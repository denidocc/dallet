package com.dallet.app.ledger

interface CategoryRepository {
    suspend fun getAll(): List<Category>
    suspend fun replaceAll(categories: List<Category>)
    suspend fun insert(category: Category): Long
    suspend fun update(category: Category)
    suspend fun delete(categoryId: Long)
}
