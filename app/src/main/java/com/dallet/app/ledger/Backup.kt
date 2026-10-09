package com.dallet.app.ledger

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

class InvalidBackupException(message: String) : Exception(message)

data class BackupData(
    val accounts: List<Account>,
    val categories: List<Category>,
    val operations: List<Operation>,
    val shopping: ShoppingData = ShoppingData.EMPTY
)

object BackupCodec {
    const val SCHEMA_VERSION = 2
    private const val APP_MARKER = "dallet"
    private const val NOT_A_BACKUP = "Файл не похож на резервную копию Dallet"
    private const val TOO_NEW = "Резервная копия создана в более новой версии Dallet"

    fun encode(data: BackupData): String {
        val root = JSONObject()
        root.put("app", APP_MARKER)
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("accounts", JSONArray().apply {
            data.accounts.forEach { account ->
                put(
                    JSONObject()
                        .put("id", account.id)
                        .put("name", account.name)
                        .put("openingBalanceMinor", account.openingBalanceMinor)
                )
            }
        })
        root.put("categories", JSONArray().apply {
            data.categories.forEach { category ->
                put(
                    JSONObject()
                        .put("id", category.id)
                        .put("name", category.name)
                        .put("kind", category.kind.name)
                        .put("colorArgb", category.colorArgb)
                        .put("iconKey", category.iconKey)
                )
            }
        })
        root.put("operations", JSONArray().apply {
            data.operations.forEach { operation ->
                val item = JSONObject()
                    .put("id", operation.id)
                    .put("kind", operation.kind.name)
                    .put("amountMinor", operation.amountMinor)
                    .put("date", operation.date.toString())
                operation.time?.let { item.put("time", ReportCsv.timeLabel(it)) }
                operation.comment?.let { item.put("comment", it) }
                operation.accountId?.let { item.put("accountId", it) }
                operation.categoryId?.let { item.put("categoryId", it) }
                operation.fromAccountId?.let { item.put("fromAccountId", it) }
                operation.toAccountId?.let { item.put("toAccountId", it) }
                put(item)
            }
        })
        root.put("shopping", encodeShopping(data.shopping))
        return root.toString()
    }

    private fun encodeShopping(data: ShoppingData): JSONObject {
        val root = JSONObject()
        root.put("lists", JSONArray().apply {
            data.lists.forEach { list ->
                val item = JSONObject()
                    .put("id", list.id)
                    .put("title", list.title)
                    .put("createdAt", list.createdAt.toString())
                    .put("note", list.note)
                list.closedAt?.let { item.put("closedAt", it.toString()) }
                list.operationId?.let { item.put("operationId", it) }
                put(item)
            }
        })
        root.put("items", JSONArray().apply {
            data.items.forEach { item ->
                put(
                    JSONObject()
                        .put("id", item.id)
                        .put("listId", item.listId)
                        .put("name", item.name)
                        .put("unit", item.unit.name)
                        .put("quantityThousandths", item.quantityThousandths)
                        .put("unitPriceMinor", item.unitPriceMinor)
                        .put("checked", item.checked)
                        .put("position", item.position)
                )
            }
        })
        root.put("products", JSONArray().apply {
            data.products.forEach { product ->
                val item = JSONObject()
                    .put("id", product.id)
                    .put("name", product.name)
                    .put("unit", product.unit.name)
                product.lastPriceMinor?.let { item.put("lastPriceMinor", it) }
                product.lastPricedAt?.let { item.put("lastPricedAt", it.toString()) }
                put(item)
            }
        })
        root.put("pricePoints", JSONArray().apply {
            data.pricePoints.forEach { point ->
                put(
                    JSONObject()
                        .put("id", point.id)
                        .put("productId", point.productId)
                        .put("at", point.at.toString())
                        .put("unitPriceMinor", point.unitPriceMinor)
                )
            }
        })
        return root
    }

    private fun decodeShopping(root: JSONObject): ShoppingData = ShoppingData(
        lists = root.optJSONArray("lists").objectsOrEmpty().map {
            ShoppingList(
                id = it.getLong("id"),
                title = it.getString("title"),
                createdAt = LocalDateTime.parse(it.getString("createdAt")),
                closedAt = it.optNullableString("closedAt")?.let { text -> LocalDateTime.parse(text) },
                operationId = it.optNullableLong("operationId"),
                note = it.optNullableString("note").orEmpty()
            )
        },
        items = root.optJSONArray("items").objectsOrEmpty().map {
            ShoppingItem(
                id = it.getLong("id"),
                listId = it.getLong("listId"),
                name = it.getString("name"),
                unit = QuantityUnit.valueOf(it.getString("unit")),
                quantityThousandths = it.getLong("quantityThousandths"),
                unitPriceMinor = it.getLong("unitPriceMinor"),
                checked = it.getBoolean("checked"),
                position = it.getInt("position")
            )
        },
        products = root.optJSONArray("products").objectsOrEmpty().map {
            Product(
                id = it.getLong("id"),
                name = it.getString("name"),
                unit = QuantityUnit.valueOf(it.getString("unit")),
                lastPriceMinor = it.optNullableLong("lastPriceMinor"),
                lastPricedAt = it.optNullableString("lastPricedAt")
                    ?.let { text -> LocalDateTime.parse(text) }
            )
        },
        pricePoints = root.optJSONArray("pricePoints").objectsOrEmpty().map {
            PricePoint(
                id = it.getLong("id"),
                productId = it.getLong("productId"),
                at = LocalDateTime.parse(it.getString("at")),
                unitPriceMinor = it.getLong("unitPriceMinor")
            )
        }
    )

