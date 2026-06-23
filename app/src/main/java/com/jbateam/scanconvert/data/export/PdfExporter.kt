package com.jbateam.scanconvert.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.jbateam.scanconvert.data.CurrencyMeta
import com.jbateam.scanconvert.domain.ListItem
import com.jbateam.scanconvert.domain.TravelList
import com.jbateam.scanconvert.domain.fmt
import com.jbateam.scanconvert.domain.fmtDateTime
import com.jbateam.scanconvert.domain.fmtHistRate
import com.jbateam.scanconvert.domain.fmtNum
import com.jbateam.scanconvert.domain.total
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Rendert eine [TravelList] in eine echte, vektorbasierte A4-PDF — direkt über
 * [PdfDocument] + [Canvas], OHNE WebView und OHNE den Druck-Framework-Umweg.
 *
 * Hintergrund (Fix): Der frühere Weg (Offscreen-WebView → `createPrintDocumentAdapter`
 * → `android.print.PdfPrint`) war unzuverlässig — der WebView-Renderer-Prozess startet
 * kalt/langsam, konkurriert mit der Kamera-/OCR-Pipeline um den Main-Thread und stürzt
 * auf manchen Geräten/Emulatoren ab; zudem sind die `LayoutResultCallback`/
 * `WriteResultCallback`-Konstruktoren paket-privat und über Classloader-Grenzen auf
 * neueren ART-Versionen nicht zugänglich (IllegalAccessError). Das direkte Zeichnen
 * ist deterministisch, schnell, vektorbasiert und ohne Hidden-API.
 *
 * Design (Farben/Typo) folgt der Export-Vorlage; Schriften kommen aus
 * `assets/export/fonts/` (Plus Jakarta Sans + Space Grotesk). Liefert die fertige
 * Datei oder `null` bei Fehler.
 */
object PdfExporter {

    suspend fun renderListPdf(context: Context, list: TravelList, outFile: File): File? =
        withContext(Dispatchers.Default) {
            runCatching {
                outFile.parentFile?.mkdirs()
                val doc = PdfDocument()
                try {
                    Renderer(context, doc).render(list)
                    FileOutputStream(outFile).use { doc.writeTo(it) }
                } finally {
                    doc.close()
                }
                outFile
            }.getOrElse {
                Log.e(TAG, "PDF render failed", it)
                runCatching { if (outFile.exists()) outFile.delete() }
                null
            }
        }

    private const val TAG = "PdfExporter"

    // ---- A4 in PostScript-Punkten (1/72 inch) ----
    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val MARGIN_X = 44f
    private const val MARGIN_TOP = 46f
    private const val MARGIN_BOTTOM = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN_X
    private const val CONTENT_R = PAGE_W - MARGIN_X
    private const val ROW_H = 58f

    // ---- Farb-Tokens (aus der Vorlage) ----
    private const val INK = 0xFF1E261D.toInt()
    private const val INK2 = 0xFF5E6B5C.toInt()
    private const val INK3 = 0xFF93A08F.toInt()
    private const val SURFACE = 0xFFFFFFFF.toInt()
    private const val SURFACE_WARM = 0xFFF1F6EF.toInt()
    private const val LINE = 0xFFE1EADE.toInt()
    private const val ACCENT = 0xFF1F9D6B.toInt()
    private const val ACCENT_DEEP = 0xFF14774F.toInt()
    private const val ACCENT_SOFT = 0x2E1F9D6B
    private const val DANGER = 0xFFC0533A.toInt()

    private class Renderer(context: Context, private val doc: PdfDocument) {
        private val assets = context.applicationContext.assets
        private fun font(name: String) =
            Typeface.createFromAsset(assets, "export/fonts/$name.ttf")

        private val jakartaRegular = font("plus_jakarta_sans_regular")
        private val jakartaMedium = font("plus_jakarta_sans_medium")
        private val jakartaSemi = font("plus_jakarta_sans_semibold")
        private val jakartaBold = font("plus_jakarta_sans_bold")
        private val jakartaExtra = font("plus_jakarta_sans_extrabold")
        private val groteskMedium = font("space_grotesk_medium")
        private val groteskSemi = font("space_grotesk_semibold")
        private val groteskBold = font("space_grotesk_bold")

        private fun paint(tf: Typeface, sizePt: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                textSize = sizePt
                this.color = color
                textAlign = align
            }

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        private var page: PdfDocument.Page? = null
        private var c: Canvas = Canvas()
        private var y = MARGIN_TOP
        private var pageNo = 0

