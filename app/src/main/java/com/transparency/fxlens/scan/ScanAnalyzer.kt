package com.transparency.fxlens.scan

import android.graphics.RectF
import com.google.mlkit.vision.text.Text
import kotlin.math.hypot

/**
 * Wählt aus allen ML-Kit-Elementen im ROI den Preis, dessen Box-Mittelpunkt
 * dem ROI-Zentrum am nächsten liegt (Handoff §12 „Auswahl").
 * Erwartet Bounding-Boxen in View-Koordinaten (COORDINATE_SYSTEM_VIEW_REFERENCED).
 */
fun bestPriceInRoi(text: Text?, roi: RectF): Double? {
    if (text == null) return null
    val cx = roi.centerX()
    val cy = roi.centerY()
    var best: Double? = null
    var bestDist = Float.MAX_VALUE
    for (block in text.textBlocks) {
        for (line in block.lines) {
            for (element in line.elements) {
                val box = element.boundingBox ?: continue
                val ex = box.exactCenterX()
                val ey = box.exactCenterY()
                if (!roi.contains(ex, ey)) continue
                val value = parsePrice(element.text) ?: continue
                val dist = hypot(ex - cx, ey - cy)
                if (dist < bestDist) {
                    bestDist = dist
                    best = value
                }
            }
        }
    }
    return best
}

/**
 * Tolerantes Preis-Parsing (Handoff §12): Symbole/Buchstaben strippen,
 * '.'/',' heuristisch als Dezimal- oder Tausendertrenner deuten.
 */
fun parsePrice(raw: String): Double? {
    val cleaned = raw.filter { it.isDigit() || it == '.' || it == ',' }
    if (cleaned.none { it.isDigit() }) return null

    val hasDot = '.' in cleaned
    val hasComma = ',' in cleaned
    val normalized = when {
        hasDot && hasComma -> {
            // Das spätere Zeichen ist Dezimaltrenner, das andere Tausendertrenner.
            if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) {
                cleaned.replace(".", "").replace(',', '.')
            } else {
                cleaned.replace(",", "")
            }
        }
        hasComma -> {
            // Einzelnes ',' = Dezimaltrenner (de); mehrere = Tausendertrenner.
            if (cleaned.count { it == ',' } > 1) cleaned.replace(",", "")
            else cleaned.replace(',', '.')
        }
        hasDot -> {
            // Genau ein '.' mit 1–2 Nachkommastellen = Dezimaltrenner,
            // sonst (3 Nachkommastellen oder mehrere '.') Tausendertrenner.
            val digitsAfter = cleaned.length - cleaned.lastIndexOf('.') - 1
            if (cleaned.count { it == '.' } == 1 && digitsAfter in 1..2) cleaned
            else cleaned.replace(".", "")
        }
        else -> cleaned
    }

    val value = normalized.toDoubleOrNull() ?: return null
    return if (value <= 0.0 || value > 10_000_000.0) null else value
}
