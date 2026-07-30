package com.dallet.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun delete(accountId: Long)

    @Transaction
    suspend fun replaceAll(accounts: List<AccountEntity>) {
        deleteAll()
        insertAll(accounts)
    }
}
