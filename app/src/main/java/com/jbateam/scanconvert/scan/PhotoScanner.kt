package com.jbateam.scanconvert.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/** Ein erkannter Preis auf einem Galerie-Foto — Box in Bitmap-Pixelkoordinaten. */
data class PhotoDetection(val id: Int, val value: Double, val box: RectF)

/** Ergebnis des Foto-Scans: angezeigtes Bitmap + Preise in dessen Koordinaten. */
data class PhotoScanResult(val bitmap: Bitmap, val detections: List<PhotoDetection>)

/** Maximale Bild-Kante für Anzeige UND OCR — ein gemeinsames Bitmap, eine Koordinatenwelt. */
private const val MAX_DIM = 2048

/** Mindesthöhe einer Ziffern-Box relativ zur Bildhöhe — filtert Streutexte/Rauschen. */
private const val PHOTO_MIN_HEIGHT_FRAC = 0.008f

/** Obergrenze an Overlays — mehr wäre auf einem Foto nicht mehr bedienbar. */
private const val MAX_DETECTIONS = 60

/**
 * Statischer Foto-Scan für die In-App-Galerie (analog Google-Translate-Foto-Modus):
 * Bitmap EXIF-korrekt und ≤[MAX_DIM] laden, ML-Kit-Texterkennung über das GANZE Bild,
 * Preise mit denselben Heuristiken wie der Kamera-Scan extrahieren — aber mit
 * Bounding-Boxen, damit Overlays über den Zahlen platziert werden können.
 */
object PhotoScanner {

    suspend fun scan(context: Context, uri: Uri): PhotoScanResult {
        val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
        val text = recognize(bitmap)
        return PhotoScanResult(bitmap, pricesInPhoto(text, bitmap.width, bitmap.height))
    }

    /**
     * Lädt das Foto als SOFTWARE-Bitmap (ML Kit kann keine Hardware-Bitmaps lesen),
     * bereits gedreht und auf ≤[MAX_DIM] verkleinert. API 28+: ImageDecoder wendet die
     * EXIF-Rotation selbst an; API 26/27: BitmapFactory + Rotation aus der
     * MediaStore-ORIENTATION-Spalte (keine exifinterface-Abhängigkeit nötig).
     */
    private fun loadBitmap(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val w = info.size.width
                val h = info.size.height
                val longest = max(w, h)
                if (longest > MAX_DIM) {
                    val scale = MAX_DIM.toFloat() / longest
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                }
            }
        }

        // API 26/27: Bounds lesen → inSampleSize (grobe 2er-Potenz) → dekodieren →
        // exakt auf ≤MAX_DIM skalieren → per Matrix drehen. Die inSampleSize-Schleife
        // allein bindet die Größe NICHT (sie halbiert nur), daher die exakte Nachskalierung.
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("decode failed: $uri")

        // Exakte Skalierung, falls die längste Kante MAX_DIM noch überschreitet.
        val longest = max(decoded.width, decoded.height)
        val raw = if (longest > MAX_DIM) {
            val s = MAX_DIM.toFloat() / longest
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * s).toInt().coerceAtLeast(1),
                (decoded.height * s).toInt().coerceAtLeast(1),
                true,
            ).also { if (it != decoded) decoded.recycle() }
        } else decoded

        val rotation = mediaStoreOrientation(context, uri)
        return if (rotation == 0) raw else {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                .also { if (it != raw) raw.recycle() }
        }
    }

    /** Rotation (0/90/180/270) aus der MediaStore-ORIENTATION-Spalte; 0 bei Unbekanntem. */
    private fun mediaStoreOrientation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.query(
            uri, arrayOf(MediaStore.Images.Media.ORIENTATION), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } ?: 0
    }.getOrDefault(0)

    /** Einmalige Erkennung: Client pro Scan anlegen und danach schließen. */
    private suspend fun recognize(bitmap: Bitmap): Text {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            }
        } finally {
            recognizer.close()
        }
    }
}

/**
 * Alle Preise auf dem Foto — gleiche Token-/Zeit-/Datums-Heuristiken wie [pricesInRoi],
 * aber OHNE Wert-Dedup: auf einer Rechnung darf derselbe Betrag mehrfach vorkommen,
 * jede Fundstelle bekommt ihr eigenes Overlay. Nur räumlich überlappende Doppel-Lesungen
 * werden zusammengefasst. Ergebnis in Lesereihenfolge, max. [MAX_DETECTIONS].
 */
internal fun pricesInPhoto(text: Text?, width: Int, height: Int): List<PhotoDetection> {
    if (text == null || width <= 0 || height <= 0) return emptyList()
    val minH = height * PHOTO_MIN_HEIGHT_FRAC
    val found = ArrayList<Pair<Double, RectF>>()
    for (block in text.textBlocks) {
        for (line in block.lines) {
            val lt = line.text
            if (TIME_RE.containsMatchIn(lt) || DATE_RE.containsMatchIn(lt)) continue
            for (el in line.elements) {
                val box = el.boundingBox ?: continue
                if (!isNumericToken(el.text)) continue
                val value = parsePrice(el.text) ?: continue
                if (box.height() < minH) continue
                found.add(value to RectF(box))
            }
        }
    }
    if (found.isEmpty()) return emptyList()

    // Räumliches Dedup: überlappt eine Box zu >60 % mit einer bereits behaltenen,
    // ist es dieselbe Zahl (doppelte Block-/Zeilen-Lesung von ML Kit).
    val kept = ArrayList<Pair<Double, RectF>>()
    for (cand in found) {
        val overlapping = kept.any { (_, b) -> overlapFrac(b, cand.second) > 0.6f }
        if (!overlapping) kept.add(cand)
    }

    // Lesereihenfolge wie im Kamera-Pfad: Zeilen über cy-Abstand relativ zur
    // Median-Boxhöhe clustern, oben→unten, darin links→rechts.
    val medianH = kept.map { it.second.height() }.sorted().let { it[it.size / 2] }
    val rowGap = (medianH * 0.7f).coerceAtLeast(1f)
    val rows = ArrayList<MutableList<Pair<Double, RectF>>>()
    for (d in kept.sortedBy { it.second.centerY() }) {
        val row = rows.lastOrNull()
        if (row == null || d.second.centerY() - row.last().second.centerY() > rowGap) {
            rows.add(mutableListOf(d))
        } else row.add(d)
    }
    return rows.flatMap { row -> row.sortedBy { it.second.centerX() } }
        .take(MAX_DETECTIONS)
        .mapIndexed { i, (value, box) -> PhotoDetection(id = i, value = value, box = box) }
}

/** Überlappungsfläche relativ zur kleineren Box (0..1). */
private fun overlapFrac(a: RectF, b: RectF): Float {
    val left = max(a.left, b.left)
    val top = max(a.top, b.top)
    val right = min(a.right, b.right)
    val bottom = min(a.bottom, b.bottom)
    if (right <= left || bottom <= top) return 0f
    val inter = (right - left) * (bottom - top)
    val smaller = min(a.width() * a.height(), b.width() * b.height()).coerceAtLeast(1f)
    return inter / smaller
}
