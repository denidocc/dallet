package com.dallet.app

import android.app.Application
import androidx.room.Room
import com.dallet.app.data.DalletDatabase
import com.dallet.app.data.MIGRATION_2_3
import com.dallet.app.data.MIGRATION_3_4
import com.dallet.app.data.MIGRATION_4_5
import com.dallet.app.data.RoomAccountRepository
import com.dallet.app.data.RoomCategoryRepository
import com.dallet.app.data.RoomLedgerDataStore
import com.dallet.app.data.RoomOperationRepository
import com.dallet.app.data.RoomShoppingRepository
import com.dallet.app.data.SharedPreferencesSettingsStore
import com.dallet.app.backup.AutoBackup
import com.dallet.app.notify.DueReminders
import com.dallet.app.ledger.Ledger
import com.dallet.app.ledger.SettingsStore
import com.dallet.app.ledger.ShoppingService
import com.dallet.app.security.PinLock
import com.dallet.app.security.SharedPreferencesSecurityStorage

class DalletApplication : Application() {
    lateinit var ledger: Ledger
        private set

    lateinit var shopping: ShoppingService
        private set

    lateinit var pinLock: PinLock
        private set

    lateinit var settings: SettingsStore
        private set

    lateinit var strings: AppStrings
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        pinLock = PinLock(SharedPreferencesSecurityStorage(this))
        val database = Room.databaseBuilder(
            this,
            DalletDatabase::class.java,
            "dallet.db"
        ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigrationFrom(1)
            .build()
        settings = SharedPreferencesSettingsStore(this)
        strings = AppStrings(this, settings)
        DueReminders.createChannel(this, strings)
        DueReminders.schedule(this)
        AutoBackup.schedule(this)
        val accountRepository = RoomAccountRepository(database.accountDao())
        val categoryRepository = RoomCategoryRepository(database.categoryDao())
        val operationRepository = RoomOperationRepository(database.operationDao())
        val shoppingRepository = RoomShoppingRepository(database.shoppingDao())
        ledger = Ledger(
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            operationRepository = operationRepository,
            shoppingRepository = shoppingRepository,
            settings = settings,
            dataStore = RoomLedgerDataStore(
                database = database,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                operationRepository = operationRepository,
                shoppingRepository = shoppingRepository
            )
        )
        shopping = ShoppingService(ledger, shoppingRepository)
    }
}
