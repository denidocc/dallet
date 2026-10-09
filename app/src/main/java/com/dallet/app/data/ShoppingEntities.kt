package com.dallet.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAtSec: Long,
    val closedAtSec: Long?,
    val operationId: Long?,
    @ColumnInfo(defaultValue = "''") val note: String = ""
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val name: String,
    val unit: String,
    val quantityThousandths: Long,
    val unitPriceMinor: Long,
    val checked: Boolean,
    val position: Int
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String,
    val lastPriceMinor: Long?,
    val lastPricedAtSec: Long?
)

@Entity(tableName = "price_points")
data class PricePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val atSec: Long,
    val unitPriceMinor: Long
)
