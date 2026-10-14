package com.dallet.app.ledger

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

enum class BackupProblem {
    NOT_A_BACKUP,
    TOO_NEW
}

class InvalidBackupException(
    message: String,
    val problem: BackupProblem = BackupProblem.NOT_A_BACKUP
) : Exception(message)

data class BackupData(
    val accounts: List<Account>,
    val categories: List<Category>,
    val operations: List<Operation>,
    val shopping: ShoppingData = ShoppingData.EMPTY,
    val goals: GoalData = GoalData.EMPTY,
    val mainCurrency: Currency = Currency.DEFAULT,
    val budgets: Map<Long, Long> = emptyMap(),
    val recurring: List<RecurringRule> = emptyList(),
    val debts: DebtData = DebtData.EMPTY
)

object BackupCodec {
    const val SCHEMA_VERSION = 5
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
                        .apply { category.parentId?.let { put("parentId", it) } }
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
        root.put("goals", encodeGoals(data.goals))
        root.put("debts", encodeDebts(data.debts))
        root.put("mainCurrency", data.mainCurrency.name)
        root.put("recurring", RecurringCodec.encode(data.recurring))
        root.put("budgets", JSONArray().apply {
            data.budgets.forEach { (categoryId, limit) ->
                put(JSONObject().put("categoryId", categoryId).put("limitMinor", limit))
            }
        })
        return root.toString()
    }

    private fun encodeDebts(data: DebtData): JSONObject {
        val root = JSONObject()
        root.put("debts", JSONArray().apply {
            data.debts.forEach { debt ->
                put(
                    JSONObject()
                        .put("id", debt.id)
                        .put("person", debt.person)
                        .put("direction", debt.direction.name)
                        .put("note", debt.note)
                        .put("createdAt", debt.createdAt.toString())
                )
            }
        })
        root.put("entries", JSONArray().apply {
            data.entries.forEach { entry ->
                val item = JSONObject()
                    .put("id", entry.id)
                    .put("debtId", entry.debtId)
                    .put("kind", entry.kind.name)
                    .put("amountMinor", entry.amountMinor)
                    .put("date", entry.date.toString())
                entry.accountId?.let { item.put("accountId", it) }
                entry.operationId?.let { item.put("operationId", it) }
                put(item)
            }
        })
        return root
    }

    private fun decodeDebts(root: JSONObject): DebtData = DebtData(
        debts = root.optJSONArray("debts").objectsOrEmpty().map {
            Debt(
                id = it.getLong("id"),
                person = it.getString("person"),
                direction = DebtDirection.valueOf(it.getString("direction")),
                note = it.optNullableString("note").orEmpty(),
                createdAt = LocalDate.parse(it.getString("createdAt"))
            )
        },
        entries = root.optJSONArray("entries").objectsOrEmpty().map {
            DebtEntry(
                id = it.getLong("id"),
                debtId = it.getLong("debtId"),
                kind = DebtEntryKind.valueOf(it.getString("kind")),
                amountMinor = it.getLong("amountMinor"),
                date = LocalDate.parse(it.getString("date")),
                accountId = it.optNullableLong("accountId"),
                operationId = it.optNullableLong("operationId")
            )
        }
    )

    private fun validateDebts(debts: DebtData, accountIds: Set<Long>) {
        requireValid(hasUniquePositiveIds(debts.debts.map { it.id }))
        requireValid(hasUniquePositiveIds(debts.entries.map { it.id }))
        val debtIds = debts.debts.map { it.id }.toSet()
        requireValid(debts.debts.all { it.person.isNotBlank() })
        requireValid(
            debts.entries.all {
                it.debtId in debtIds && it.amountMinor > 0 && (it.accountId == null || it.accountId in accountIds)
            }
        )
        debts.debts.forEach { debt ->
            val own = debts.entries.filter { it.debtId == debt.id }
            val issued = own.filter { it.kind == DebtEntryKind.ISSUE }
            requireValid(issued.size == 1)
            val repaid = own.filter { it.kind != DebtEntryKind.ISSUE }.sumOf { it.amountMinor }
            requireValid(repaid <= issued.single().amountMinor)
        }
    }

    private fun encodeGoals(data: GoalData): JSONObject {
        val root = JSONObject()
        root.put("goals", JSONArray().apply {
            data.goals.forEach { goal ->
                put(
                    JSONObject()
                        .put("id", goal.id)
                        .put("name", goal.name)
                        .put("targetMinor", goal.targetMinor)
                        .put("createdAt", goal.createdAt.toString())
                        .put("archived", goal.archived)
                )
            }
        })
        root.put("entries", JSONArray().apply {
            data.entries.forEach { entry ->
                val item = JSONObject()
                    .put("id", entry.id)
                    .put("goalId", entry.goalId)
                    .put("accountId", entry.accountId)
                    .put("amountMinor", entry.amountMinor)
                    .put("date", entry.date.toString())
                entry.operationId?.let { item.put("operationId", it) }
                put(item)
            }
        })
        return root
    }

    private fun decodeGoals(root: JSONObject): GoalData = GoalData(
        goals = root.optJSONArray("goals").objectsOrEmpty().map {
            Goal(
                id = it.getLong("id"),
                name = it.getString("name"),
                targetMinor = it.getLong("targetMinor"),
                createdAt = LocalDate.parse(it.getString("createdAt")),
                archived = it.optBoolean("archived", false)
            )
        },
        entries = root.optJSONArray("entries").objectsOrEmpty().map {
            GoalEntry(
                id = it.getLong("id"),
                goalId = it.getLong("goalId"),
                accountId = it.getLong("accountId"),
                amountMinor = it.getLong("amountMinor"),
                date = LocalDate.parse(it.getString("date")),
                operationId = it.optNullableLong("operationId")
            )
        }
    )

    private fun validateGoals(goals: GoalData, accountIds: Set<Long>) {
        requireValid(hasUniquePositiveIds(goals.goals.map { it.id }))
        requireValid(hasUniquePositiveIds(goals.entries.map { it.id }))
        val goalIds = goals.goals.map { it.id }.toSet()
        requireValid(goals.goals.all { it.name.isNotBlank() && it.targetMinor > 0 })
        requireValid(
            goals.entries.all { it.goalId in goalIds && it.accountId in accountIds && it.amountMinor != 0L }
        )
        val saved = goals.entries.groupBy { it.goalId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        requireValid(saved.values.all { it >= 0L })
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
            if (version > SCHEMA_VERSION) throw InvalidBackupException(TOO_NEW, BackupProblem.TOO_NEW)
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
                    iconKey = it.getString("iconKey"),
                    parentId = it.optNullableLong("parentId")
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
            val goals = root.optJSONObject("goals")?.let { decodeGoals(it) } ?: GoalData.EMPTY
            val debts = root.optJSONObject("debts")?.let { decodeDebts(it) } ?: DebtData.EMPTY
            val currency = Currency.fromName(root.optNullableString("mainCurrency"))
            val budgets = root.optJSONArray("budgets").objectsOrEmpty()
                .associate { it.getLong("categoryId") to it.getLong("limitMinor") }
            val recurring = RecurringCodec.decode(root.optJSONArray("recurring"))
            return BackupData(
                accounts = accounts,
                categories = categories,
                operations = operations,
                shopping = shopping,
                goals = goals,
                mainCurrency = currency,
                budgets = budgets,
                recurring = recurring,
                debts = debts
            )
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
        val categoriesForParents = data.categories.associateBy { it.id }
        requireValid(
            data.categories.all { category ->
                val parent = category.parentId?.let { categoriesForParents[it] }
                category.parentId == null ||
                    (parent != null && parent.kind == category.kind && parent.parentId == null)
            }
        )
        validateShopping(data.shopping)
        validateGoals(data.goals, data.accounts.map { it.id }.toSet())
        validateDebts(data.debts, data.accounts.map { it.id }.toSet())
        val expenseIds = data.categories.filter { it.kind == CategoryKind.EXPENSE }.map { it.id }.toSet()
        requireValid(data.budgets.all { it.key in expenseIds && it.value > 0 })
        val validAccountIds = data.accounts.map { it.id }.toSet()
        val validCategoryIds = data.categories.map { it.id }.toSet()
        requireValid(hasUniquePositiveIds(data.recurring.map { it.id }))
        requireValid(
            data.recurring.all {
                it.accountId in validAccountIds && it.categoryId in validCategoryIds &&
                    it.amountMinor > 0 && it.kind != OperationKind.TRANSFER
            }
        )

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
