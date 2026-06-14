package com.jbateam.scanconvert.scan

import android.graphics.RectF
import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.math.hypot

/** Eine erkannte Zahl im ROI samt Box-Mittelpunkt und -Höhe (View-Koordinaten). */
data class Detection(val value: Double, val cx: Float, val cy: Float, val h: Float)

/** Uhrzeit (12:30) bzw. Datum (13.06.2026, 06/13/26) — werden nicht als Preis gewertet (§5). */
private val TIME_RE = Regex("""\d{1,2}:\d{2}""")
private val DATE_RE = Regex("""\d{1,2}[.\-/]\d{1,2}[.\-/]\d{2,4}""")

/** Relative Toleranz, ab der zwei Werte als „gleich" gelten (OCR-Jitter). */
private const val SIM_TOL = 0.012

/** Mindesthöhe einer Ziffern-Box relativ zur ROI-Höhe — filtert winzige Streutexte. */
private const val MIN_HEIGHT_FRAC = 0.12f

/**
 * Alle Preise im ROI (Handoff §12 „Auswahl"), streng auf den sichtbaren Rahmen
 * begrenzt: nur Zahlen, deren Box-Mittelpunkt IM Rahmen liegt, die nicht in einem
 * Wort stecken (z. B. „transparencyv2") und nicht winzig sind. Mehrfach gelesene
 * Werte werden zusammengefasst; das Ergebnis ist in Lesereihenfolge
 * (zeilenweise oben→unten, darin links→rechts), max. 4 Einträge.
 * Erwartet Bounding-Boxen in View-Koordinaten (COORDINATE_SYSTEM_VIEW_REFERENCED).
 */
fun pricesInRoi(text: Text?, roi: RectF): List<Detection> {
    if (text == null) return emptyList()
    val minH = roi.height() * MIN_HEIGHT_FRAC
    val found = ArrayList<Detection>()
    for (block in text.textBlocks) {
        for (line in block.lines) {
            val lt = line.text
            if (TIME_RE.containsMatchIn(lt) || DATE_RE.containsMatchIn(lt)) continue
            for (el in line.elements) {
                val box = el.boundingBox ?: continue
                if (!isNumericToken(el.text)) continue
                val value = parsePrice(el.text) ?: continue
                val cx = box.exactCenterX()
                val cy = box.exactCenterY()
                if (!roi.contains(cx, cy)) continue        // nur Zahlen mit Mittelpunkt im Rahmen
                if (box.height() < minH) continue           // winzige Streutexte ignorieren
                found.add(Detection(value, cx, cy, box.height().toFloat()))
            }
        }
    }
    if (found.isEmpty()) return emptyList()

    // Gleiche Werte zusammenfassen — je Cluster die dem Zentrum nächste Box behalten.
    val cxR = roi.centerX()
    val cyR = roi.centerY()
    val deduped = ArrayList<Detection>()
    for (d in found.sortedBy { hypot(it.cx - cxR, it.cy - cyR) }) {
        if (deduped.none { similar(it.value, d.value) }) deduped.add(d)
    }

    // Lesereihenfolge: Zeilen über cy-Abstand relativ zur Median-Boxhöhe clustern
    // (robust gegen die Lage der Zeile im Rahmen), oben→unten, darin links→rechts.
    val medianH = deduped.map { it.h }.sorted().let { it[it.size / 2] }
    val rowGap = (medianH * 0.7f).coerceAtLeast(1f)
    val rows = ArrayList<MutableList<Detection>>()
    for (d in deduped.sortedBy { it.cy }) {
        val row = rows.lastOrNull()
        if (row == null || d.cy - row.last().cy > rowGap) rows.add(mutableListOf(d))
        else row.add(d)
    }
    return rows.flatMap { row -> row.sortedBy { it.cx } }.take(4)
}

/**
 * Token gilt als Zahl, wenn die Ziffern einen führenden Block bilden: KEIN Buchstabe
 * vor der ersten Ziffer (verwirft Wort-Ziffern wie „v2", „A4", „transparencyv2") und
 * höchstens eine kurze Einheit dahinter (z. B. „17°C", „50EUR").
 */
private fun isNumericToken(s: String): Boolean {
    val firstDigit = s.indexOfFirst { it.isDigit() }
    if (firstDigit < 0) return false
    if (s.take(firstDigit).any { it.isLetter() }) return false
    return s.count { it.isLetter() } <= 3
}

private fun similar(a: Double, b: Double): Boolean {
    val scale = maxOf(abs(a), abs(b)).coerceAtLeast(1e-6)
    return abs(a - b) <= SIM_TOL * scale
}

/**
 * Tolerantes Preis-Parsing (Handoff §12): Symbole/Buchstaben strippen,
 * '.'/',' heuristisch als Dezimal- oder Tausendertrenner deuten.
 * Tokens mit ':' (Uhrzeit) werden grundsätzlich verworfen.
 */
fun parsePrice(raw: String): Double? {
    if (':' in raw) return null
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
