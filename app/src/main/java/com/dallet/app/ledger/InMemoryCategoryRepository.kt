package com.dallet.app.ledger

class InMemoryCategoryRepository : CategoryRepository {
    private val categories = mutableListOf<Category>()

    override suspend fun getAll(): List<Category> = categories.toList()

    override suspend fun replaceAll(categories: List<Category>) {
        this.categories.clear()
        this.categories.addAll(categories)
    }
}
