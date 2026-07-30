package com.dallet.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OperationDao {
    @Query("SELECT * FROM operations ORDER BY dateEpochDay DESC, id DESC")
    suspend fun getAll(): List<OperationEntity>

    @Insert
    suspend fun insert(operation: OperationEntity): Long
}
