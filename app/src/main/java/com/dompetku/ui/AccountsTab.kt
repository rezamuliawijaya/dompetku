package com.dompetku.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dompetku.data.AccountKind
import com.dompetku.data.Workspace

@Composable
fun AccountsTab(
    vm: DompetViewModel,
    onOpenLink: () -> Unit,
    onOpenImport: () -> Unit
) {
    val workspace by vm.workspace.collectAsState()
    val accountsWithBalance by vm.accountsWithBalance.collectAsState()
    val balance by vm.workspaceBalance.collectAsState()

    var showAddAccount by remember { mutableStateOf(false) }
    var smsResult by remember { mutableStateOf<String?>(null) }

    val smsPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            vm.importSms { smsResult = it }
        } else {
            smsResult = "Izin SMS ditolak. Aktifkan izin di pengaturan sistem untuk fitur ini."
        }
    }

    fun requestSmsAndImport() {
        val wanted = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        smsPermissions.launch(wanted.toTypedArray())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Akun & Dompet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        item { WorkspaceSwitcher(workspace = workspace, onChange = vm::setWorkspace) }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (workspace == Workspace.PERSONAL) "Total saldo pribadi"
                        else "Total saldo usaha",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRupiah(balance),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        items(accountsWithBalance, key = { it.account.id }) { item ->
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(
                        imageVector = when (item.account.kind) {
                            AccountKind.KAS -> Icons.Rounded.AccountBalanceWallet
                            AccountKind.BANK -> Icons.Rounded.AccountBalance
                            AccountKind.EWALLET -> Icons.Rounded.QrCode2
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.account.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when (item.account.kind) {
                                AccountKind.KAS -> "Kas tunai"
                                AccountKind.BANK -> "Bank" +
                                    (item.account.institution.takeIf { it.isNotBlank() }
                                        ?.let { " • $it" } ?: "")
                                AccountKind.EWALLET -> "E-Wallet" +
                                    (item.account.institution.takeIf { it.isNotBlank() }
                                        ?.let { " • $it" } ?: "")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatRupiah(item.balance),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = amountColor(item.balance >= 0)
                    )
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = { showAddAccount = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Tambah Akun")
            }
        }

        item {
            Text(
                text = "Otomatisasi",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            ActionCard(
                title = "Hubungkan Platform Bank / E-Wallet",
                subtitle = "BCA, Mandiri, BRI, GoPay, OVO, DANA, dan lainnya",
                icon = { Icon(Icons.Rounded.AccountBalance, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = onOpenLink
            )
        }
        item {
            ActionCard(
                title = "Tarik Transaksi dari SMS",
                subtitle = "Pindai notifikasi bank & e-wallet di perangkat ini",
                icon = { Icon(Icons.Rounded.Sms, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { requestSmsAndImport() }
            )
        }
        item {
            ActionCard(
                title = "Import Mutasi CSV / Rekening Koran",
                subtitle = "Impor transaksi dari file ekspor bank",
                icon = { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = onOpenImport
            )
        }
    }

    if (showAddAccount) {
        AddAccountDialog(
            workspace = workspace,
            onDismiss = { showAddAccount = false },
            onAdd = { name, kind, institution, initial ->
                vm.addAccount(name, kind, institution, initial, workspace)
                showAddAccount = false
            }
        )
    }

    smsResult?.let { result ->
        AlertDialog(
            onDismissRequest = { smsResult = null },
            title = { Text("Hasil pindai SMS") },
            text = { Text(result) },
            confirmButton = {
                TextButton(onClick = { smsResult = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onClick) { Text("Buka") }
        }
    }
}

@Composable
private fun AddAccountDialog(
    workspace: Workspace,
    onDismiss: () -> Unit,
    onAdd: (String, AccountKind, String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AccountKind.KAS) }
    var institution by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }
    var kindExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Akun") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nama akun") }, singleLine = true
                )
                Box {
                    OutlinedButton(
                        onClick = { kindExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when (kind) {
                                AccountKind.KAS -> "Jenis: Kas tunai"
                                AccountKind.BANK -> "Jenis: Bank"
                                AccountKind.EWALLET -> "Jenis: E-Wallet"
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Rounded.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = kindExpanded, onDismissRequest = { kindExpanded = false }) {
                        AccountKind.entries.forEach { k ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (k) {
                                            AccountKind.KAS -> "Kas tunai"
                                            AccountKind.BANK -> "Bank"
                                            AccountKind.EWALLET -> "E-Wallet"
                                        }
                                    )
                                },
                                onClick = { kind = k; kindExpanded = false }
                            )
                        }
                    }
                }
                if (kind != AccountKind.KAS) {
                    OutlinedTextField(
                        value = institution, onValueChange = { institution = it },
                        label = { Text("Kode platform (BCA, GOPAY, …)") }, singleLine = true
                    )
                }
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = formatAmountInput(it) },
                    label = { Text("Saldo awal") },
                    prefix = { Text("Rp ") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(name, kind, institution, parseAmount(balanceText))
                },
                enabled = name.isNotBlank()
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
