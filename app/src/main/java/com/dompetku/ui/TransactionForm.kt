package com.dompetku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dompetku.data.AccountEntity
import com.dompetku.data.CategoryEntity
import com.dompetku.data.Seed
import com.dompetku.data.Transaction
import com.dompetku.data.TxType
import com.dompetku.data.Workspace
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionForm(
    existing: Transaction?,
    workspace: Workspace,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit
) {
    var type by remember { mutableStateOf(existing?.type ?: TxType.EXPENSE) }
    var amountText by remember {
        mutableStateOf(existing?.let { formatAmountInput(it.amount.toString()) } ?: "")
    }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var date by remember {
        mutableStateOf(existing?.let { millisToLocalDate(it.dateMillis) } ?: LocalDate.now())
    }
    var accountId by remember { mutableStateOf(existing?.accountId ?: 0L) }
    var counterAccountId by remember { mutableStateOf(existing?.counterAccountId ?: 0L) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // kategori aktif mengikuti tipe; untuk transfer kategori tidak dipakai
    val typeCategories = remember(categories, type) { categories.filter { it.type == type } }
    var categoryName by remember {
        mutableStateOf(existing?.category ?: "")
    }
    LaunchedEffect(typeCategories) {
        if (type != TxType.TRANSFER &&
            typeCategories.firstOrNull { it.name == categoryName } == null
        ) {
            categoryName = typeCategories.firstOrNull()?.name ?: "Lainnya"
        }
    }
    LaunchedEffect(accounts) {
        if (accountId == 0L) accountId = accounts.firstOrNull()?.id ?: 0L
    }

    val isTransfer = type == TxType.TRANSFER
    val amount = parseAmount(amountText)
    val valid = when {
        amount <= 0L -> false
        isTransfer -> accountId != 0L && counterAccountId != 0L && accountId != counterAccountId
        else -> title.isNotBlank() && accountId != 0L
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existing == null) "Catat Transaksi" else "Ubah Transaksi",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Hapus",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == TxType.EXPENSE,
                    onClick = { type = TxType.EXPENSE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Text("Keluar") }
                SegmentedButton(
                    selected = type == TxType.INCOME,
                    onClick = { type = TxType.INCOME },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Text("Masuk") }
                SegmentedButton(
                    selected = type == TxType.TRANSFER,
                    onClick = { type = TxType.TRANSFER },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Text("Transfer") }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = formatAmountInput(it) },
                label = { Text("Jumlah") },
                prefix = { Text("Rp ") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            if (isTransfer) {
                DropdownPicker(
                    label = "Dari akun",
                    display = accounts.firstOrNull { it.id == accountId }?.name ?: "Pilih akun",
                    options = accounts.filter { it.id != counterAccountId }.map { it.name },
                    onSelect = { index ->
                        accounts.filter { it.id != counterAccountId }
                            .getOrNull(index)?.let { accountId = it.id }
                    }
                )
                DropdownPicker(
                    label = "Ke akun",
                    display = accounts.firstOrNull { it.id == counterAccountId }?.name
                        ?: "Pilih akun",
                    options = accounts.filter { it.id != accountId }.map { it.name },
                    onSelect = { index ->
                        accounts.filter { it.id != accountId }
                            .getOrNull(index)?.let { counterAccountId = it.id }
                    }
                )
            } else {
                DropdownPicker(
                    label = "Akun / sumber dana",
                    display = accounts.firstOrNull { it.id == accountId }?.name ?: "Pilih akun",
                    options = accounts.map { it.name },
                    onSelect = { index ->
                        accounts.getOrNull(index)?.let { accountId = it.id }
                    }
                )

                Column {
                    Text(
                        text = "Kategori",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        typeCategories.forEach { cat ->
                            FilterChip(
                                selected = categoryName == cat.name,
                                onClick = { categoryName = cat.name },
                                label = { Text("${cat.emoji} ${cat.name}") },
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(if (isTransfer) "Keterangan (opsional)" else "Keterangan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDateShort(
                        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    )
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Catatan (opsional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSave(
                        Transaction(
                            id = existing?.id ?: 0L,
                            syncId = existing?.syncId ?: newSyncId(),
                            title = if (isTransfer && title.isBlank())
                                "Transfer antar akun" else title.trim(),
                            amount = amount,
                            type = type,
                            category = if (isTransfer) Seed.TRANSFER_CATEGORY else categoryName,
                            note = note.trim(),
                            dateMillis = date.atStartOfDay(ZoneId.systemDefault())
                                .toInstant().toEpochMilli(),
                            accountId = accountId,
                            counterAccountId = if (isTransfer) counterAccountId else null,
                            workspace = workspace,
                            source = existing?.source
                                ?: com.dompetku.data.TxSource.MANUAL,
                            externalKey = existing?.externalKey
                        )
                    )
                },
                enabled = valid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (existing == null) "Simpan" else "Simpan Perubahan",
                    fontSize = 16.sp
                )
            }

            if (existing != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text("Hapus Transaksi", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date
                .atTime(12, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { sel ->
                        date = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDeleteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus transaksi?") },
            text = { Text("\u201C${existing.title}\u201D akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = { onDelete(existing) }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") }
            }
        )
    }
}

/** Dropdown sederhana berbasis tombol + menu (tanpa API eksperimental). */
@Composable
private fun DropdownPicker(
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
        Box {
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
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
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

private fun newSyncId(): String = java.util.UUID.randomUUID().toString()
