package com.dallet.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Transaction
    suspend fun replaceAll(categories: List<CategoryEntity>) {
        deleteAll()
        insertAll(categories)
    }
}