        private fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNo).create())
            page = p
            c = p.canvas
            y = MARGIN_TOP
        }

        private fun finishLast() { page?.let { doc.finishPage(it); page = null } }

        fun render(list: TravelList) {
            newPage()
            drawHeader()
            drawRule()
            drawTitle(list)
            drawTotalCard(list)
            drawEntries(list)
            drawFooter()
            finishLast()
        }

        // ---- Bausteine ----

        private fun rounded(l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int, border: Int? = null) {
            val rect = RectF(l, t, r, b)
            fill.color = color
            c.drawRoundRect(rect, rad, rad, fill)
            if (border != null) {
                stroke.color = border
                stroke.strokeWidth = 1f
                c.drawRoundRect(rect, rad, rad, stroke)
            }
        }

        private fun text(s: String, x: Float, baseline: Float, p: Paint) = c.drawText(s, x, baseline, p)

        private fun drawHeader() {
            // Logo-Marke (44pt) = neues App-Icon: dunkles Rechteck + Scanner-Klammern,
            // drei Währungssymbole (€ $ ¥) mit grüner „Scan-Linie".
            val m = 44f
            val lx = MARGIN_X
            val lt = y
            val cy = lt + m / 2f
            rounded(lx, lt, lx + m, lt + m, 11f, 0xFF13110C.toInt())
            stroke.color = Color.WHITE
            stroke.strokeWidth = 3f
            stroke.strokeCap = Paint.Cap.ROUND
            val pad = 10f
            val a = lx + pad; val b = lt + pad; val a2 = lx + m - pad; val b2 = lt + m - pad
            val seg = 7f
            // vier Eck-Klammern
            c.drawLine(a, b, a + seg, b, stroke); c.drawLine(a, b, a, b + seg, stroke)
            c.drawLine(a2 - seg, b, a2, b, stroke); c.drawLine(a2, b, a2, b + seg, stroke)
            c.drawLine(a, b2 - seg, a, b2, stroke); c.drawLine(a, b2, a + seg, b2, stroke)
            c.drawLine(a2, b2 - seg, a2, b2, stroke); c.drawLine(a2 - seg, b2, a2, b2, stroke)
            // € $ ¥ (Reihenfolge/Position aus dem Logo, auf 44pt skaliert)
            val symPaint = paint(groteskBold, 8.5f, Color.WHITE, Paint.Align.CENTER)
            val symBase = cy + 3f
            text("€", lx + 13.3f, symBase, symPaint)
            text("$", lx + 22f, symBase, symPaint)
            text("¥", lx + 30.7f, symBase, symPaint)
            // grüne Scan-Linie (Pille) über den Symbolen
            fill.color = ACCENT
            c.drawRoundRect(RectF(lx + 9f, cy - 1.3f, lx + 35f, cy + 1.3f), 1.3f, 1.3f, fill)

            // Markentext
            val tx = lx + m + 12f
            text("ScanConvert", tx, lt + 17f, paint(jakartaExtra, 16f, INK))
            text("TRAVEL TOOL", tx, lt + 33f, paint(jakartaBold, 8f, ACCENT_DEEP).apply { letterSpacing = 0.16f })

            // rechts: Erstellt am
            val date = SimpleDateFormat("d. MMMM yyyy", Locale.GERMANY).format(Date())
            text("ERSTELLT AM", CONTENT_R, lt + 12f, paint(jakartaBold, 8f, INK3, Paint.Align.RIGHT).apply { letterSpacing = 0.12f })
            text(date, CONTENT_R, lt + 28f, paint(jakartaBold, 11f, INK, Paint.Align.RIGHT))

            y = lt + m
        }

        private fun drawRule() {
            y += 16f
            fill.color = LINE
            c.drawRect(MARGIN_X, y, CONTENT_R, y + 1f, fill)
            y += 18f
        }

        private fun drawTitle(list: TravelList) {
            text(list.name, MARGIN_X, y + 24f, paint(jakartaExtra, 27f, INK))
            y += 36f
            val sym = CurrencyMeta.info(list.currency).sym
            text(
                "${list.items.size} Ausgaben · Zielwährung ${list.currency} ($sym)",
                MARGIN_X, y + 4f, paint(jakartaMedium, 12f, INK2),
            )
            y += 18f
        }

        private fun drawTotalCard(list: TravelList) {
            y += 14f
            val sym = CurrencyMeta.info(list.currency).sym
            val hasBudget = list.budget != null
            val cardH = if (hasBudget) 184f else 100f
            val top = y
            rounded(MARGIN_X, top, CONTENT_R, top + cardH, 18f, SURFACE, LINE)
            drawCornerBrackets(MARGIN_X, top, CONTENT_R, top + cardH)

            // Inhalt deutlich von den Eck-Klammern (Zone 13–27 vom Rand) wegrücken,
            // damit Text die Klammern nie überlappt.
            val px = MARGIN_X + 30f
            val pr = CONTENT_R - 30f
            text("AUSGEGEBENE SUMME", px, top + 32f, paint(jakartaBold, 9f, INK3).apply { letterSpacing = 0.12f })
            val totalStr = fmtNum(list.total(), list.currency)
            val tvPaint = paint(groteskBold, 34f, INK)
            text(totalStr, px, top + 70f, tvPaint)
            val tvW = tvPaint.measureText(totalStr)
            text(sym, px + tvW + 7f, top + 70f, paint(groteskMedium, 18f, INK2))

            // rechts: Anzahl
            text(list.items.size.toString(), pr, top + 52f, paint(groteskBold, 22f, INK, Paint.Align.RIGHT))
            text("Ausgaben", pr, top + 70f, paint(jakartaSemi, 11f, INK2, Paint.Align.RIGHT))

            if (hasBudget) drawBudget(list, top + 98f, px, pr)
            y = top + cardH
        }

        private fun drawCornerBrackets(l: Float, t: Float, r: Float, b: Float) {
            stroke.color = ACCENT_SOFT
            stroke.strokeWidth = 2.5f
            stroke.strokeCap = Paint.Cap.ROUND
            val o = 13f; val s = 14f
            c.drawLine(l + o, t + o, l + o + s, t + o, stroke); c.drawLine(l + o, t + o, l + o, t + o + s, stroke)
            c.drawLine(r - o - s, t + o, r - o, t + o, stroke); c.drawLine(r - o, t + o, r - o, t + o + s, stroke)
            c.drawLine(l + o, b - o - s, l + o, b - o, stroke); c.drawLine(l + o, b - o, l + o + s, b - o, stroke)
            c.drawLine(r - o, b - o - s, r - o, b - o, stroke); c.drawLine(r - o - s, b - o, r - o, b - o, stroke)
        }

        private fun drawBudget(list: TravelList, top: Float, px: Float, pr: Float) {
            val budget = list.budget ?: return
            val tot = list.total()
            // Trennlinie
            fill.color = LINE
            c.drawRect(px, top, pr, top + 1f, fill)
            val ratio = if (budget > 0) tot / budget else 0.0
            val pct = (ratio * 100).roundToInt()
            val over = tot > budget
            val mid = tot > budget * 0.8 && !over
            text("BUDGET", px, top + 18f, paint(jakartaBold, 9f, INK3).apply { letterSpacing = 0.12f })
            text("$pct % ausgeschöpft", pr, top + 18f, paint(groteskSemi, 11f, if (over) DANGER else INK2, Paint.Align.RIGHT))
            // Track
            val by = top + 26f
            rounded(px, by, pr, by + 8f, 4f, SURFACE_WARM, LINE)
            val w = ((pr - px) * min(1.0, ratio).coerceAtLeast(0.0)).toFloat()
            if (w > 1f) {
                // Solide Füllfarbe statt LinearGradient-Shader: PdfDocument serialisiert
                // Shader unzuverlässig (in vielen PDF-Viewern schwarz/leer). Solid rendert überall.
                fill.color = if (over) 0xFFD9663F.toInt() else if (mid) 0xFFD9A23F.toInt() else ACCENT
                c.drawRoundRect(RectF(px, by, px + w.coerceAtLeast(6f), by + 8f), 4f, 4f, fill)
            }
            // Meta
            val rem = budget - tot
            text("Budget ${fmt(budget, list.currency)}", px, top + 52f, paint(jakartaMedium, 12f, INK2))
            val remStr = if (over) "+${fmt(-rem, list.currency)} über" else "${fmt(rem, list.currency)} übrig"
            text(remStr, pr, top + 52f, paint(groteskBold, 13f, if (over) DANGER else ACCENT_DEEP, Paint.Align.RIGHT))
        }

        private fun drawEntries(list: TravelList) {
            y += 22f
            text("AUSGABEN", MARGIN_X, y, paint(jakartaBold, 9f, INK3).apply { letterSpacing = 0.14f })
            text("BETRAG IN ${list.currency}", CONTENT_R, y, paint(jakartaBold, 9f, INK3, Paint.Align.RIGHT).apply { letterSpacing = 0.1f })
            y += 11f

            if (list.items.isEmpty()) {
                val top = y
                rounded(MARGIN_X, top, CONTENT_R, top + 56f, 16f, SURFACE, LINE)
                text("Noch keine Ausgaben erfasst.", PAGE_W / 2f, top + 32f, paint(jakartaMedium, 12f, INK2, Paint.Align.CENTER))
                y = top + 56f
                return
            }

            val items = list.items.sortedByDescending { it.ts }
            var i = 0
            while (i < items.size) {
                // Wie viele Zeilen passen noch auf diese Seite? (Platz für Footer lassen)
                val avail = PAGE_H - MARGIN_BOTTOM - 48f - y
                var fit = (avail / ROW_H).toInt()
                if (fit < 1) { newPage(); continue }
                fit = min(fit, items.size - i)
                val top = y
                rounded(MARGIN_X, top, CONTENT_R, top + fit * ROW_H, 16f, SURFACE, LINE)
                for (k in 0 until fit) {
                    drawItemRow(items[i + k], list.currency, top + k * ROW_H, i + k + 1, separator = k > 0)
                }
                y = top + fit * ROW_H
                i += fit
                if (i < items.size) newPage()
            }
        }

        private fun drawItemRow(item: ListItem, cur: String, rowTop: Float, index: Int, separator: Boolean) {
            if (separator) {
                fill.color = LINE
                c.drawRect(MARGIN_X + 16f, rowTop, CONTENT_R - 16f, rowTop + 1f, fill)
            }
            val cy = rowTop + ROW_H / 2f
            // Chip mit Quell-Währungssymbol
            val chipR = 16f
            val chipCx = MARGIN_X + 20f + chipR
            rounded(chipCx - chipR, cy - chipR, chipCx + chipR, cy + chipR, chipR, SURFACE_WARM, LINE)
            text(CurrencyMeta.info(item.from).sym, chipCx, cy + 5f, paint(groteskBold, 13f, ACCENT_DEEP, Paint.Align.CENTER))

            // Name + Quelle + Nachverfolgung (Datum/Uhrzeit + Kurs zum Zeitpunkt, §7)
            val tx = chipCx + chipR + 12f
            val name = item.label?.takeIf { it.isNotBlank() } ?: "Ausgabe $index"
            val namePaint = if (item.label.isNullOrBlank()) paint(jakartaSemi, 13f, INK3) else paint(jakartaBold, 14f, INK)
            text(name, tx, rowTop + 20f, namePaint)
            text("aus ${fmtNum(item.raw, item.from)} ${item.from}", tx, rowTop + 34f, paint(jakartaMedium, 11f, INK2))
            text(
                fmtDateTime(item.ts) + "   ·   " + fmtHistRate(item.from, cur, item.raw, item.value),
                tx, rowTop + 47f, paint(jakartaMedium, 8.5f, INK3),
            )

            // Betrag rechts (vertikal mittig)
            val amt = fmtNum(item.value, cur)
            val amtPaint = paint(groteskBold, 17f, INK, Paint.Align.RIGHT)
            val symPaint = paint(groteskSemi, 12f, INK3, Paint.Align.RIGHT)
            val sym = CurrencyMeta.info(cur).sym
            text(sym, CONTENT_R - 20f, cy + 6f, symPaint)
            text(amt, CONTENT_R - 20f - symPaint.measureText(sym) - 3f, cy + 6f, amtPaint)
        }

        private fun drawFooter() {
            val fy = PAGE_H - MARGIN_BOTTOM
            fill.color = LINE
            c.drawRect(MARGIN_X, fy - 16f, CONTENT_R, fy - 15f, fill)
            text("Erstellt mit ScanConvert · Travel Tool", MARGIN_X, fy, paint(jakartaMedium, 10f, INK2))
            text("Kurse zum Zeitpunkt des Scans fixiert", CONTENT_R, fy, paint(groteskMedium, 9.5f, INK3, Paint.Align.RIGHT))
        }
    }
}
