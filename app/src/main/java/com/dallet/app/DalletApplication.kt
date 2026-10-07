package com.dallet.app

import android.app.Application
import androidx.room.Room
import com.dallet.app.data.DalletDatabase
import com.dallet.app.data.RoomAccountRepository
import com.dallet.app.data.RoomCategoryRepository
import com.dallet.app.data.RoomLedgerDataStore
import com.dallet.app.data.RoomOperationRepository
import com.dallet.app.ledger.Ledger

class DalletApplication : Application() {
    lateinit var ledger: Ledger
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            this,
            DalletDatabase::class.java,
            "dallet.db"
        ).fallbackToDestructiveMigration().build()
        val accountRepository = RoomAccountRepository(database.accountDao())
        val categoryRepository = RoomCategoryRepository(database.categoryDao())
        val operationRepository = RoomOperationRepository(database.operationDao())
        ledger = Ledger(
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            operationRepository = operationRepository,
            dataStore = RoomLedgerDataStore(
                database = database,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                operationRepository = operationRepository
            )
        )
    }
}
