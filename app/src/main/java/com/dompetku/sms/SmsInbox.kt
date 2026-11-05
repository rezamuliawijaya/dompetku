package com.dompetku.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.dompetku.data.AccountEntity
import com.dompetku.data.AccountKind
import com.dompetku.data.AppDatabase
import com.dompetku.data.Transaction
import com.dompetku.data.TxSource
import com.dompetku.data.TxType
import com.dompetku.data.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Menarik transaksi dari kotak masuk SMS: baca SMS dari pengirim bank/e-wallet
 * yang dikenal, parse, lalu simpan sebagai transaksi (dengan deduplikasi).
 */
object SmsInbox {

    data class ImportResult(
        val scanned: Int,
        val matched: Int,
        val imported: Int,
        val skippedDuplicate: Int
    )

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun importFromInbox(context: Context, days: Long = 120): ImportResult =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext ImportResult(0, 0, 0, 0)

            val db = AppDatabase.get(context)
            val dao = db.transactionDao()
            val accountDao = db.accountDao()

            val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC"
            )

            val parsedList = mutableListOf<BankSmsParser.ParsedSms>()
            cursor?.use { c ->
                val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    val address = c.getString(addrIdx) ?: continue
                    val body = c.getString(bodyIdx) ?: continue
                    val date = c.getLong(dateIdx)
                    val parsed = BankSmsParser.parse(address, body, date) ?: continue
                    parsedList.add(parsed)
                }
            }

            var imported = 0
            var dup = 0
            // cache akun per institusi agar tidak query berulang
            val accountCache = mutableMapOf<String, AccountEntity>()

            for (p in parsedList) {
                if (dao.getByExternalKey(p.externalKey) != null) {
                    dup++
                    continue
                }
                val account = accountCache.getOrPut(p.institution) {
                    resolveOrCreateAccount(accountDao, p)
                }
                dao.insert(
                    Transaction(
                        title = "${p.display} • ${p.title}",
                        amount = p.amount,
                        type = if (p.isIncome) TxType.INCOME else TxType.EXPENSE,
                        category = "Lainnya",
                        note = "Otomatis dari SMS",
                        dateMillis = startOfDay(p.receivedMillis),
                        accountId = account.id,
                        workspace = account.workspace,
                        source = TxSource.SMS,
                        externalKey = p.externalKey
                    )
                )
                imported++
            }

            ImportResult(parsedList.size, parsedList.size, imported, dup)
        }

    private suspend fun resolveOrCreateAccount(
        accountDao: com.dompetku.data.AccountDao,
        parsed: BankSmsParser.ParsedSms
    ): AccountEntity {
        // cari akun dengan institusi yang cocok (yang belum arsip, urut pertama)
        accountDao.getAll().firstOrNull {
            it.institution.equals(parsed.institution, ignoreCase = true) && !it.deleted
        }?.let { return it }

        // belum ada: buat akun baru di ruang Pribadi
        val kind = if (parsed.display.contains("Pay", ignoreCase = true) ||
            parsed.display.contains("OVO", ignoreCase = true) ||
            parsed.display.contains("DANA", ignoreCase = true)
        ) AccountKind.EWALLET else AccountKind.BANK
        val account = AccountEntity(
            name = parsed.display,
            kind = kind,
            institution = parsed.institution,
            workspace = Workspace.PERSONAL
        )
        val id = accountDao.insert(account)
        return account.copy(id = id)
    }

    private fun startOfDay(millis: Long): Long =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
