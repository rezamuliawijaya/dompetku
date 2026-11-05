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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dompetku.sms.SmsPrefs
import com.dompetku.sync.SyncManager

@Composable
fun SettingsTab(vm: DompetViewModel) {
    val context = LocalContext.current
    val syncStatus by vm.syncStatus.collectAsState()

    var serverUrl by remember {
        mutableStateOf(SyncManager.loadConfig(context)?.serverUrl ?: "http://10.0.2.2:8080")
    }
    var token by remember { mutableStateOf(SyncManager.loadConfig(context)?.token ?: "") }
    var smsAuto by remember {
        mutableStateOf(SmsPrefs.autoCaptureEnabled(context))
    }
    var smsNotif by remember { mutableStateOf(SmsPrefs.notifEnabled(context)) }

    val smsPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) { vm.refreshSyncConfig() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Pengaturan",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        // ---------- sinkronisasi online ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "🌐 Sinkronisasi Online",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Data tersinkron antar perangkat lewat server DompetKu. " +
                        "Satu token = satu ruang data; bagikan token yang sama ke tim perusahaan Anda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Alamat server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token ruang sinkron") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.createSyncRoom(serverUrl) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Buat Ruang Baru") }
                    OutlinedButton(
                        onClick = { vm.saveSyncConfig(serverUrl, token) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Simpan Token") }
                }
                Button(
                    onClick = { vm.syncNow() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sinkron Sekarang") }
                syncStatus?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ---------- SMS ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "💬 Transaksi Otomatis dari SMS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                SettingSwitch(
                    title = "Catat otomatis dari notifikasi bank",
                    subtitle = "SMS dari BCA, Mandiri, GoPay, DANA, dll. langsung jadi transaksi",
                    checked = smsAuto
                ) { checked ->
                    if (checked) {
                        val wanted = buildList {
                            add(Manifest.permission.RECEIVE_SMS)
                            add(Manifest.permission.READ_SMS)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        smsPermissions.launch(wanted.toTypedArray())
                    }
                    SmsPrefs.setAutoCapture(context, checked)
                    smsAuto = checked
                }
                SettingSwitch(
                    title = "Notifikasi saat transaksi tercatat",
                    subtitle = "Tampilkan pemberitahuan singkat",
                    checked = smsNotif
                ) { checked ->
                    SmsPrefs.setNotif(context, checked)
                    smsNotif = checked
                }
            }
        }

        // ---------- tentang ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tentang",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "DompetKu Pro v2.0 — pencatat keuangan pribadi & bisnis.\n" +
                        "100% data di perangkat + sinkronisasi opsional ke server milik sendiri.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontSize = 15.sp)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
