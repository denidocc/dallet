package com.dallet.app.ledger

class InMemoryCategoryRepository : CategoryRepository {
    private val categories = mutableListOf<Category>()
    private var nextId = 1L

    override suspend fun getAll(): List<Category> = categories.toList()

    override suspend fun replaceAll(categories: List<Category>) {
        this.categories.clear()
        this.categories.addAll(categories)
        nextId = (categories.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    override suspend fun insert(category: Category): Long {
        val id = if (category.id == 0L) nextId++ else category.id
        categories.add(category.copy(id = id))
        return id
    }

    override suspend fun update(category: Category) {
        val index = categories.indexOfFirst { it.id == category.id }
        require(index >= 0) { "category not found" }
        categories[index] = category
    }

    override suspend fun delete(categoryId: Long) {
        categories.removeAll { it.id == categoryId }
    }
}
