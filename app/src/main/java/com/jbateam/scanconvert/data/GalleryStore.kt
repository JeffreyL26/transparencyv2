package com.jbateam.scanconvert.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ein Foto-Album (MediaStore-Bucket); `bucketId == null` steht für „Alle Fotos“. */
data class GalleryAlbum(val bucketId: Long?, val name: String, val count: Int)

/** Ein einzelnes Foto der Galerie. */
data class GalleryPhoto(val id: Long, val bucketId: Long, val uri: Uri)

/**
 * Lokale Galerie über MediaStore (§Galerie-Scan): NUR Bilder, neueste zuerst.
 * Ein einziger Query liefert ids + Buckets; Alben und Album-Filter werden daraus
 * im Speicher abgeleitet (robust gegen fehlende GROUP-BY-Unterstützung ab API 30).
 */
object GalleryStore {

    /** Alle Fotos (id, bucket) neueste zuerst — Basis für Alben UND Grid. */
    suspend fun loadPhotos(context: Context): List<GalleryPhoto> = withContext(Dispatchers.IO) {
        val photos = ArrayList<GalleryPhoto>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    photos.add(
                        GalleryPhoto(
                            id = id,
                            bucketId = c.getLong(bucketCol),
                            uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        )
                    )
                }
            }
        }
        photos
    }

    /**
     * Alben aus den Foto-Zeilen ableiten: Bucket-Namen nachschlagen (ein zweiter,
     * kleiner Query), sortiert nach Größe. „Alle Fotos“ steht nicht drin —
     * die UI stellt es als ersten Chip voran.
     */
    suspend fun loadAlbums(context: Context, photos: List<GalleryPhoto>): List<GalleryAlbum> =
        withContext(Dispatchers.IO) {
            if (photos.isEmpty()) return@withContext emptyList()
            val names = HashMap<Long, String>()
            runCatching {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        if (id !in names) names[id] = c.getString(nameCol) ?: ""
                    }
                }
            }
            photos.groupBy { it.bucketId }
                .map { (bucketId, ps) ->
                    GalleryAlbum(bucketId, names[bucketId].orEmpty().ifBlank { "—" }, ps.size)
                }
                .sortedByDescending { it.count }
        }

    // ---------- Thumbnails ----------

    /** Prozessweiter Thumbnail-Cache, begrenzt auf 1/8 des App-Speichers (in KB). */
    private val thumbCache = object : LruCache<Long, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceAtLeast(4 * 1024)
    ) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount / 1024
    }

    /**
     * Quadratisches Grid-Thumbnail (~240 px). API 29+ über loadThumbnail; darunter
     * über den (dort noch nicht deprecateten) Thumbnails-Provider. null bei Fehler
     * (Zelle bleibt dann als Platzhalter stehen).
     */
    suspend fun thumbnail(context: Context, photo: GalleryPhoto): Bitmap? {
        thumbCache.get(photo.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bmp = runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(photo.uri, Size(240, 240), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver, photo.id,
                        MediaStore.Images.Thumbnails.MINI_KIND, null,
                    )
                }
            }.getOrNull()
            if (bmp != null) thumbCache.put(photo.id, bmp)
            bmp
        }
    }
}
