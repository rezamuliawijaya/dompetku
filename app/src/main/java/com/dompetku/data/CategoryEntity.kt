package com.dompetku.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val syncId: String,
    val name: String,
    val emoji: String,
    val type: TxType,
    val workspace: Workspace,
    val sortIndex: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)
