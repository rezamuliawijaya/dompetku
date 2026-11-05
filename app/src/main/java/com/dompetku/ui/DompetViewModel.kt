package com.dompetku.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dompetku.data.AccountDao
import com.dompetku.data.AccountEntity
import com.dompetku.data.AccountKind
import com.dompetku.data.AppDatabase
import com.dompetku.data.CategoryEntity
import com.dompetku.data.LedgerRow
import com.dompetku.data.Transaction
import com.dompetku.data.seedIfEmpty
import com.dompetku.data.TxType
import com.dompetku.data.Workspace
import com.dompetku.sms.SmsInbox
import com.dompetku.sync.SyncConfig
import com.dompetku.sync.SyncManager
import com.dompetku.sync.SyncWorker
import com.dompetku.util.Csv
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MonthData(
    val transactions: List<Transaction> = emptyList(),
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
    val byCategory: List<CategoryStat> = emptyList()
)

data class CategoryStat(val category: String, val total: Long)

data class AccountWithBalance(val account: AccountEntity, val balance: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class DompetViewModel(private val app: Application) : ViewModel() {

    private val db = AppDatabase.get(app)

    // ---- ruang kerja & bulan ----

    private val _workspace = MutableStateFlow(Workspace.PERSONAL)
    val workspace: StateFlow<Workspace> = _workspace

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    init {
        viewModelScope.launch { db.seedIfEmpty() }
    }

    fun setWorkspace(workspace: Workspace) { _workspace.value = workspace }
    fun prevMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() { _month.value = _month.value.plusMonths(1) }
    fun goCurrentMonth() { _month.value = YearMonth.now() }

    // ---- akun & saldo ----

    val accounts: StateFlow<List<AccountEntity>> = _workspace
        .flatMapLatest { db.accountDao().observeAll(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accountsWithBalance: StateFlow<List<AccountWithBalance>> =
        combine(
            accounts,
            _workspace.flatMapLatest { db.transactionDao().observeLedgerRows(it) }
        ) { accs, rows -> computeBalances(accs, rows) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workspaceBalance: StateFlow<Long> = accountsWithBalance
        .map { list -> list.sumOf { it.balance } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    // ---- data bulan terpilih ----

    val monthData: StateFlow<MonthData> =
        combine(_month, _workspace) { m, w -> m to w }
            .flatMapLatest { (m, w) ->
                val zone = ZoneId.systemDefault()
                val start = m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                db.transactionDao().observeBetween(w, start, end)
            }
            .map { list ->
                val income = list.filter { it.type == TxType.INCOME }.sumOf { it.amount }
                val expense = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
                val byCategory = list
                    .filter { it.type == TxType.EXPENSE }
                    .groupBy { it.category }
                    .map { (category, items) -> CategoryStat(category, items.sumOf { it.amount }) }
                    .sortedByDescending { it.total }
                MonthData(list, income, expense, byCategory)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthData())

    // ---- kategori ruang kerja aktif (untuk form) ----

    val categories: StateFlow<List<CategoryEntity>> = _workspace
        .flatMapLatest { db.categoryDao().observeFor(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- sinkronisasi ----

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    fun refreshSyncConfig() {
        val config = SyncManager.loadConfig(app)
        _syncStatus.value = if (config == null) null else "Terkonfigurasi: ${config.serverUrl}"
    }

    fun createSyncRoom(serverUrl: String) {
        viewModelScope.launch {
            try {
                val token = SyncManager.createLedger(serverUrl, "Ruang DompetKu")
                SyncManager.saveConfig(app, SyncConfig(serverUrl.trim(), token))
                SyncManager.setLastSync(app, 0L)
                SyncWorker.schedulePeriodic(app)
                _syncStatus.value = "Ruang sinkron dibuat ✓ — token tersimpan"
            } catch (t: Throwable) {
                _syncStatus.value = "Gagal membuat ruang: ${t.message}"
            }
        }
    }

    fun saveSyncConfig(serverUrl: String, token: String) {
        viewModelScope.launch {
            try {
                SyncManager.saveConfig(app, SyncConfig(serverUrl.trim(), token.trim()))
                SyncWorker.schedulePeriodic(app)
                _syncStatus.value = "Konfigurasi tersimpan ✓"
            } catch (t: Throwable) {
                _syncStatus.value = "Gagal: ${t.message}"
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _syncStatus.value = "Menyinkronkan…"
            try {
                val result = SyncManager.syncNow(app)
                SyncWorker.schedulePeriodic(app)
                _syncStatus.value =
                    "Tersinkron ✓ — dorong ${result.pushed}, tarik ${result.pulled} baris"
            } catch (t: Throwable) {
                _syncStatus.value = "Sinkron gagal: ${t.message}"
            }
        }
    }

    // ---- aksi transaksi ----

    fun save(transaction: Transaction) {
        viewModelScope.launch {
            val account = db.accountDao().getById(transaction.accountId)
            val ws = account?.workspace ?: transaction.workspace
            val dao = db.transactionDao()
            if (transaction.id == 0L) dao.insert(transaction.copy(workspace = ws))
            else dao.update(transaction.copy(workspace = ws, updatedAt = System.currentTimeMillis()))
        }
    }

    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            db.transactionDao().softDelete(transaction.id, System.currentTimeMillis())
        }
    }

    // ---- akun ----

    fun addAccount(
        name: String,
        kind: AccountKind,
        institution: String,
        initialBalance: Long,
        workspace: Workspace
    ) {
        viewModelScope.launch {
            db.accountDao().insert(
                AccountEntity(
                    name = name.trim(),
                    kind = kind,
                    institution = institution.trim().uppercase(),
                    initialBalance = initialBalance,
                    workspace = workspace
                )
            )
        }
    }

    suspend fun allAccounts(): List<AccountEntity> = db.accountDao().getAll()

    suspend fun accountById(id: Long): AccountEntity? = db.accountDao().getById(id)

    suspend fun accountNameMap(): Map<Long, String> =
        db.accountDao().getAll().associate { it.id to it.name }

    // ---- SMS ----

    fun importSms(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val r = SmsInbox.importFromInbox(app)
                onResult(
                    "SMS dipindai: ${r.scanned}\nCocok pola transaksi: ${r.matched}\n" +
                        "Tersimpan: ${r.imported}\nLewati (duplikat): ${r.skippedDuplicate}"
                )
            } catch (t: Throwable) {
                onResult("Gagal membaca SMS: ${t.message}")
            }
        }
    }

    // ---- ekspor CSV laporan bulanan ----

    fun exportCsv(
        month: YearMonth,
        workspace: Workspace,
        onReady: (Uri?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val zone = ZoneId.systemDefault()
                val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val txs = com.dompetku.data.AppDatabase.get(app).transactionDao()
                    .observeBetween(workspace, start, end)
                val list = txs.first()
                val accNames = accountNameMap()
                val wsLabel = if (workspace == Workspace.PERSONAL) "Pribadi" else "Bisnis"

                val header = listOf(
                    "Tanggal", "Jenis", "Kategori", "Keterangan", "Catatan", "Akun", "Jumlah"
                )
                val rows = mutableListOf(header)
                for (t in list) {
                    rows.add(
                        listOf(
                            com.dompetku.ui.formatDateCsv(t.dateMillis),
                            when (t.type) {
                                TxType.INCOME -> "Pemasukan"
                                TxType.EXPENSE -> "Pengeluaran"
                                TxType.TRANSFER -> "Transfer"
                            },
                            t.category,
                            t.title,
                            t.note,
                            accNames[t.accountId] ?: "-",
                            (if (t.type == TxType.INCOME) "" else "-") + t.amount
                        )
                    )
                }
                val fileName = "dompetku-${wsLabel.lowercase()}-${month}.csv"
                onReady(Csv.writeExport(app, fileName, rows))
            } catch (t: Throwable) {
                _syncStatus.value = "Ekspor gagal: ${t.message}"
                onReady(null)
            }
        }
    }

    // ---- import CSV ----

    fun importCsv(rows: List<Csv.Row>, accountId: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val account = db.accountDao().getById(accountId)
                    ?: return@launch onResult("Akun tidak ditemukan")
                val dao = db.transactionDao()
                var imported = 0
                var dup = 0
                for (row in rows) {
                    val key = Csv.dedupKey(row)
                    if (dao.getByExternalKey(key) != null) { dup++; continue }
                    dao.insert(
                        Transaction(
                            title = row.title,
                            amount = row.amount,
                            type = if (row.isExpense) TxType.EXPENSE else TxType.INCOME,
                            category = "Lainnya",
                            note = "Import CSV",
                            dateMillis = row.dateMillis,
                            accountId = accountId,
                            workspace = account.workspace,
                            source = com.dompetku.data.TxSource.IMPORT,
                            externalKey = key
                        )
                    )
                    imported++
                }
                onResult("Import selesai: $imported tersimpan, $dup duplikat dilewati")
            } catch (t: Throwable) {
                onResult("Import gagal: ${t.message}")
            }
        }
    }

    // ---- helper ----

    private fun computeBalances(
        accounts: List<AccountEntity>,
        rows: List<LedgerRow>
    ): List<AccountWithBalance> {
        val balances = accounts.associate { it.id to it.initialBalance }.toMutableMap()
        for (r in rows) {
            when (r.type) {
                TxType.INCOME ->
                    balances[r.accountId] = (balances[r.accountId] ?: 0L) + r.amount
                TxType.EXPENSE ->
                    balances[r.accountId] = (balances[r.accountId] ?: 0L) - r.amount
                TxType.TRANSFER -> {
                    balances[r.accountId] = (balances[r.accountId] ?: 0L) - r.amount
                    r.counterAccountId?.let {
                        balances[it] = (balances[it] ?: 0L) + r.amount
                    }
                }
            }
        }
        return accounts.map { AccountWithBalance(it, balances[it.id] ?: 0L) }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { DompetViewModel(app) }
        }
    }
}
