package com.dompetku.ui

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sms
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dompetku.sms.BankSmsParser
import com.dompetku.sms.SmsPrefs

/**
 * Pusat "Hubungkan Platform": penjelasan metode koneksi + daftar platform
 * yang didukung via auto-capture SMS dan import mutasi CSV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkScreen(
    onClose: () -> Unit,
    onOpenImport: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val smsPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun requestSmsPermissions() {
        val wanted = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        smsPermissions.launch(wanted.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hubungkan Platform", fontWeight = FontWeight.SemiBold) },
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
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cara menghubungkan akun",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "1️⃣  SMS otomatis — transaksi dari notifikasi SMS bank/e-wallet " +
                            "langsung tercatat, tanpa internet.\n" +
                            "2️⃣  Import mutasi CSV — unggah rekening koran dari m-banking.\n" +
                            "3️⃣  API resmi — untuk tarik mutasi langsung butuh kemitraan " +
                            "agregator (mis. Brick, Ayoconnect); kerangka konektornya sudah siap " +
                            "disambungkan begitu Anda memiliki kredensial API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    requestSmsPermissions()
                    SmsPrefs.setAutoCapture(context, true)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Rounded.Sms, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Aktifkan SMS Otomatis")
            }

            OutlinedButton(
                onClick = onOpenImport,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Import Mutasi CSV")
            }

            Text(
                text = "Platform yang didukung",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(((BankSmsParser.platforms.size / 2 + 1) * 72).dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(BankSmsParser.platforms) { platform ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = if (platform.kind == "BANK") "🏦" else "📱",
                                fontSize = 22.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = platform.display,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "SMS • CSV",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Catatan: auto-capture hanya membaca SMS dari pengirim bank/e-wallet " +
                    "di atas dan hanya mencatat SMS dengan pola nominal & arah yang jelas. " +
                    "Data tidak pernah dikirim ke mana pun tanpa Anda mengaktifkan sinkronisasi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
