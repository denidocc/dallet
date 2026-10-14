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

/** Adds a free-form note to shopping lists. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_lists ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}

/** Adds savings goals and the money moved into them. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`targetMinor` INTEGER NOT NULL, " +
                "`createdEpochDay` INTEGER NOT NULL, " +
                "`archived` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`goalId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "`dateEpochDay` INTEGER NOT NULL, " +
                "`operationId` INTEGER)"
        )
    }
}

/** Adds debts and the money moved through them. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `debts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`person` TEXT NOT NULL, " +
                "`direction` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdEpochDay` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `debt_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`debtId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "`dateEpochDay` INTEGER NOT NULL, " +
                "`accountId` INTEGER, " +
                "`operationId` INTEGER)"
        )
    }
}

/** Adds subcategories: a category may point at a top-level parent. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN parentId INTEGER")
    }
}
