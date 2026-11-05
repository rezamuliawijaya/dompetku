package com.dompetku.data

data class Category(val name: String, val emoji: String)

/**
 * Bantuan pencarian emoji kategori dari data seed.
 * Kategori untuk UI diambil dari database (CategoryDao); helper ini dipakai
 * untuk menampilkan emoji pada baris transaksi tanpa query tambahan.
 */
object Categories {

    fun seedFor(workspace: Workspace, type: TxType): List<Category> {
        val pairs = when (workspace to type) {
            Workspace.PERSONAL to TxType.EXPENSE -> Seed.personalExpense
            Workspace.PERSONAL to TxType.INCOME -> Seed.personalIncome
            Workspace.BUSINESS to TxType.EXPENSE -> Seed.businessExpense
            else -> Seed.businessIncome
        }
        return pairs.map { Category(it.first, it.second) }
    }

    fun forType(workspace: Workspace, type: TxType): List<Category> = seedFor(workspace, type)

    fun emojiOf(name: String, type: TxType, workspace: Workspace): String {
        val list = seedFor(workspace, type)
        return list.firstOrNull { it.name == name }?.emoji
            // kategori Transfer / tak dikenal
            ?: if (name == Seed.TRANSFER_CATEGORY) "🔁" else "📦"
    }
}
