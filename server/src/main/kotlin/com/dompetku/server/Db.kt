package com.dompetku.server

import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * Penyimpanan SQLite sederhana untuk server sinkronisasi.
 * Satu "ruang data" (ledger) = satu token; semua tabel berkunci (token, sync_id).
 */
object Db {

    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:dompetku-server.db")

    init {
        conn.createStatement().use { st ->
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ledgers(" +
                    "token TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL)"
            )
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS accounts(" +
                    "token TEXT NOT NULL, sync_id TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL," +
                    "institution TEXT, initial_balance INTEGER NOT NULL, workspace TEXT NOT NULL," +
                    "color_index INTEGER NOT NULL, archived INTEGER NOT NULL, updated_at INTEGER NOT NULL," +
                    "deleted INTEGER NOT NULL, PRIMARY KEY(token, sync_id))"
            )
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS categories(" +
                    "token TEXT NOT NULL, sync_id TEXT NOT NULL, name TEXT NOT NULL, emoji TEXT NOT NULL," +
                    "type TEXT NOT NULL, workspace TEXT NOT NULL, updated_at INTEGER NOT NULL," +
                    "deleted INTEGER NOT NULL, PRIMARY KEY(token, sync_id))"
            )
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS transactions(" +
                    "token TEXT NOT NULL, sync_id TEXT NOT NULL, title TEXT NOT NULL, amount INTEGER NOT NULL," +
                    "type TEXT NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL, date_millis INTEGER NOT NULL," +
                    "account_sync_id TEXT NOT NULL, counter_account_sync_id TEXT, workspace TEXT NOT NULL," +
                    "source TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL," +
                    "deleted INTEGER NOT NULL, PRIMARY KEY(token, sync_id))"
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tx_token_upd ON transactions(token, updated_at)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_acc_token_upd ON accounts(token, updated_at)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_cat_token_upd ON categories(token, updated_at)")
        }
    }

    fun createLedger(name: String): CreateLedgerResponse {
        val token = UUID.randomUUID().toString().replace("-", "")
        conn.prepareStatement("INSERT INTO ledgers(token, name, created_at) VALUES (?,?,?)").use { ps ->
            ps.setString(1, token)
            ps.setString(2, name.ifBlank { "Ruang DompetKu" })
            ps.setLong(3, System.currentTimeMillis())
            ps.executeUpdate()
        }
        return CreateLedgerResponse(token, name)
    }

    fun ledgerExists(token: String): Boolean =
        conn.prepareStatement("SELECT 1 FROM ledgers WHERE token = ?").use { ps ->
            ps.setString(1, token)
            ps.executeQuery().next()
        }

    fun upsertAccounts(token: String, items: List<AccountDto>) {
        conn.prepareStatement(
            "INSERT OR REPLACE INTO accounts(token, sync_id, name, kind, institution, initial_balance, " +
                "workspace, color_index, archived, updated_at, deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?)"
        ).use { ps ->
            for (a in items) {
                ps.setString(1, token); ps.setString(2, a.syncId); ps.setString(3, a.name)
                ps.setString(4, a.kind); ps.setString(5, a.institution); ps.setLong(6, a.initialBalance)
                ps.setString(7, a.workspace); ps.setInt(8, a.colorIndex)
                ps.setInt(9, if (a.archived) 1 else 0); ps.setLong(10, a.updatedAt)
                ps.setInt(11, if (a.deleted) 1 else 0)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    fun upsertCategories(token: String, items: List<CategoryDto>) {
        conn.prepareStatement(
            "INSERT OR REPLACE INTO categories(token, sync_id, name, emoji, type, workspace, updated_at, deleted) " +
                "VALUES (?,?,?,?,?,?,?,?)"
        ).use { ps ->
            for (c in items) {
                ps.setString(1, token); ps.setString(2, c.syncId); ps.setString(3, c.name)
                ps.setString(4, c.emoji); ps.setString(5, c.type); ps.setString(6, c.workspace)
                ps.setLong(7, c.updatedAt); ps.setInt(8, if (c.deleted) 1 else 0)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    fun upsertTransactions(token: String, items: List<TxDto>) {
        conn.prepareStatement(
            "INSERT OR REPLACE INTO transactions(token, sync_id, title, amount, type, category, note, date_millis, " +
                "account_sync_id, counter_account_sync_id, workspace, source, created_at, updated_at, deleted) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { ps ->
            for (t in items) {
                ps.setString(1, token); ps.setString(2, t.syncId); ps.setString(3, t.title)
                ps.setLong(4, t.amount); ps.setString(5, t.type); ps.setString(6, t.category)
                ps.setString(7, t.note); ps.setLong(8, t.dateMillis)
                ps.setString(9, t.accountSyncId); ps.setString(10, t.counterAccountSyncId)
                ps.setString(11, t.workspace); ps.setString(12, t.source)
                ps.setLong(13, t.createdAt); ps.setLong(14, t.updatedAt)
                ps.setInt(15, if (t.deleted) 1 else 0)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    fun pullAccounts(token: String, since: Long): List<AccountDto> {
        val out = mutableListOf<AccountDto>()
        conn.prepareStatement(
            "SELECT sync_id, name, kind, institution, initial_balance, workspace, color_index, archived, " +
                "updated_at, deleted FROM accounts WHERE token = ? AND updated_at > ?"
        ).use { ps ->
            ps.setString(1, token); ps.setLong(2, since)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out.add(
                        AccountDto(
                            syncId = rs.getString(1), name = rs.getString(2), kind = rs.getString(3),
                            institution = rs.getString(4), initialBalance = rs.getLong(5),
                            workspace = rs.getString(6), colorIndex = rs.getInt(7),
                            archived = rs.getInt(8) == 1, updatedAt = rs.getLong(9),
                            deleted = rs.getInt(10) == 1
                        )
                    )
                }
            }
        }
        return out
    }

    fun pullCategories(token: String, since: Long): List<CategoryDto> {
        val out = mutableListOf<CategoryDto>()
        conn.prepareStatement(
            "SELECT sync_id, name, emoji, type, workspace, updated_at, deleted FROM categories " +
                "WHERE token = ? AND updated_at > ?"
        ).use { ps ->
            ps.setString(1, token); ps.setLong(2, since)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out.add(
                        CategoryDto(
                            syncId = rs.getString(1), name = rs.getString(2), emoji = rs.getString(3),
                            type = rs.getString(4), workspace = rs.getString(5),
                            updatedAt = rs.getLong(6), deleted = rs.getInt(7) == 1
                        )
                    )
                }
            }
        }
        return out
    }

    fun pullTransactions(token: String, since: Long): List<TxDto> {
        val out = mutableListOf<TxDto>()
        conn.prepareStatement(
            "SELECT sync_id, title, amount, type, category, note, date_millis, account_sync_id, " +
                "counter_account_sync_id, workspace, source, created_at, updated_at, deleted FROM transactions " +
                "WHERE token = ? AND updated_at > ?"
        ).use { ps ->
            ps.setString(1, token); ps.setLong(2, since)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out.add(
                        TxDto(
                            syncId = rs.getString(1), title = rs.getString(2), amount = rs.getLong(3),
                            type = rs.getString(4), category = rs.getString(5), note = rs.getString(6),
                            dateMillis = rs.getLong(7), accountSyncId = rs.getString(8),
                            counterAccountSyncId = rs.getString(9), workspace = rs.getString(10),
                            source = rs.getString(11), createdAt = rs.getLong(12),
                            updatedAt = rs.getLong(13), deleted = rs.getInt(14) == 1
                        )
                    )
                }
            }
        }
        return out
    }
}
