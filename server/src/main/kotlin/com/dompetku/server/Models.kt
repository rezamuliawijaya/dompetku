package com.dompetku.server

import kotlinx.serialization.Serializable

// ---- DTO sinkronisasi (paralel dengan entitas aplikasi) ----

@Serializable
data class TxDto(
    val syncId: String,
    val title: String,
    val amount: Long,
    val type: String,            // INCOME | EXPENSE | TRANSFER
    val category: String,
    val note: String = "",
    val dateMillis: Long,
    val accountSyncId: String,
    val counterAccountSyncId: String? = null,
    val workspace: String,       // PERSONAL | BUSINESS
    val source: String,          // MANUAL | SMS | IMPORT | SYNC
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false
)

@Serializable
data class AccountDto(
    val syncId: String,
    val name: String,
    val kind: String,            // KAS | BANK | EWALLET
    val institution: String? = null,
    val initialBalance: Long = 0L,
    val workspace: String,
    val colorIndex: Int = 0,
    val archived: Boolean = false,
    val updatedAt: Long,
    val deleted: Boolean = false
)

@Serializable
data class CategoryDto(
    val syncId: String,
    val name: String,
    val emoji: String,
    val type: String,            // INCOME | EXPENSE
    val workspace: String,       // PERSONAL | BUSINESS
    val updatedAt: Long,
    val deleted: Boolean = false
)

@Serializable
data class Changes(
    val transactions: List<TxDto> = emptyList(),
    val accounts: List<AccountDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList()
)

@Serializable
data class SyncRequest(
    val changes: Changes,
    val since: Long = 0L
)

@Serializable
data class SyncResponse(
    val records: Changes,
    val serverTime: Long
)

@Serializable
data class CreateLedgerRequest(val name: String = "")

@Serializable
data class CreateLedgerResponse(val token: String, val name: String)
