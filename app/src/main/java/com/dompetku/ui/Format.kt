package com.dompetku.ui

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.text.NumberFormat

private val idLocale = Locale("in", "ID")

private val rupiahFormat: NumberFormat =
    NumberFormat.getIntegerInstance(idLocale).apply { isGroupingUsed = true }

private val dayHeaderFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", idLocale)

private val dayOfMonthFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", idLocale)

private val csvDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy", idLocale)

private val monthTitleFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", idLocale)

/** "Rp 1.250.000" */
fun formatRupiah(amount: Long): String = "Rp " + rupiahFormat.format(amount)

/** Format dengan tanda: pemasukan "+Rp …", pengeluaran "−Rp …" */
fun formatRupiahSigned(amount: Long, isIncome: Boolean): String =
    (if (isIncome) "+Rp " else "−Rp ") + rupiahFormat.format(amount)

/** Hanya digit dari input pengguna */
fun parseAmount(text: String): Long =
    text.filter { it.isDigit() }.toLongOrNull() ?: 0L

/** Kelompokkan digit ribuan: "1250000" -> "1.250.000" */
fun formatAmountInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    return rupiahFormat.format(digits.toLong())
}

fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** Header daftar: "Hari ini", "Kemarin", atau "Senin, 3 Nov 2025" */
fun formatDayHeader(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Hari ini"
        today.minusDays(1) -> "Kemarin"
        else -> date.format(dayHeaderFormat)
    }
}

fun formatDateShort(millis: Long): String =
    millisToLocalDate(millis).format(dayOfMonthFormat)

fun formatDateCsv(millis: Long): String =
    millisToLocalDate(millis).format(csvDateFormat)

fun formatMonthTitle(month: YearMonth): String = month.format(monthTitleFormat)
