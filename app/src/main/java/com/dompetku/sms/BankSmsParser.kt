package com.dompetku.sms

/**
 * Parser notifikasi SMS bank & e-wallet Indonesia.
 * Hanya SMS yang cocok dengan pola jumlah + arah yang jelas yang dianggap transaksi;
 * SMS lain diabaikan dengan aman.
 */
object BankSmsParser {

    data class Platform(
        val code: String,          // kode institusi untuk pencocokan pengirim
        val display: String,       // nama tampilan
        val kind: String           // BANK | EWALLET
    )

    val platforms = listOf(
        Platform("BCA", "BCA", "BANK"),
        Platform("MANDIRI", "Mandiri", "BANK"),
        Platform("BRI", "BRI", "BANK"),
        Platform("BNI", "BNI", "BANK"),
        Platform("CIMB", "CIMB Niaga", "BANK"),
        Platform("SEABANK", "SeaBank", "BANK"),
        Platform("JENIUS", "Jenius", "BANK"),
        Platform("BSI", "Bank Syariah Indonesia", "BANK"),
        Platform("GOPAY", "GoPay", "EWALLET"),
        Platform("OVO", "OVO", "EWALLET"),
        Platform("DANA", "DANA", "EWALLET"),
        Platform("SHOPEEPAY", "ShopeePay", "EWALLET"),
        Platform("SHOPEE", "ShopeePay", "EWALLET"),
        Platform("LINKAJA", "LinkAja", "EWALLET"),
    )

    data class ParsedSms(
        val institution: String,   // kode platform: BCA, GOPAY, dst.
        val display: String,
        val amount: Long,
        val isIncome: Boolean,     // dana masuk ke rekening/dompet
        val title: String,         // ringkasan singkat
        val receivedMillis: Long,
        val raw: String
    ) {
        /** Kunci deduplikasi: institusi + jumlah + menit waktu + potongan isi SMS */
        val externalKey: String
            get() = "$institution|$amount|${receivedMillis / 60_000}|${raw.take(48).hashCode()}"
    }

    private val amountRegex =
        Regex("(?:Rp|IDR)[\\s.]*([\\d.,]+)", RegexOption.IGNORE_CASE)

    // kata kunci dana keluar (diperiksa dulu — notifikasi pengeluaran lebih umum)
    private val outKeywords = listOf(
        "pembelian", "belanja", "transfer ke", "kirim ke", "tarik tunai", "bayar",
        "pembayaran", "debet", "debit ", "kamu keluar", "kamu melakukan pembayaran",
        "topup", "top up", "isi ulang", "purchase", "paid to", "qr"
    )

    private val inKeywords = listOf(
        "kredit", "transfer masuk", "dari", "diterima", "menerima", "menerima dana",
        "penambahan", "masuk", "refund", "cashback", "cash in", "top up berhasil"
    )

    fun matchSender(address: String): Platform? {
        val a = address.uppercase()
        return platforms.firstOrNull { a.contains(it.code) }
    }

    /** Ambil angka rupiah pertama yang valid dari teks. */
    private fun parseAmount(text: String): Long? {
        val m = amountRegex.find(text) ?: return null
        // gabungkan digit; titik/koma adalah pemisah ribuan pada format Indonesia
        val digits = m.groupValues[1].filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val value = digits.toLongOrNull() ?: return null
        return if (value > 0) value else null
    }

    fun parse(address: String, body: String, receivedMillis: Long): ParsedSms? {
        val platform = matchSender(address) ?: return null
        val amount = parseAmount(body) ?: return null
        val lower = body.lowercase()

        val out = outKeywords.any { lower.contains(it) }
        val inc = inKeywords.any { lower.contains(it) }
        // pola "Rp-50.000" / "-Rp50.000" juga menandakan keluar
        val negativeSign = Regex("Rp\\s*-|\\-\\s*Rp").containsMatchIn(body)
        val isIncome = when {
            negativeSign -> false
            out && !inc -> false
            inc && !out -> true
            // dua-duanya atau tidak ada: cek pola saldo turun vs naik tidak reliable — tolak
            else -> return null
        }

        val title = buildTitle(body)
        if (title.isBlank()) return null

        return ParsedSms(
            institution = platform.code,
            display = platform.display,
            amount = amount,
            isIncome = isIncome,
            title = title,
            receivedMillis = receivedMillis,
            raw = body
        )
    }

    /** Ambil potongan deskripsi: buang bagian saldo dan nomor rekening panjang. */
    private fun buildTitle(body: String): String {
        var t = body
            .replace(Regex("saldo\\s*(?:Rp|IDR)?[\\s.]*[\\d.,]+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?:Rp|IDR)[\\s.]*[\\d.,]+", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\d{6,}"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        // buang awalan platform "BCA:" dan tanggal pendek
        t = t.replace(Regex("^[A-Za-z]+\\s*:\\s*"), "")
        t = t.replace(Regex("^\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\s*"), "")
        t = t.trim(' ', '-', '.', ',', ':')
        return t.take(40).ifBlank { "Transaksi otomatis" }
    }
}
