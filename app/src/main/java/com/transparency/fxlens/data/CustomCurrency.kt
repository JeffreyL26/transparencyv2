package com.transparency.fxlens.data

import kotlinx.serialization.Serializable

/**
 * Vom Nutzer angelegte „Custom"-Währung (max. 5, §F2). Sie ist an eine echte
 * Referenzwährung gekoppelt: `1 refCode = perRef [custom]`. Ihr EUR-Kurs ergibt
 * sich daher live aus dem Referenzkurs (rate[refCode] · perRef) und folgt diesem.
 *
 * `code` ist der stabile, eindeutige Schlüssel (zugleich Chip-Anzeige) — die vom
 * Nutzer optional eingegebene Abkürzung, sonst generiert. Statt einer Flagge wird
 * `emoji` angezeigt.
 */
@Serializable
data class CustomCurrency(
    val code: String,
    val name: String,
    val emoji: String,
    val refCode: String,
    val perRef: Double,
)
