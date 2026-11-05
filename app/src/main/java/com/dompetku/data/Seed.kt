package com.dompetku.data

import java.util.UUID

/** Kategori & akun bawaan yang di-seed saat database pertama dibuat. */
object Seed {

    private fun catId(workspace: Workspace, type: TxType, name: String): String =
        UUID.nameUUIDFromBytes("cat:$workspace:$type:$name".toByteArray()).toString()

    private fun accId(workspace: Workspace, name: String): String =
        UUID.nameUUIDFromBytes("acc:$workspace:$name".toByteArray()).toString()

    val personalExpense = listOf(
        "Makan & Minum" to "🍜",
        "Transportasi" to "🚌",
        "Belanja" to "🛍️",
        "Tagihan" to "🧾",
        "Hiburan" to "🎮",
        "Kesehatan" to "💊",
        "Pendidikan" to "📚",
        "Lainnya" to "📦",
    )

    val personalIncome = listOf(
        "Gaji" to "💼",
        "Bonus" to "🎁",
        "Usaha" to "🏪",
        "Hadiah" to "🎉",
        "Lainnya" to "💰",
    )

    val businessExpense = listOf(
        "Gaji Karyawan" to "👥",
        "Sewa" to "🏢",
        "Operasional" to "🖇️",
        "Marketing" to "📣",
        "Pajak" to "🧾",
        "Perlengkapan" to "📦",
        "Transportasi" to "🚌",
        "Lainnya" to "📦",
    )

    val businessIncome = listOf(
        "Penjualan" to "🛒",
        "Jasa Layanan" to "🤝",
        "Investasi" to "📈",
        "Lainnya" to "💰",
    )

    fun categories(): List<CategoryEntity> = buildList {
        var i = 0
        personalExpense.forEach { (name, emoji) ->
            add(CategoryEntity(syncId = catId(Workspace.PERSONAL, TxType.EXPENSE, name), name = name,
                emoji = emoji, type = TxType.EXPENSE, workspace = Workspace.PERSONAL, sortIndex = i++))
        }
        i = 0
        personalIncome.forEach { (name, emoji) ->
            add(CategoryEntity(syncId = catId(Workspace.PERSONAL, TxType.INCOME, name), name = name,
                emoji = emoji, type = TxType.INCOME, workspace = Workspace.PERSONAL, sortIndex = i++))
        }
        i = 0
        businessExpense.forEach { (name, emoji) ->
            add(CategoryEntity(syncId = catId(Workspace.BUSINESS, TxType.EXPENSE, name), name = name,
                emoji = emoji, type = TxType.EXPENSE, workspace = Workspace.BUSINESS, sortIndex = i++))
        }
        i = 0
        businessIncome.forEach { (name, emoji) ->
            add(CategoryEntity(syncId = catId(Workspace.BUSINESS, TxType.INCOME, name), name = name,
                emoji = emoji, type = TxType.INCOME, workspace = Workspace.BUSINESS, sortIndex = i++))
        }
    }

    fun accounts(): List<AccountEntity> = listOf(
        AccountEntity(
            syncId = accId(Workspace.PERSONAL, "Kas"),
            name = "Kas", kind = AccountKind.KAS, workspace = Workspace.PERSONAL
        ),
        AccountEntity(
            syncId = accId(Workspace.BUSINESS, "Kas Usaha"),
            name = "Kas Usaha", kind = AccountKind.KAS, workspace = Workspace.BUSINESS
        ),
    )

    const val TRANSFER_CATEGORY = "Transfer"
}
