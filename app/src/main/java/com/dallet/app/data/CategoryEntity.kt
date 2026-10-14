package com.dallet.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val kind: String,
    val colorArgb: Int,
    val iconKey: String,
    val parentId: Long? = null
)
