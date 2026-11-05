package com.dompetku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dompetku.data.AccountEntity
import com.dompetku.data.Categories
import com.dompetku.data.Seed
import com.dompetku.data.Transaction
import com.dompetku.data.TxType
import com.dompetku.data.Workspace
import com.dompetku.sync.SyncManager
import com.dompetku.sync.SyncWorker
import com.dompetku.ui.theme.ExpenseRed
import com.dompetku.ui.theme.ExpenseRedDark
import com.dompetku.ui.theme.IncomeGreen
import com.dompetku.ui.theme.IncomeGreenDark
import java.time.LocalDate
import kotlin.math.abs

enum class HomeTab(val label: String) {
    BERANDA("Beranda"),
    LAPORAN("Laporan"),
    AKUN("Akun"),
    PENGATURAN("Pengaturan");

    @Composable
    fun icon() = when (this) {
        BERANDA -> Icons.Rounded.Home
        LAPORAN -> Icons.Rounded.Assessment
        AKUN -> Icons.Rounded.AccountBalanceWallet
        PENGATURAN -> Icons.Rounded.Settings
    }
}

@Composable
fun HomeScreen(vm: DompetViewModel) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(HomeTab.BERANDA) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var formOpenCount by remember { mutableIntStateOf(0) }
    var showLink by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    // jadwalkan sinkron berkala bila konfigurasi tersimpan
    LaunchedEffect(Unit) {
        if (SyncManager.loadConfig(context) != null) SyncWorker.schedulePeriodic(context)
        vm.refreshSyncConfig()
    }

    fun openForm(transaction: Transaction?) {
        editing = transaction
        formOpenCount++
        showForm = true
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon(), contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == HomeTab.BERANDA) {
                FloatingActionButton(onClick = { openForm(null) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Catat transaksi")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                HomeTab.BERANDA -> DashboardTab(
                    vm = vm,
                    onEdit = { openForm(it) },
                    onAdd = { openForm(null) }
                )
                HomeTab.LAPORAN -> ReportTab(vm)
                HomeTab.AKUN -> AccountsTab(
                    vm = vm,
                    onOpenLink = { showLink = true },
                    onOpenImport = { showImport = true }
                )
                HomeTab.PENGATURAN -> SettingsTab(vm)
            }
        }
    }

    if (showForm) {
        key(formOpenCount) {
            TransactionForm(
                existing = editing,
                workspace = vm.workspace.collectAsState().value,
                accounts = vm.accounts.collectAsState().value,
                categories = vm.categories.collectAsState().value,
                onDismiss = { showForm = false },
                onSave = { vm.save(it); showForm = false },
                onDelete = { vm.delete(it); showForm = false }
            )
        }
    }

    if (showLink) {
        LinkScreen(
            onClose = { showLink = false },
            onOpenImport = { showLink = false; showImport = true }
        )
    }

    if (showImport) {
        ImportScreen(
            vm = vm,
            onClose = { showImport = false }
        )
    }
}

// ---------------------------------------------------------------- dashboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSwitcher(
    workspace: Workspace,
    onChange: (Workspace) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SegmentedButton(
            selected = workspace == Workspace.PERSONAL,
            onClick = { onChange(Workspace.PERSONAL) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text("👤 Pribadi") }
        SegmentedButton(
            selected = workspace == Workspace.BUSINESS,
            onClick = { onChange(Workspace.BUSINESS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text("🏢 Bisnis") }
    }
}

@Composable
fun DashboardTab(
    vm: DompetViewModel,
    onEdit: (Transaction) -> Unit,
    onAdd: () -> Unit
) {
    val workspace by vm.workspace.collectAsState()
    val month by vm.month.collectAsState()
    val balance by vm.workspaceBalance.collectAsState()
    val data by vm.monthData.collectAsState()
    val accounts by vm.accounts.collectAsState()

    val accountNames = remember(accounts) { accounts.associate { it.id to it.name } }

    Column(modifier = Modifier.fillMaxSize()) {
        MonthTopBar(
            month = month,
            showToday = month != java.time.YearMonth.now(),
            onPrev = vm::prevMonth,
            onNext = vm::nextMonth,
            onToday = vm::goCurrentMonth
        )
        if (data.transactions.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize()) {
                WorkspaceSwitcher(workspace = workspace, onChange = vm::setWorkspace)
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    workspaceLabel = if (workspace == Workspace.PERSONAL) "pribadi" else "bisnis",
                    onAdd = onAdd
                )
            }
        } else {
            TransactionList(
                modifier = Modifier.weight(1f),
                balance = balance,
                data = data,
                workspace = workspace,
                accountNames = accountNames,
                onEdit = onEdit,
                header = {
                    WorkspaceSwitcher(workspace = workspace, onChange = vm::setWorkspace)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthTopBar(
    month: java.time.YearMonth,
    showToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Bulan sebelumnya")
                }
                Text(
                    text = formatMonthTitle(month),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Bulan berikutnya")
                }
            }
        },
        actions = {
            if (showToday) {
                TextButton(onClick = onToday) { Text("Hari ini") }
            }
        }
    )
}

