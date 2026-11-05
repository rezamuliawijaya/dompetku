package com.dompetku.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class TxType { INCOME, EXPENSE, TRANSFER }

enum class Workspace { PERSONAL, BUSINESS }

enum class AccountKind { KAS, BANK, EWALLET }

enum class TxSource { MANUAL, SMS, IMPORT, SYNC }

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val syncId: String = UUID.randomUUID().toString(),
    val title: String,
    // jumlah dalam rupiah, selalu positif; arah ditentukan lewat type
    val amount: Long,
    val type: TxType,
    val category: String,
    val note: String = "",
    // awal hari (tengah malam) waktu lokal
    val dateMillis: Long,
    val accountId: Long = 0L,
    // untuk TRANSFER: akun tujuan
    val counterAccountId: Long? = null,
    val workspace: Workspace = Workspace.PERSONAL,
    val source: TxSource = TxSource.MANUAL,
    // kunci deduplikasi untuk transaksi otomatis (hash isi SMS / baris import)
    val externalKey: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)
