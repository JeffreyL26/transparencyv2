package com.jbateam.scanconvert.domain

import com.jbateam.scanconvert.data.CurrencyMeta
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Umrechnung & Formatierung (Handoff §9).
 * Alle Kurse sind relativ zu EUR.
 */

/** convert(amt, from, to) = amt / rate[from] * rate[to] */
fun convert(amt: Double, from: String, to: String, rates: Map<String, Double>): Double {
    val rf = rates[from] ?: return 0.0
    val rt = rates[to] ?: return 0.0
    if (rf == 0.0) return 0.0
    return amt / rf * rt
}

/** rate(from, to) = rate[to] / rate[from] */
fun rateOf(from: String, to: String, rates: Map<String, Double>): Double {
    val rf = rates[from] ?: return 0.0
    val rt = rates[to] ?: return 0.0
    if (rf == 0.0) return 0.0
    return rt / rf
}

private val deSymbols = DecimalFormatSymbols(Locale.GERMANY)

private fun numberFormat(digits: Int): DecimalFormat =
    DecimalFormat("#,##0", deSymbols).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }

/** Betrag ohne Symbol, Locale de-DE — z. B. "27,02" / JPY "2.700". */
fun fmtNum(value: Double, code: String): String =
    numberFormat(CurrencyMeta.fractionDigits(code)).format(value)

/**
 * Betrag mit Währungssymbol, Locale de-DE — z. B. "24,90 €", "27,02 $".
 * (Symbol nach dem Betrag, ICU-de-DE-Symbol wie Intl.NumberFormat der Referenz: CHF → "CHF".)
 */
fun fmt(value: Double, code: String): String =
    fmtNum(value, code) + " " + CurrencyMeta.displaySym(code)

/** Kurs mit 4 Nachkommastellen, Locale de-DE — z. B. "1,0850". */
fun fmtRate(from: String, to: String, rates: Map<String, Double>): String =
    numberFormat(4).format(rateOf(from, to, rates))

/**
 * Historischer Kurs einer erfassten Position: aus dem zum Scan-Zeitpunkt fixierten
 * Wert abgeleitet (`value/raw`), unabhängig vom aktuellen Live-Kurs. 0, falls `raw` 0 ist.
 */
fun histRate(raw: Double, value: Double): Double = if (raw > 0.0) value / raw else 0.0

/**
 * Lesbarer „Kurs zum Zeitpunkt" einer Position — z. B. „1 EUR = 1,1438 USD".
 * Quelle ist der fixierte Wert der Position, nicht der aktuelle Kurs (§7).
 */
fun fmtHistRate(from: String, currency: String, raw: Double, value: Double): String =
    "1 $from = " + numberFormat(4).format(histRate(raw, value)) + " " + currency

/** Datum + Uhrzeit einer Erfassung, Locale de-DE — z. B. „23.06.2026, 14:32". */
fun fmtDateTime(ts: Long): String =
    SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY).format(Date(ts))

/**
 * Editierbarer Roh-String OHNE Tausender-Trennzeichen (de-DE Komma) — z. B. "27,02"
 * bzw. JPY "2700". Für das händische Anpassen einer falsch erkannten Zahl, damit das
 * Eingabefeld einen sauber editierbaren Wert ohne „1.234"-Gruppierung zeigt.
 */
fun fmtPlain(value: Double, code: String): String {
    val digits = CurrencyMeta.fractionDigits(code)
    return DecimalFormat("0", deSymbols).apply {
        isGroupingUsed = false
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)
}

/**
 * Parst eine händisch eingegebene Betrags-Eingabe robust zu einem Double ≥ 0.
 * Behandelt de-DE („1.234,56") wie US („1,234.56" / „27.02") und reine Komma-/
 * Punkt-Dezimalstellen; leere/ungültige Eingaben → null.
 */
fun parseAmount(text: String): Double? {
    val t = text.trim().replace(" ", "").replace(" ", "")
    if (t.isEmpty()) return null
    val hasComma = t.contains(',')
    val hasDot = t.contains('.')
    val normalized = when {
        // Beide vorhanden: das ZULETZT stehende Zeichen ist das Dezimaltrennzeichen.
        hasComma && hasDot ->
            if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.')
            else t.replace(",", "")
        hasComma -> t.replace(',', '.')
        else -> t
    }
    return normalized.toDoubleOrNull()?.takeIf { it >= 0.0 }
}
