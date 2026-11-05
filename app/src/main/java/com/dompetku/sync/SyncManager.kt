package com.dompetku.sync

import android.content.Context
import com.dompetku.data.AccountEntity
import com.dompetku.data.AccountKind
import com.dompetku.data.AppDatabase
import com.dompetku.data.CategoryEntity
import com.dompetku.data.Transaction
import com.dompetku.data.TxSource
import com.dompetku.data.TxType
import com.dompetku.data.Workspace
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface SyncApi {
    @POST("api/v1/ledger")
    suspend fun createLedger(@Body body: CreateLedgerRequest): CreateLedgerResponse

    @POST("api/v1/sync")
    suspend fun sync(
        @Header("Authorization") auth: String,
        @Body body: SyncRequest
    ): SyncResponse
}

data class SyncConfig(val serverUrl: String, val token: String)

data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val serverTime: Long
)

/**
 * Sinkronisasi dua arah sederhana:
 * - Dorong semua baris lokal (upsert berdasarkan syncId di server).
 * - Tarik baris server yang berubah sejak tarik terakhir; terapkan last-write-wins
 *   berdasarkan updatedAt.
 */
object SyncManager {

    private const val PREFS = "dompetku_sync"
    private const val KEY_URL = "server_url"
    private const val KEY_TOKEN = "token"
    private const val KEY_LAST_SYNC = "last_sync"

    fun loadConfig(context: Context): SyncConfig? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return SyncConfig(url, token)
    }

    fun saveConfig(context: Context, config: SyncConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, config.serverUrl)
            .putString(KEY_TOKEN, config.token)
            .apply()
    }

    fun lastSync(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SYNC, 0L)

    fun setLastSync(context: Context, value: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_SYNC, value).apply()
    }

    fun api(baseUrl: String): SyncApi {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(
                Json { ignoreUnknownKeys = true; encodeDefaults = true }.asConverterFactory(
                    "application/json".toMediaType()
                )
            )
            .build()
            .create(SyncApi::class.java)
    }

    suspend fun createLedger(serverUrl: String, name: String): String =
        api(serverUrl).createLedger(CreateLedgerRequest(name)).token

    suspend fun syncNow(context: Context): SyncResult {
        val config = loadConfig(context)
            ?: error("Sinkronisasi belum dikonfigurasi")
        val db = AppDatabase.get(context)
        val api = api(config.serverUrl)

        // ---- kumpulkan data lokal ----
        val localAccounts = db.accountDao().getAllForSync()
        val localCategories = db.categoryDao().getAllForSync()
        val localTxs = db.transactionDao().getAllForSync()

        val accIdToSync = localAccounts.associate { it.id to it.syncId }

        val request = SyncRequest(
            changes = Changes(
                accounts = localAccounts.map { it.toDto() },
                categories = localCategories.map { it.toDto() },
                transactions = localTxs.mapNotNull { tx ->
                    val accSync = accIdToSync[tx.accountId] ?: return@mapNotNull null
                    val counterSync = tx.counterAccountId?.let { accIdToSync[it] }
                    tx.toDto(accSync, counterSync)
                }
            ),
            since = lastSync(context)
        )

        // ---- kirim & terima ----
        val response = api.sync("Bearer ${config.token}", request)

        // ---- terapkan baris dari server (last-write-wins) ----
        val accBySync = localAccounts.associate { it.syncId to it }
        var pulled = 0

        for (dto in response.records.accounts) {
            val local = accBySync[dto.syncId]
            if (local == null) {
                db.accountDao().insert(dto.toEntity())
                pulled++
            } else if (dto.updatedAt > local.updatedAt) {
                db.accountDao().update(dto.toEntity(local.id))
                pulled++
            }
        }

        for (dto in response.records.categories) {
            val local = db.categoryDao().getBySyncId(dto.syncId)
            if (local == null) {
                db.categoryDao().insert(dto.toEntity())
                pulled++
            } else if (dto.updatedAt > local.updatedAt) {
                db.categoryDao().update(dto.toEntity(local.id))
                pulled++
            }
        }

        // muat ulang peta akun untuk menyelesaikan accountId transaksi
        val accountsNow = db.accountDao().getAllForSync()
        val accSyncToId = accountsNow.associate { it.syncId to it.id }

        for (dto in response.records.transactions) {
            val local = db.transactionDao().getBySyncId(dto.syncId)
            val accountId = accSyncToId[dto.accountSyncId] ?: continue
            val counterId = dto.counterAccountSyncId?.let { accSyncToId[it] }
            if (local == null) {
                db.transactionDao().insert(dto.toEntity(accountId, counterId))
                pulled++
            } else if (dto.updatedAt > local.updatedAt) {
                db.transactionDao().update(dto.toEntity(local.id, counterId))
                pulled++
            }
        }

        setLastSync(context, response.serverTime)
        return SyncResult(request.changes.transactions.size, pulled, response.serverTime)
    }

    // ---- pemetaan entitas <-> DTO ----

    private fun AccountEntity.toDto() = AccountDto(
        syncId = syncId, name = name, kind = kind.name,
        institution = institution.ifEmpty { null },
        initialBalance = initialBalance, workspace = workspace.name,
        colorIndex = colorIndex, archived = archived,
        updatedAt = updatedAt, deleted = deleted
    )

    private fun AccountDto.toEntity(localId: Long = 0L) = AccountEntity(
        id = localId, syncId = syncId, name = name,
        kind = AccountKind.valueOf(kind),
        institution = institution.orEmpty(),
        initialBalance = initialBalance,
        workspace = Workspace.valueOf(workspace),
        colorIndex = colorIndex, archived = archived,
        updatedAt = updatedAt, deleted = deleted
    )

    private fun CategoryEntity.toDto() = CategoryDto(
        syncId = syncId, name = name, emoji = emoji, type = type.name,
        workspace = workspace.name, updatedAt = updatedAt, deleted = deleted
    )

    private fun CategoryDto.toEntity(localId: Long = 0L) = CategoryEntity(
        id = localId, syncId = syncId, name = name, emoji = emoji,
        type = TxType.valueOf(type), workspace = Workspace.valueOf(workspace),
        updatedAt = updatedAt, deleted = deleted
    )

    private fun Transaction.toDto(accSync: String, counterSync: String?) = TxDto(
        syncId = syncId, title = title, amount = amount, type = type.name,
        category = category, note = note, dateMillis = dateMillis,
        accountSyncId = accSync, counterAccountSyncId = counterSync,
        workspace = workspace.name, source = source.name, externalKey = externalKey,
        createdAt = createdAt, updatedAt = updatedAt, deleted = deleted
    )

    private fun TxDto.toEntity(accountId: Long, counterAccountId: Long?) = Transaction(
        syncId = syncId, title = title, amount = amount,
        type = TxType.valueOf(type), category = category, note = note,
        dateMillis = dateMillis, accountId = accountId,
        counterAccountId = counterAccountId,
        workspace = Workspace.valueOf(workspace),
        source = TxSource.valueOf(source), externalKey = externalKey,
        createdAt = createdAt, updatedAt = updatedAt, deleted = deleted
    )
}
