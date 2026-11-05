package com.dompetku.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Ekspor & parse CSV mutasi (dipakai untuk ekspor laporan dan import rekening koran). */
object Csv {

    private val zone: ZoneId = ZoneId.systemDefault()

    // ---- ekspor ----

    fun writeExport(context: Context, fileName: String, rows: List<List<String>>): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        // BOM agar Excel mengenali UTF-8
        file.writeText("\uFEFF")
        file.appendText(rows.joinToString("\r\n") { row ->
            row.joinToString(";") { cell -> "\"" + cell.replace("\"", "\"\"") + "\"" }
        })
        return FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
    }

    // ---- import ----

    data class Row(
        val dateMillis: Long,
        val title: String,
        val amount: Long,
        val isExpense: Boolean
    )

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
    )

    private fun parseDate(text: String): Long? {
        val t = text.trim().trim('"')
        for (fmt in dateFormats) {
            try {
                return LocalDate.parse(t, fmt).atStartOfDay(zone).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun parseAmount(text: String): Long? {
        var t = text.trim()
        val negative = t.contains("-") || t.contains("(")
        t = t.replace(Regex("[^\\d]"), "")
        if (t.isEmpty()) return null
        val value = t.toLongOrNull() ?: return null
        if (value == 0L) return null
        return if (negative) -value else value
    }

    /**
     * Parse isi file CSV mutasi secara toleran: deteksi pemisah (; atau ,),
     * cari kolom tanggal, jumlah, dan deskripsi. Baris tanpa tanggal+jumlah
     * (mis. header) dilewati otomatis.
     */
    fun parse(text: String): List<Row> {
        val rows = mutableListOf<Row>()
        val lines = text.lines().filter { it.isNotBlank() }
        val sepCount = lines.firstOrNull()?.count { it == ';' } ?: 0
        val separator = if (sepCount >= 1) ";" else ","
        for (line in lines) {
            val cells = line.split(separator).map { it.trim().trim('"') }
            if (cells.size < 2) continue
            val date = cells.mapNotNull { parseDate(it) }.firstOrNull() ?: continue
            // jumlah: sel bukan tanggal yang bernilai numerik
            val amount = cells.filter { parseDate(it) == null }
                .mapNotNull { parseAmount(it) }
                .firstOrNull() ?: continue
            val title = cells.filter {
                parseDate(it) == null && parseAmount(it) == null && it.isNotBlank()
            }.maxByOrNull { it.length } ?: "Mutasi"
            rows.add(
                Row(
                    dateMillis = date,
                    title = title.take(60),
                    amount = kotlin.math.abs(amount),
                    isExpense = amount < 0
                )
            )
        }
        return rows
    }

    fun dedupKey(row: Row): String =
        "csv|${row.dateMillis}|${row.amount}|${row.title.hashCode()}"
}
