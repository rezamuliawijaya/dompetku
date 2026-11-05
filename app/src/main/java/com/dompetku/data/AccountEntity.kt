package com.dompetku.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val syncId: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: AccountKind,
    // kode platform bila kind = BANK/EWALLET: BCA, MANDIRI, GOPAY, dst.
    val institution: String = "",
    val initialBalance: Long = 0L,
    val workspace: Workspace,
    val colorIndex: Int = 0,
    val archived: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false
)