    private fun validateShopping(shopping: ShoppingData) {
        requireValid(hasUniquePositiveIds(shopping.lists.map { it.id }))
        requireValid(hasUniquePositiveIds(shopping.items.map { it.id }))
        requireValid(hasUniquePositiveIds(shopping.products.map { it.id }))
        requireValid(hasUniquePositiveIds(shopping.pricePoints.map { it.id }))
        val listIds = shopping.lists.map { it.id }.toSet()
        val productIds = shopping.products.map { it.id }.toSet()
        requireValid(shopping.lists.all { it.title.isNotBlank() })
        requireValid(
            shopping.items.all {
                it.listId in listIds &&
                    it.name.isNotBlank() &&
                    it.quantityThousandths > 0 &&
                    it.unitPriceMinor >= 0
            }
        )
        requireValid(shopping.products.all { it.name.isNotBlank() })
        requireValid(
            shopping.pricePoints.all { it.productId in productIds && it.unitPriceMinor > 0 }
        )
    }

    private fun JSONArray?.objectsOrEmpty(): List<JSONObject> = this?.objects() ?: emptyList()

    fun decode(json: String): BackupData {
        try {
            val root = JSONObject(json)
            if (root.optString("app") != APP_MARKER) throw InvalidBackupException(NOT_A_BACKUP)
            val version = root.getInt("schemaVersion")
            if (version > SCHEMA_VERSION) throw InvalidBackupException(TOO_NEW)
            if (version < 1) throw InvalidBackupException(NOT_A_BACKUP)

            val accounts = root.getJSONArray("accounts").objects().map {
                Account(
                    id = it.getLong("id"),
                    name = it.getString("name"),
                    openingBalanceMinor = it.getLong("openingBalanceMinor")
                )
            }
            val categories = root.getJSONArray("categories").objects().map {
                Category(
                    id = it.getLong("id"),
                    name = it.getString("name"),
                    kind = CategoryKind.valueOf(it.getString("kind")),
                    colorArgb = it.getInt("colorArgb"),
                    iconKey = it.getString("iconKey")
                )
            }
            val operations = root.getJSONArray("operations").objects().map {
                Operation(
                    id = it.getLong("id"),
                    kind = OperationKind.valueOf(it.getString("kind")),
                    amountMinor = it.getLong("amountMinor"),
                    date = LocalDate.parse(it.getString("date")),
                    comment = it.optNullableString("comment"),
                    accountId = it.optNullableLong("accountId"),
                    categoryId = it.optNullableLong("categoryId"),
                    fromAccountId = it.optNullableLong("fromAccountId"),
                    toAccountId = it.optNullableLong("toAccountId"),
                    time = it.optNullableString("time")?.let { text -> LocalTime.parse(text) }
                )
            }
            val shopping = root.optJSONObject("shopping")?.let { decodeShopping(it) }
                ?: ShoppingData.EMPTY
            return BackupData(accounts, categories, operations, shopping)
        } catch (error: InvalidBackupException) {
            throw error
        } catch (error: JSONException) {
            throw InvalidBackupException(NOT_A_BACKUP)
        } catch (error: IllegalArgumentException) {
            throw InvalidBackupException(NOT_A_BACKUP)
        } catch (error: DateTimeParseException) {
            throw InvalidBackupException(NOT_A_BACKUP)
        }
    }

    fun validate(data: BackupData) {
        if (data.accounts.isEmpty()) throw InvalidBackupException(NOT_A_BACKUP)
        requireValid(hasUniquePositiveIds(data.accounts.map { it.id }))
        requireValid(hasUniquePositiveIds(data.categories.map { it.id }))
        requireValid(hasUniquePositiveIds(data.operations.map { it.id }))
        requireValid(data.accounts.all { it.name.isNotBlank() && it.openingBalanceMinor >= 0 })
        requireValid(data.categories.all { it.name.isNotBlank() })
        validateShopping(data.shopping)

        val accountIds = data.accounts.map { it.id }.toSet()
        val categoriesById = data.categories.associateBy { it.id }
        data.operations.forEach { operation ->
            requireValid(operation.amountMinor > 0)
            when (operation.kind) {
                OperationKind.EXPENSE, OperationKind.INCOME -> {
                    val category = categoriesById[operation.categoryId]
                    requireValid(operation.accountId in accountIds)
                    requireValid(category != null)
                    val expected =
                        if (operation.kind == OperationKind.EXPENSE) CategoryKind.EXPENSE
                        else CategoryKind.INCOME
                    requireValid(category?.kind == expected)
                }
                OperationKind.TRANSFER -> {
                    requireValid(operation.fromAccountId in accountIds)
                    requireValid(operation.toAccountId in accountIds)
                    requireValid(operation.fromAccountId != operation.toAccountId)
                }
            }
        }
    }

    private fun hasUniquePositiveIds(ids: List<Long>): Boolean =
        ids.size == ids.toSet().size && ids.all { it > 0 }

    private fun requireValid(condition: Boolean) {
        if (!condition) throw InvalidBackupException(NOT_A_BACKUP)
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else getLong(key)
}
