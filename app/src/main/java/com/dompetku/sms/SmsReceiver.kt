package com.dompetku.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dompetku.R
import com.dompetku.data.AccountEntity
import com.dompetku.data.AccountKind
import com.dompetku.data.AppDatabase
import com.dompetku.data.Transaction
import com.dompetku.data.TxSource
import com.dompetku.data.TxType
import com.dompetku.data.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Menangkap SMS masuk secara realtime: bila berasal dari bank/e-wallet yang dikenal
 * dan polanya cocok, transaksi langsung dicatat (bila fitur aktif di Pengaturan).
 */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!SmsPrefs.autoCaptureEnabled(context)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val db = AppDatabase.get(context)

        for (msg in messages) {
            val address = msg.originatingAddress ?: continue
            val body = msg.messageBody ?: continue
            val received = msg.timestampMillis
            val parsed = BankSmsParser.parse(address, body, received) ?: continue

            val pending = goAsync()
            scope.launch {
                try {
                    val dao = db.transactionDao()
                    if (dao.getByExternalKey(parsed.externalKey) != null) return@launch
                    val account = resolveOrCreateAccount(db.accountDao(), parsed)
                    dao.insert(
                        Transaction(
                            title = "${parsed.display} • ${parsed.title}",
                            amount = parsed.amount,
                            type = if (parsed.isIncome) TxType.INCOME else TxType.EXPENSE,
                            category = "Lainnya",
                            note = "Otomatis dari SMS",
                            dateMillis = startOfDay(received),
                            accountId = account.id,
                            workspace = account.workspace,
                            source = TxSource.SMS,
                            externalKey = parsed.externalKey
                        )
                    )
                    if (SmsPrefs.notifEnabled(context)) notify(context, parsed)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private suspend fun resolveOrCreateAccount(
        accountDao: com.dompetku.data.AccountDao,
        parsed: BankSmsParser.ParsedSms
    ): AccountEntity {
        accountDao.getAll().firstOrNull {
            it.institution.equals(parsed.institution, ignoreCase = true) && !it.deleted
        }?.let { return it }

        val platformKind = BankSmsParser.platforms
            .firstOrNull { it.code == parsed.institution }?.kind
        val kind = when (platformKind) {
            "EWALLET" -> AccountKind.EWALLET
            else -> AccountKind.BANK
        }
        val account = AccountEntity(
            name = parsed.display,
            kind = kind,
            institution = parsed.institution,
            workspace = Workspace.PERSONAL
        )
        val id = accountDao.insert(account)
        return account.copy(id = id)
    }

    private fun notify(context: Context, parsed: BankSmsParser.ParsedSms) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "dompetku_capture"
        manager.createNotificationChannel(
            NotificationChannel(
                channelId, "Transaksi otomatis", NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val text = if (parsed.isIncome) "+Rp " else "−Rp "
        val nf = java.text.NumberFormat.getIntegerInstance(java.util.Locale("in", "ID"))
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${parsed.display}: transaksi tercatat")
            .setContentText(text + nf.format(parsed.amount) + " • " + parsed.title)
            .setAutoCancel(true)
            .build()
        manager.notify(parsed.externalKey.hashCode(), notification)
    }

    private fun startOfDay(millis: Long): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** Preferensi fitur penangkapan SMS. */
object SmsPrefs {
    private const val FILE = "dompetku_sms"

    fun autoCaptureEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("auto", true)

    fun setAutoCapture(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean("auto", enabled).apply()
    }

    fun notifEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("notif", true)

    fun setNotif(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean("notif", enabled).apply()
    }
}
