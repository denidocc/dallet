package com.dallet.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        OperationEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        ProductEntity::class,
        PricePointEntity::class,
        GoalEntity::class,
        GoalEntryEntity::class,
        DebtEntity::class,
        DebtEntryEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class DalletDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun operationDao(): OperationDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun goalDao(): GoalDao
    abstract fun debtDao(): DebtDao
}