@Composable
private fun TransactionList(
    modifier: Modifier = Modifier,
    balance: Long,
    data: MonthData,
    workspace: Workspace,
    accountNames: Map<Long, String>,
    onEdit: (Transaction) -> Unit,
    header: @Composable () -> Unit
) {
    val grouped = remember(data.transactions) {
        data.transactions.groupBy { millisToLocalDate(it.dateMillis) }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { header() }
        item {
            SummaryCard(
                balance = balance,
                income = data.totalIncome,
                expense = data.totalExpense,
                workspace = workspace
            )
        }
        if (data.byCategory.isNotEmpty()) {
            item { CategoryCard(stats = data.byCategory, workspace = workspace) }
        }
        grouped.forEach { (date, items) ->
            item(key = "header_${date.toEpochDay()}") {
                DayHeader(date = date, items = items)
            }
            items(items, key = { it.id }) { tx ->
                TransactionRow(
                    transaction = tx,
                    accountNames = accountNames,
                    workspace = workspace,
                    onClick = { onEdit(tx) }
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(balance: Long, income: Long, expense: Long, workspace: Workspace) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (workspace == Workspace.PERSONAL) "Saldo pribadi" else "Saldo usaha",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatRupiah(balance),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Masuk bulan ini",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "+ " + formatRupiah(income),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keluar bulan ini",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "− " + formatRupiah(expense),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(stats: List<CategoryStat>, workspace: Workspace) {
    val maxTotal = stats.maxOf { it.total }.coerceAtLeast(1L)
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Pengeluaran per kategori",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            stats.forEach { stat ->
                val fraction = (stat.total.toFloat() / maxTotal).coerceIn(0.02f, 1f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Text(
                        text = Categories.emojiOf(stat.category, TxType.EXPENSE, workspace),
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stat.category,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatRupiah(stat.total),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DayHeader(date: LocalDate, items: List<Transaction>) {
    val income = items.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val expense = items.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val net = income - expense
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatDayHeader(date),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (net != 0L) {
            Text(
                text = formatRupiahSigned(abs(net), isIncome = net > 0),
                style = MaterialTheme.typography.labelMedium,
                color = amountColor(isIncome = net > 0)
            )
        }
    }
}

@Composable
internal fun TransactionRow(
    transaction: Transaction,
    accountNames: Map<Long, String>,
    workspace: Workspace,
    onClick: () -> Unit
) {
    val isTransfer = transaction.type == TxType.TRANSFER
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = Categories.emojiOf(transaction.category, transaction.type, workspace),
                    fontSize = 20.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rowSubtitle(transaction, accountNames),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isTransfer) "→ " + formatRupiah(transaction.amount)
                else formatRupiahSigned(transaction.amount, transaction.type == TxType.INCOME),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isTransfer) MaterialTheme.colorScheme.onSurface
                else amountColor(transaction.type == TxType.INCOME)
            )
        }
    }
}

internal fun rowSubtitle(
    transaction: Transaction,
    accountNames: Map<Long, String>
): String {
    val account = accountNames[transaction.accountId]
    return when {
        transaction.type == TxType.TRANSFER ->
            "${account ?: "?"} → ${accountNames[transaction.counterAccountId] ?: "?"}"
        transaction.note.isBlank() ->
            listOfNotNull(transaction.category, account).joinToString(" • ")
        else ->
            listOfNotNull(transaction.category, transaction.note, account).joinToString(" • ")
    }
}

@Composable
internal fun amountColor(isIncome: Boolean) =
    if (isSystemInDarkTheme()) {
        if (isIncome) IncomeGreenDark else ExpenseRedDark
    } else {
        if (isIncome) IncomeGreen else ExpenseRed
    }

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    workspaceLabel: String,
    onAdd: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "👛", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Belum ada catatan bulan ini",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Mulai catat transaksi ruang $workspaceLabel\nagar keuangan lebih terkendali.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd) { Text("Catat Transaksi") }
    }
}
