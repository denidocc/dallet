package com.dallet.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, OperationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class DalletDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun operationDao(): OperationDao
}
