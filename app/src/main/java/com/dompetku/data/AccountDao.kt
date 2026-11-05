package com.dompetku.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE workspace = :workspace AND deleted = 0 ORDER BY id")
    fun observeAll(workspace: Workspace): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): AccountEntity?

    @Query("SELECT * FROM accounts")
    suspend fun getAllForSync(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}
