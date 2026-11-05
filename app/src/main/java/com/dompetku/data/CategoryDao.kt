package com.dompetku.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query(
        "SELECT * FROM categories WHERE workspace = :workspace AND deleted = 0 " +
            "ORDER BY sortIndex"
    )
    fun observeFor(workspace: Workspace): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): CategoryEntity?

    @Query("SELECT * FROM categories")
    suspend fun getAllForSync(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)
}
