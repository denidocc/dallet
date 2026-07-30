package com.dallet.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface OperationDao {
    @Query("SELECT * FROM operations ORDER BY dateEpochDay DESC, id DESC")
    suspend fun getAll(): List<OperationEntity>

    @Insert
    suspend fun insert(operation: OperationEntity): Long

    @Update
    suspend fun update(operation: OperationEntity)

    @Query("DELETE FROM operations WHERE id = :operationId")
    suspend fun delete(operationId: Long)
}
