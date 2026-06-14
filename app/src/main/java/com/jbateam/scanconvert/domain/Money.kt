package com.jbateam.scanconvert.domain

import com.jbateam.scanconvert.data.CurrencyMeta
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
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
