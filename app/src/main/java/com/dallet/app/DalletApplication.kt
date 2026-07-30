package com.dallet.app

import android.app.Application
import androidx.room.Room
import com.dallet.app.data.DalletDatabase
import com.dallet.app.data.RoomAccountRepository
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
        ).build()
        ledger = Ledger(RoomAccountRepository(database.accountDao()))
    }
}
