package com.dompetku.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Proyeksi minimal untuk menghitung saldo per akun & per workspace */
data class LedgerRow(
    val type: TxType,
    val amount: Long,
    val accountId: Long,
    val counterAccountId: Long?
)

@Dao
interface TransactionDao {

    @Query(
        "SELECT * FROM transactions " +
            "WHERE workspace = :workspace AND deleted = 0 " +
            "AND dateMillis >= :start AND dateMillis < :end " +
            "ORDER BY dateMillis DESC, createdAt DESC"
    )
    fun observeBetween(workspace: Workspace, start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Transaction?

    @Query("SELECT * FROM transactions WHERE externalKey = :key LIMIT 1")
    suspend fun getByExternalKey(key: String): Transaction?

    @Query(
        "SELECT type, amount, accountId, counterAccountId FROM transactions " +
            "WHERE workspace = :workspace AND deleted = 0"
    )
    fun observeLedgerRows(workspace: Workspace): Flow<List<LedgerRow>>

    @Query("SELECT * FROM transactions")
    fun observeAllForSync(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions")
    suspend fun getAllForSync(): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Query("UPDATE transactions SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}
