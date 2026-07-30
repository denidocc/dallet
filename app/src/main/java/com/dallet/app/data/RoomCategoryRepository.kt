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
}

private fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        kind = CategoryKind.valueOf(kind),
        colorArgb = colorArgb,
        iconKey = iconKey
    )

private fun Category.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        kind = kind.name,
        colorArgb = colorArgb,
        iconKey = iconKey
    )
