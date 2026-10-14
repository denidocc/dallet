package com.dallet.app.data

import com.dallet.app.ledger.Category
import com.dallet.app.ledger.CategoryKind
import com.dallet.app.ledger.CategoryRepository

class RoomCategoryRepository(
    private val dao: CategoryDao
) : CategoryRepository {
    override suspend fun getAll(): List<Category> =
        dao.getAll().map { it.toDomain() }

    override suspend fun replaceAll(categories: List<Category>) {
        dao.replaceAll(categories.map { it.toEntity() })
    }

    override suspend fun insert(category: Category): Long {
        dao.insert(category.toEntity())
        return category.id
    }

    override suspend fun update(category: Category) {
        dao.update(category.toEntity())
    }

    override suspend fun delete(categoryId: Long) {
        dao.delete(categoryId)
    }
}

private fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        kind = CategoryKind.valueOf(kind),
        colorArgb = colorArgb,
        iconKey = iconKey,
        parentId = parentId
    )

private fun Category.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        kind = kind.name,
        colorArgb = colorArgb,
        iconKey = iconKey,
        parentId = parentId
    )
