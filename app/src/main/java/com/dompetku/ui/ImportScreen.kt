package com.dompetku.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dompetku.data.AccountEntity
import com.dompetku.util.Csv

/** Layar import mutasi CSV/rekening koran ke akun terpilih. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    vm: DompetViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var accounts by remember { mutableStateOf<List<AccountEntity>>(emptyList()) }
    var accountId by remember { mutableStateOf(0L) }
    var minusIsExpense by remember { mutableStateOf(true) }
    var parsedRows by remember { mutableStateOf<List<Csv.Row>>(emptyList()) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        accounts = vm.allAccounts()
        accountId = accounts.firstOrNull()?.id ?: 0L
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText().orEmpty()
                val rows = Csv.parse(text)
                parsedRows = rows
                fileName = uri.lastPathSegment
                result = if (rows.isEmpty())
                    "Tidak ada baris yang bisa dibaca. Pastikan kolom tanggal & jumlah ada."
                else null
            } catch (t: Throwable) {
                result = "Gagal membaca file: ${t.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Mutasi CSV", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // pilih akun
            DropdownPickerPublic(
                label = "Masukkan ke akun",
                display = accounts.firstOrNull { it.id == accountId }?.name
                    ?: if (accounts.isEmpty()) "Belum ada akun" else "Pilih akun",
                options = accounts.map { it.name },
                onSelect = { index -> accounts.getOrNull(index)?.let { accountId = it.id } }
            )

            // arah minus
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Interpretasi angka minus",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { minusIsExpense = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (minusIsExpense) "✓ Minus = Pengeluaran"
                                else "Minus = Pengeluaran"
                            )
                        }
                        OutlinedButton(
                            onClick = { minusIsExpense = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (!minusIsExpense) "✓ Minus = Pemasukan"
                                else "Minus = Pemasukan"
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Pilih File CSV") }

            fileName?.let {
                Text(
                    text = "File: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (parsedRows.isNotEmpty()) {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Pratinjau ${parsedRows.size} baris terbaca",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        parsedRows.take(5).forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = row.title.take(24),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatDateShort(row.dateMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = formatRupiahSigned(
                                        row.amount,
                                        isIncome = row.isExpense != minusIsExpense
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (parsedRows.size > 5) {
                            Text(
                                "… dan ${parsedRows.size - 5} baris lainnya",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val rows = parsedRows.map { row ->
                            if (minusIsExpense) row
                            else row.copy(isExpense = !row.isExpense)
                        }
                        vm.importCsv(rows, accountId) { result = it }
                    },
                    enabled = accountId != 0L,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("Import ${parsedRows.size} Transaksi") }
            }

            result?.let {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/** Versi publik dari dropdown pemilih (dipakai lintas layar). */
@Composable
internal fun DropdownPickerPublic(
    label: String,
    display: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.layout.Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = display,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, option ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
