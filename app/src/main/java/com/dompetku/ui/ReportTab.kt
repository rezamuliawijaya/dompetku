package com.dompetku.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.dompetku.data.Categories
import com.dompetku.data.TxType
import com.dompetku.data.Workspace
import com.dompetku.ui.theme.ExpenseRed
import com.dompetku.ui.theme.IncomeGreen
import java.time.YearMonth

@Composable
fun ReportTab(vm: DompetViewModel) {
    val context = LocalContext.current
    val workspace by vm.workspace.collectAsState()
    val month by vm.month.collectAsState()
    val data by vm.monthData.collectAsState()

    val incomeByCategory = remember(data.transactions) {
        data.transactions.filter { it.type == TxType.INCOME }
            .groupBy { it.category }
            .map { (cat, items) -> CategoryStat(cat, items.sumOf { it.amount }) }
            .sortedByDescending { it.total }
    }
    val profit = data.totalIncome - data.totalExpense

    var exportHint by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MonthNavRow(month = month, onPrev = vm::prevMonth, onNext = vm::nextMonth,
            onToday = vm::goCurrentMonth)
        WorkspaceSwitcher(workspace = workspace, onChange = vm::setWorkspace)

        // laba / rugi
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Laba / Rugi bulan ini",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = (if (profit >= 0) "+ " else "− ") + formatRupiah(abs(profit)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (profit >= 0) amountColor(true) else amountColor(false)
                )
                Spacer(Modifier.height(12.dp))
                ReportLine("Pemasukan", data.totalIncome, IncomeGreen, ExpenseRed)
                ReportLine("Pengeluaran", data.totalExpense, IncomeGreen, ExpenseRed)
            }
        }

        if (incomeByCategory.isNotEmpty()) {
            CategoryBreakdown(
                title = "Pemasukan per kategori",
                stats = incomeByCategory,
                workspace = workspace,
                barColor = amountColor(true),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (data.byCategory.isNotEmpty()) {
            CategoryBreakdown(
                title = "Pengeluaran per kategori",
                stats = data.byCategory,
                workspace = workspace,
                barColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Button(
            onClick = {
                vm.exportCsv(month, workspace) { uri ->
                    if (uri != null) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(send, "Ekspor laporan CSV")
                        )
                        exportHint = null
                    } else {
                        exportHint = "Ekspor gagal — lihat status di Pengaturan"
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(50.dp)
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Ekspor Laporan CSV")
        }
        exportHint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun MonthNavRow(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onToday) { Text("Hari ini") }
    }
}

@Composable
private fun ReportLine(
    label: String,
    amount: Long,
    incomeColor: Color,
    expenseColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = formatRupiah(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CategoryBreakdown(
    title: String,
    stats: List<CategoryStat>,
    workspace: Workspace,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val maxTotal = stats.maxOf { it.total }.coerceAtLeast(1L)
    Card(shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
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
                            Text(stat.category, style = MaterialTheme.typography.bodyMedium)
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
                                    .background(barColor)
                            )
                        }
                    }
                }
            }
        }
    }
}
