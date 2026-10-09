package com.dallet.app.ledger

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class QuantityUnit(val label: String) {
    PIECE("шт"),
    KILOGRAM("кг"),
    GRAM("г"),
    LITER("л"),
    MILLILITER("мл"),
    PACK("уп.")
}

data class ShoppingList(
    val id: Long,
    val title: String,
    val createdAt: LocalDateTime,
    val closedAt: LocalDateTime? = null,
    val operationId: Long? = null,
    val note: String = ""
) {
    val isClosed: Boolean get() = closedAt != null
}

data class ShoppingItem(
    val id: Long,
    val listId: Long,
    val name: String,
    val unit: QuantityUnit,
    val quantityThousandths: Long,
    val unitPriceMinor: Long,
    val checked: Boolean,
    val position: Int
) {
    /** Quantity times unit price, rounded half up to the minor unit. */
    val lineTotalMinor: Long get() = (quantityThousandths * unitPriceMinor + 500L) / 1000L
}

data class Product(
    val id: Long,
    val name: String,
    val unit: QuantityUnit,
    val lastPriceMinor: Long? = null,
    val lastPricedAt: LocalDateTime? = null
)

data class PricePoint(
    val id: Long,
    val productId: Long,
    val at: LocalDateTime,
    val unitPriceMinor: Long
)

data class ShoppingData(
    val lists: List<ShoppingList> = emptyList(),
    val items: List<ShoppingItem> = emptyList(),
    val products: List<Product> = emptyList(),
    val pricePoints: List<PricePoint> = emptyList()
) {
    companion object {
        val EMPTY = ShoppingData()
    }
}

data class ShoppingListDetail(
    val list: ShoppingList,
    val items: List<ShoppingItem>
) {
    val totalMinor: Long get() = items.sumOf { it.lineTotalMinor }
    val checkedTotalMinor: Long get() = items.filter { it.checked }.sumOf { it.lineTotalMinor }
    val checkedCount: Int get() = items.count { it.checked }
}

data class ShoppingListSummary(
    val list: ShoppingList,
    val itemCount: Int,
    val checkedCount: Int,
    val totalMinor: Long,
    val checkedTotalMinor: Long
)

data class ClosePurchaseResult(
    val operationId: Long?,
    val totalMinor: Long,
    val carriedListId: Long?
)

object QuantityFormat {
    fun parse(input: String): Long {
        val normalized = input.trim().replace(',', '.')
        require(normalized.isNotEmpty()) { "empty quantity" }
        val value = BigDecimal(normalized).setScale(3, RoundingMode.HALF_UP)
        return value.movePointRight(3).longValueExact()
    }

    fun format(thousandths: Long): String =
        BigDecimal.valueOf(thousandths, 3).stripTrailingZeros().toPlainString()
}

interface ShoppingRepository {
    suspend fun getAll(): ShoppingData
    suspend fun replaceAll(data: ShoppingData)

    suspend fun lists(): List<ShoppingList>
    suspend fun list(id: Long): ShoppingList?
    suspend fun insertList(list: ShoppingList): Long
    suspend fun updateList(list: ShoppingList)
    suspend fun deleteList(id: Long)

    suspend fun items(listId: Long): List<ShoppingItem>
    suspend fun item(id: Long): ShoppingItem?
    suspend fun insertItem(item: ShoppingItem): Long
    suspend fun updateItem(item: ShoppingItem)
    suspend fun deleteItem(id: Long)

    suspend fun products(): List<Product>
    suspend fun insertProduct(product: Product): Long
    suspend fun updateProduct(product: Product)
    suspend fun deleteProduct(id: Long)

    suspend fun pricePoints(productId: Long): List<PricePoint>
    suspend fun insertPricePoint(point: PricePoint): Long
    suspend fun updatePricePoint(point: PricePoint)
}

class InMemoryShoppingRepository : ShoppingRepository {
    private var lists = mutableListOf<ShoppingList>()
    private var items = mutableListOf<ShoppingItem>()
    private var products = mutableListOf<Product>()
    private var points = mutableListOf<PricePoint>()
    private var nextListId = 1L
    private var nextItemId = 1L
    private var nextProductId = 1L
    private var nextPointId = 1L

    override suspend fun getAll(): ShoppingData =
        ShoppingData(lists.toList(), items.toList(), products.toList(), points.toList())

