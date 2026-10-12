package com.dallet.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_lists ORDER BY id ASC")
    suspend fun lists(): List<ShoppingListEntity>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun list(id: Long): ShoppingListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: ShoppingListEntity): Long

    @Update
    suspend fun updateList(list: ShoppingListEntity)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteListRow(id: Long)

    @Query("DELETE FROM shopping_items WHERE listId = :listId")
    suspend fun deleteItemsOfList(listId: Long)

    @Transaction
    suspend fun deleteList(id: Long) {
        deleteItemsOfList(id)
        deleteListRow(id)
    }

    @Query("SELECT * FROM shopping_items ORDER BY id ASC")
    suspend fun allItems(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE listId = :listId ORDER BY position ASC, id ASC")
    suspend fun items(listId: Long): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun item(id: Long): ShoppingItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItemEntity): Long

    @Update
    suspend fun updateItem(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT * FROM products ORDER BY id ASC")
    suspend fun products(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductRow(id: Long)

    @Query("DELETE FROM price_points WHERE productId = :productId")
    suspend fun deletePointsOfProduct(productId: Long)

    @Transaction
    suspend fun deleteProduct(id: Long) {
        deletePointsOfProduct(id)
        deleteProductRow(id)
    }

    @Query("SELECT * FROM price_points ORDER BY id ASC")
    suspend fun allPoints(): List<PricePointEntity>

    @Query("SELECT * FROM price_points WHERE productId = :productId ORDER BY atSec ASC, id ASC")
    suspend fun points(productId: Long): List<PricePointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: PricePointEntity): Long

    @Update
    suspend fun updatePoint(point: PricePointEntity)

    @Query("DELETE FROM price_points WHERE id = :id")
    suspend fun deletePoint(id: Long)

    @Query("DELETE FROM shopping_lists")
    suspend fun deleteAllLists()

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("DELETE FROM price_points")
    suspend fun deleteAllPoints()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLists(lists: List<ShoppingListEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ShoppingItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<PricePointEntity>)

    @Transaction
    suspend fun replaceAll(
        lists: List<ShoppingListEntity>,
        items: List<ShoppingItemEntity>,
        products: List<ProductEntity>,
        points: List<PricePointEntity>
    ) {
        deleteAllPoints()
        deleteAllProducts()
        deleteAllItems()
        deleteAllLists()
        insertLists(lists)
        insertItems(items)
        insertProducts(products)
        insertPoints(points)
    }
}
