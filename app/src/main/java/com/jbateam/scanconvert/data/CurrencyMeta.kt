package com.jbateam.scanconvert.data

import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Anzeige-Metadaten je Währung: deutscher Name, Symbol, Flaggen-Ländercode.
 *
 * Die 16 Demo-Währungen aus dem Handoff (§6) sind exakt nach Spezifikation
 * überschrieben; alle weiteren Codes der Live-API werden über ICU
 * (java.util.Currency, Locale de) aufgelöst. Nicht-ISO-Codes der API
 * (CNH, GGP, IMP, JEP, FOK, KID, TVD, XCG, ZWG …) haben eigene Einträge.
 */
data class CurrencyInfo(
    val code: String,
    val name: String,
    val sym: String,
    /** ISO-3166-Code der Flagge in assets/flags/{cc}.png — null = kein Flaggenbild (Symbol-Fallback). */
    val cc: String?,
)

object CurrencyMeta {

    /** Standard-Pins (Fallback, falls Onboarding übersprungen). */
    val DEFAULT_PINNED = listOf("EUR", "USD", "GBP", "CHF")

    /**
     * Aus der App ausgeblendete Codes: außer Kurs (SLL → SLE, ZWL → ZWG) bzw.
     * keine echte Währung (XDR = IWF-Sonderziehungsrecht). Werden aus allen
     * Auswahl-Listen gefiltert.
     */
    val HIDDEN = setOf("SLL", "ZWL", "XDR")

    /** Namen exakt nach Handoff-Tabelle §6 + Nicht-ISO-Codes. */
    private val nameOverrides = mapOf(
        "EUR" to "Euro",
        "USD" to "US-Dollar",
        "GBP" to "Brit. Pfund",
        "CHF" to "Schw. Franken",
        "JPY" to "Japan. Yen",
        "AUD" to "Austral. Dollar",
        "CAD" to "Kanad. Dollar",
        "CNY" to "Renminbi",
        "INR" to "Ind. Rupie",
        "BRL" to "Brasil. Real",
        "SEK" to "Schwed. Krone",
        "NOK" to "Norweg. Krone",
        "MXN" to "Mexik. Peso",
        "ZAR" to "Südafr. Rand",
        "SGD" to "Singapur-Dollar",
        "HKD" to "Hongkong-Dollar",
        // Nicht-ISO- bzw. von ICU nicht abgedeckte Codes der Live-API
        "CNH" to "Renminbi (Offshore)",
        "CLF" to "Unidad de Fomento",
        "FOK" to "Färöer-Krone",
        "GGP" to "Guernsey-Pfund",
        "IMP" to "Isle-of-Man-Pfund",
        "JEP" to "Jersey-Pfund",
        "KID" to "Kiribati-Dollar",
        "TVD" to "Tuvalu-Dollar",
        "XCG" to "Karib. Gulden",
        "ZWG" to "Simbabwe-Gold",
        "XAF" to "CFA-Franc (BEAC)",
        "XOF" to "CFA-Franc (BCEAO)",
        "XPF" to "CFP-Franc",
        "XCD" to "Ostkarib. Dollar",
    )

    /** Symbole exakt nach Handoff-Tabelle §6 + sinnvolle Symbole für Sondercodes. */
    private val symOverrides = mapOf(
        "EUR" to "€",
        "USD" to "$",
        "GBP" to "£",
        "CHF" to "Fr",
        "JPY" to "¥",
        "AUD" to "$",
        "CAD" to "$",
        "CNY" to "¥",
        "INR" to "₹",
        "BRL" to "R$",
        "SEK" to "kr",
        "NOK" to "kr",
        "MXN" to "$",
        "ZAR" to "R",
        "SGD" to "$",
        "HKD" to "HK$",
        "CNH" to "¥",
        "CLF" to "UF",
        "FOK" to "kr",
        "GGP" to "£",
        "IMP" to "£",
        "JEP" to "£",
        "KID" to "$",
        "TVD" to "$",
        "XCG" to "ƒ",
        "ZWG" to "ZiG",
        "XAF" to "FCFA",
        "XOF" to "CFA",
        "XPF" to "₣",
        "XCD" to "$",
    )

    /** Flaggen-Ländercode: Sonderfälle; sonst die ersten zwei Buchstaben des ISO-4217-Codes. */
    private val ccOverrides = mapOf(
        "EUR" to "eu",
        "ANG" to "cw",
        "XCG" to "cw",
        // CFP-Franc: Flagge Frankreichs (frz. Pazifik-Territorien).
        "XPF" to "fr",
        // CFA-Franc & Ostkarib. Dollar: generierte Locator-Karten der Währungsregion
        // (assets/flags/{xaf,xof,xcd}.png) statt einer Landesflagge.
        "XAF" to "xaf",
        "XOF" to "xof",
        "XCD" to "xcd",
    )

    /** Codes ohne eindeutiges Land — kreisförmiger Symbol-Fallback statt Flagge. */
    private val noFlag = emptySet<String>()

    private val cache = ConcurrentHashMap<String, CurrencyInfo>()

    /** Laufzeit-Registry der Custom-Währungen (§F2); wird vom ViewModel aktualisiert. */
    private val customRegistry = ConcurrentHashMap<String, CustomCurrency>()

    fun setCustoms(list: List<CustomCurrency>) {
        customRegistry.clear()
        list.forEach { customRegistry[it.code] = it }
    }

    fun isCustom(code: String): Boolean = customRegistry.containsKey(code)

    /** Emoji einer Custom-Währung (statt Flagge), sonst null. */
    fun emoji(code: String): String? = customRegistry[code]?.emoji

    fun info(code: String): CurrencyInfo {
        customRegistry[code]?.let { return CurrencyInfo(code, it.name, it.code, null) }
        return cache.getOrPut(code) { buildInfo(code) }
    }

    private fun buildInfo(code: String): CurrencyInfo {
        val icu = runCatching { Currency.getInstance(code) }.getOrNull()
        val name = nameOverrides[code]
            ?: icu?.getDisplayName(Locale.GERMAN)?.takeIf { !it.equals(code, ignoreCase = true) }
            ?: code
        val sym = symOverrides[code]
            ?: icu?.getSymbol(Locale.GERMANY)?.takeIf { it.isNotBlank() }
            ?: code
        val cc = ccOverrides[code]
            ?: if (code in noFlag || code.length < 2) null else code.take(2).lowercase(Locale.ROOT)
        return CurrencyInfo(code, name, sym, cc)
    }

    private val displaySymCache = ConcurrentHashMap<String, String>()

    /**
     * Symbol für formatierte Beträge — wie Intl.NumberFormat de-DE der Referenz
     * (CHF → "CHF", USD → "$"); die Tabellen-Symbole aus §6 (`sym`) bleiben den
     * Chips/Picker-Zeilen/Suffixen vorbehalten. Nicht-ISO-Codes fallen auf `sym` zurück.
     */
    fun displaySym(code: String): String = customRegistry[code]?.code ?: displaySymCache.getOrPut(code) {
        runCatching { Currency.getInstance(code).getSymbol(Locale.GERMANY) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: info(code).sym
    }

    /**
     * Nachkommastellen für Beträge (§9): JPY ohne Nachkommastellen, sonst 2.
     * Über ICU verallgemeinert (KRW, VND … ebenfalls 0; BHD/KWD/TND 3).
     */
    fun fractionDigits(code: String): Int {
        val d = runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2)
        return if (d < 0) 2 else d
    }
}
