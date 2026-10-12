package com.dallet.app.data

import com.dallet.app.ledger.PricePoint
import com.dallet.app.ledger.Product
import com.dallet.app.ledger.QuantityUnit
import com.dallet.app.ledger.ShoppingData
import com.dallet.app.ledger.ShoppingItem
import com.dallet.app.ledger.ShoppingList
import com.dallet.app.ledger.ShoppingRepository
import java.time.LocalDateTime
import java.time.ZoneOffset

class RoomShoppingRepository(
    private val dao: ShoppingDao
) : ShoppingRepository {
    override suspend fun getAll(): ShoppingData =
        ShoppingData(
            lists = dao.lists().map { it.toDomain() },
            items = dao.allItems().map { it.toDomain() },
            products = dao.products().map { it.toDomain() },
            pricePoints = dao.allPoints().map { it.toDomain() }
        )

    override suspend fun replaceAll(data: ShoppingData) {
        dao.replaceAll(
            lists = data.lists.map { it.toEntity() },
            items = data.items.map { it.toEntity() },
            products = data.products.map { it.toEntity() },
            points = data.pricePoints.map { it.toEntity() }
        )
    }

    override suspend fun lists(): List<ShoppingList> = dao.lists().map { it.toDomain() }

    override suspend fun list(id: Long): ShoppingList? = dao.list(id)?.toDomain()

    override suspend fun insertList(list: ShoppingList): Long = dao.insertList(list.toEntity())

    override suspend fun updateList(list: ShoppingList) {
        dao.updateList(list.toEntity())
    }

    override suspend fun deleteList(id: Long) {
        dao.deleteList(id)
    }

    override suspend fun items(listId: Long): List<ShoppingItem> =
        dao.items(listId).map { it.toDomain() }

    override suspend fun item(id: Long): ShoppingItem? = dao.item(id)?.toDomain()

    override suspend fun insertItem(item: ShoppingItem): Long = dao.insertItem(item.toEntity())

    override suspend fun updateItem(item: ShoppingItem) {
        dao.updateItem(item.toEntity())
    }

    override suspend fun deleteItem(id: Long) {
        dao.deleteItem(id)
    }

    override suspend fun products(): List<Product> = dao.products().map { it.toDomain() }

    override suspend fun insertProduct(product: Product): Long =
        dao.insertProduct(product.toEntity())

    override suspend fun updateProduct(product: Product) {
        dao.updateProduct(product.toEntity())
    }

    override suspend fun deleteProduct(id: Long) {
        dao.deleteProduct(id)
    }

    override suspend fun pricePoints(productId: Long): List<PricePoint> =
        dao.points(productId).map { it.toDomain() }

    override suspend fun insertPricePoint(point: PricePoint): Long =
        dao.insertPoint(point.toEntity())

    override suspend fun updatePricePoint(point: PricePoint) {
        dao.updatePoint(point.toEntity())
    }

    override suspend fun deletePricePoint(id: Long) {
        dao.deletePoint(id)
    }
}

private fun LocalDateTime.toSec(): Long = toEpochSecond(ZoneOffset.UTC)

private fun Long.toDateTime(): LocalDateTime = LocalDateTime.ofEpochSecond(this, 0, ZoneOffset.UTC)

private fun ShoppingListEntity.toDomain() = ShoppingList(
    id = id,
    title = title,
    createdAt = createdAtSec.toDateTime(),
    closedAt = closedAtSec?.toDateTime(),
    operationId = operationId,
    note = note
)

private fun ShoppingList.toEntity() = ShoppingListEntity(
    id = id,
    title = title,
    createdAtSec = createdAt.toSec(),
    closedAtSec = closedAt?.toSec(),
    operationId = operationId,
    note = note
)

private fun ShoppingItemEntity.toDomain() = ShoppingItem(
    id = id,
    listId = listId,
    name = name,
    unit = QuantityUnit.valueOf(unit),
    quantityThousandths = quantityThousandths,
    unitPriceMinor = unitPriceMinor,
    checked = checked,
    position = position
)

private fun ShoppingItem.toEntity() = ShoppingItemEntity(
    id = id,
    listId = listId,
    name = name,
    unit = unit.name,
    quantityThousandths = quantityThousandths,
    unitPriceMinor = unitPriceMinor,
    checked = checked,
    position = position
)

private fun ProductEntity.toDomain() = Product(
    id = id,
    name = name,
    unit = QuantityUnit.valueOf(unit),
    lastPriceMinor = lastPriceMinor,
    lastPricedAt = lastPricedAtSec?.toDateTime()
)

private fun Product.toEntity() = ProductEntity(
    id = id,
    name = name,
    unit = unit.name,
    lastPriceMinor = lastPriceMinor,
    lastPricedAtSec = lastPricedAt?.toSec()
)

private fun PricePointEntity.toDomain() = PricePoint(
    id = id,
    productId = productId,
    at = atSec.toDateTime(),
    unitPriceMinor = unitPriceMinor
)

private fun PricePoint.toEntity() = PricePointEntity(
    id = id,
    productId = productId,
    atSec = at.toSec(),
    unitPriceMinor = unitPriceMinor
)
