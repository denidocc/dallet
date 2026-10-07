package com.dallet.app.ledger

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

class InvalidBackupException(message: String) : Exception(message)

data class BackupData(
    val accounts: List<Account>,
    val categories: List<Category>,
    val operations: List<Operation>
)

object BackupCodec {
    const val SCHEMA_VERSION = 1
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
                operation.comment?.let { item.put("comment", it) }
                operation.accountId?.let { item.put("accountId", it) }
                operation.categoryId?.let { item.put("categoryId", it) }
                operation.fromAccountId?.let { item.put("fromAccountId", it) }
                operation.toAccountId?.let { item.put("toAccountId", it) }
                put(item)
            }
        })
        return root.toString()
    }

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
                    toAccountId = it.optNullableLong("toAccountId")
                )
            }
            return BackupData(accounts, categories, operations)
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