    override suspend fun replaceAll(data: ShoppingData) {
        lists = data.lists.toMutableList()
        items = data.items.toMutableList()
        products = data.products.toMutableList()
        points = data.pricePoints.toMutableList()
        nextListId = (lists.maxOfOrNull { it.id } ?: 0L) + 1L
        nextItemId = (items.maxOfOrNull { it.id } ?: 0L) + 1L
        nextProductId = (products.maxOfOrNull { it.id } ?: 0L) + 1L
        nextPointId = (points.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    override suspend fun lists(): List<ShoppingList> = lists.toList()

    override suspend fun list(id: Long): ShoppingList? = lists.firstOrNull { it.id == id }

    override suspend fun insertList(list: ShoppingList): Long {
        val id = if (list.id == 0L) nextListId++ else list.id
        lists.add(list.copy(id = id))
        return id
    }

    override suspend fun updateList(list: ShoppingList) {
        val index = lists.indexOfFirst { it.id == list.id }
        require(index >= 0) { "list not found" }
        lists[index] = list
    }

    override suspend fun deleteList(id: Long) {
        lists.removeAll { it.id == id }
        items.removeAll { it.listId == id }
    }

    override suspend fun items(listId: Long): List<ShoppingItem> =
        items.filter { it.listId == listId }

    override suspend fun item(id: Long): ShoppingItem? = items.firstOrNull { it.id == id }

    override suspend fun insertItem(item: ShoppingItem): Long {
        val id = if (item.id == 0L) nextItemId++ else item.id
        items.add(item.copy(id = id))
        return id
    }

    override suspend fun updateItem(item: ShoppingItem) {
        val index = items.indexOfFirst { it.id == item.id }
        require(index >= 0) { "item not found" }
        items[index] = item
    }

    override suspend fun deleteItem(id: Long) {
        items.removeAll { it.id == id }
    }

    override suspend fun products(): List<Product> = products.toList()

    override suspend fun insertProduct(product: Product): Long {
        val id = if (product.id == 0L) nextProductId++ else product.id
        products.add(product.copy(id = id))
        return id
    }

    override suspend fun updateProduct(product: Product) {
        val index = products.indexOfFirst { it.id == product.id }
        require(index >= 0) { "product not found" }
        products[index] = product
    }

    override suspend fun deleteProduct(id: Long) {
        products.removeAll { it.id == id }
        points.removeAll { it.productId == id }
    }

    override suspend fun pricePoints(productId: Long): List<PricePoint> =
        points.filter { it.productId == productId }.sortedBy { it.at }

    override suspend fun insertPricePoint(point: PricePoint): Long {
        val id = if (point.id == 0L) nextPointId++ else point.id
        points.add(point.copy(id = id))
        return id
    }

    override suspend fun updatePricePoint(point: PricePoint) {
        val index = points.indexOfFirst { it.id == point.id }
        require(index >= 0) { "price point not found" }
        points[index] = point
    }
}

class ShoppingService(
    private val ledger: Ledger,
    private val repository: ShoppingRepository
) {
    suspend fun createList(title: String, now: LocalDateTime): Long {
        val clean = title.trim().ifEmpty { DEFAULT_TITLE }
        return repository.insertList(ShoppingList(id = 0L, title = clean, createdAt = now))
    }

    suspend fun updateListInfo(listId: Long, title: String, note: String) {
        val list = openList(listId)
        repository.updateList(
            list.copy(title = title.trim().ifEmpty { DEFAULT_TITLE }, note = note.trim())
        )
    }

    suspend fun deleteList(listId: Long) {
        repository.deleteList(listId)
    }

    suspend fun openLists(): List<ShoppingListSummary> =
        summaries().filter { !it.list.isClosed }.sortedByDescending { it.list.createdAt }

    suspend fun archivedLists(): List<ShoppingListSummary> =
        summaries().filter { it.list.isClosed }.sortedByDescending { it.list.closedAt }

    suspend fun detail(listId: Long): ShoppingListDetail? {
        val list = repository.list(listId) ?: return null
        return ShoppingListDetail(list, sortedItems(listId))
    }

    suspend fun addItem(
        listId: Long,
        name: String,
        unit: QuantityUnit,
        quantityThousandths: Long,
        unitPriceMinor: Long
    ): Long {
        openList(listId)
        val clean = name.trim()
        require(clean.isNotEmpty()) { "name required" }
        require(quantityThousandths > 0) { "quantity must be positive" }
        require(unitPriceMinor >= 0) { "price must be non-negative" }
        ensureProduct(clean, unit)
        val position = (repository.items(listId).maxOfOrNull { it.position } ?: -1) + 1
        return repository.insertItem(
            ShoppingItem(
                id = 0L,
                listId = listId,
                name = clean,
                unit = unit,
                quantityThousandths = quantityThousandths,
                unitPriceMinor = unitPriceMinor,
                checked = false,
                position = position
            )
        )
    }

    suspend fun updateItem(item: ShoppingItem) {
        openList(item.listId)
        require(item.name.trim().isNotEmpty()) { "name required" }
        require(item.quantityThousandths > 0) { "quantity must be positive" }
        require(item.unitPriceMinor >= 0) { "price must be non-negative" }
        ensureProduct(item.name.trim(), item.unit)
        repository.updateItem(item.copy(name = item.name.trim()))
    }

    suspend fun setChecked(
        itemId: Long,
        checked: Boolean,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val item = repository.item(itemId) ?: error("item not found")
        openList(item.listId)
        repository.updateItem(item.copy(checked = checked))
        if (checked) {
            savePrices(listOf(item), now)
        }    }

    suspend fun deleteItem(itemId: Long) {
        val item = repository.item(itemId) ?: return
        openList(item.listId)
        repository.deleteItem(itemId)
    }

    suspend fun products(): List<Product> =
        repository.products().sortedBy { it.name.lowercase() }

    suspend fun deleteProduct(productId: Long) {
        repository.deleteProduct(productId)
    }

    suspend fun pricePoints(productId: Long): List<PricePoint> =
        repository.pricePoints(productId)

    /** Catalog matches for [query]: name prefix, then word prefix, then anywhere. */
    suspend fun suggestions(query: String): List<Product> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return repository.products()
            .mapNotNull { product ->
                val name = product.name.lowercase()
                val rank = when {
                    name.startsWith(needle) -> 0
                    name.split(' ', '-').any { it.startsWith(needle) } -> 1
                    name.contains(needle) -> 2
                    else -> return@mapNotNull null
                }
                rank to product
            }
            .sortedWith(compareBy({ it.first }, { it.second.name.lowercase() }))
            .map { it.second }
    }

    /** Remembers prices of all priced items in the list; returns how many were saved. */
    suspend fun savePrices(listId: Long, now: LocalDateTime): Int {
        openList(listId)
        return savePrices(repository.items(listId), now)
    }

    suspend fun closePurchase(
        listId: Long,
        accountId: Long,
        categoryId: Long,
        date: LocalDate,
        time: LocalTime?,
        now: LocalDateTime,
        carryOverUnchecked: Boolean
    ): ClosePurchaseResult {
        val list = openList(listId)
        val items = repository.items(listId)
        val checked = items.filter { it.checked }
        require(checked.isNotEmpty()) { "no checked items" }
        val total = checked.sumOf { it.lineTotalMinor }
        val operationId = if (total > 0L) {
            ledger.addExpense(
                accountId = accountId,
                categoryId = categoryId,
                amountMinor = total,
                date = date,
                comment = if (list.note.isBlank()) list.title else "${list.title} — ${list.note}",
                time = time
            )
        } else {
            null
        }
        savePrices(checked, now)
        repository.updateList(list.copy(closedAt = now, operationId = operationId))

        val carriedListId = if (carryOverUnchecked) {
            val unchecked = items.filter { !it.checked }.sortedBy { it.position }
            if (unchecked.isEmpty()) {
                null
            } else {
                val newId = repository.insertList(
                    ShoppingList(id = 0L, title = "${list.title} · остаток", createdAt = now)
                )
                unchecked.forEachIndexed { index, item ->
                    repository.insertItem(item.copy(id = 0L, listId = newId, position = index))
                }
                newId
            }
        } else {
            null
        }
        return ClosePurchaseResult(operationId, total, carriedListId)
    }

    private suspend fun savePrices(items: List<ShoppingItem>, now: LocalDateTime): Int {
        var saved = 0
        items.filter { it.unitPriceMinor > 0L }.forEach { item ->
            val product = ensureProduct(item.name, item.unit)
            repository.updateProduct(
                product.copy(lastPriceMinor = item.unitPriceMinor, lastPricedAt = now)
            )
            val sameDay = repository.pricePoints(product.id)
                .firstOrNull { it.at.toLocalDate() == now.toLocalDate() }
            if (sameDay != null) {
                repository.updatePricePoint(
                    sameDay.copy(at = now, unitPriceMinor = item.unitPriceMinor)
                )
            } else {
                repository.insertPricePoint(
                    PricePoint(
                        id = 0L,
                        productId = product.id,
                        at = now,
                        unitPriceMinor = item.unitPriceMinor
                    )
                )
            }
            saved++
        }
        return saved
    }

    private suspend fun ensureProduct(name: String, unit: QuantityUnit): Product {
        val key = name.trim().lowercase()
        repository.products()
            .firstOrNull { it.name.lowercase() == key && it.unit == unit }
            ?.let { return it }
        val created = Product(id = 0L, name = name.trim(), unit = unit)
        val id = repository.insertProduct(created)
        return created.copy(id = id)
    }

    private suspend fun openList(listId: Long): ShoppingList {
        val list = repository.list(listId)
        require(list != null) { "list not found" }
        require(!list.isClosed) { "list is closed" }
        return list
    }

    private suspend fun sortedItems(listId: Long): List<ShoppingItem> =
        repository.items(listId)
            .sortedWith(compareBy<ShoppingItem>({ it.checked }, { it.position }, { it.id }))

    private suspend fun summaries(): List<ShoppingListSummary> =
        repository.lists().map { list ->
            val items = repository.items(list.id)
            ShoppingListSummary(
                list = list,
                itemCount = items.size,
                checkedCount = items.count { it.checked },
                totalMinor = items.sumOf { it.lineTotalMinor },
                checkedTotalMinor = items.filter { it.checked }.sumOf { it.lineTotalMinor }
            )
        }

    companion object {
        const val DEFAULT_TITLE = "Покупки"
    }
}
