package com.dallet.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the optional time of day (seconds from midnight) to operations. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE operations ADD COLUMN timeSecondOfDay INTEGER")
    }
}

/** Adds shopping lists, their items, the product catalog and price history. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `shopping_lists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`createdAtSec` INTEGER NOT NULL, " +
                "`closedAtSec` INTEGER, " +
                "`operationId` INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `shopping_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`listId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`quantityThousandths` INTEGER NOT NULL, " +
                "`unitPriceMinor` INTEGER NOT NULL, " +
                "`checked` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `products` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`lastPriceMinor` INTEGER, " +
                "`lastPricedAtSec` INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `price_points` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`productId` INTEGER NOT NULL, " +
                "`atSec` INTEGER NOT NULL, " +
                "`unitPriceMinor` INTEGER NOT NULL)"
        )
    }
}
