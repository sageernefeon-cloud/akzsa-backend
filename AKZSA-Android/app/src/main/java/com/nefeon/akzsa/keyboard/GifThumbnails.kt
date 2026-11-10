package com.nefeon.akzsa.keyboard

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

/**
 * Loads the small ANIMATED GIF previews shown in the GIF panel grid.
 *
 * Android 9+ (API 28): decoded with ImageDecoder into an [AnimatedImageDrawable] that loops forever, so each
 * thumbnail plays like the real GIF. Android 8 (API 26-27) cannot animate GIFs natively, so it shows the first
 * frame instead. No third-party image library. HTTPS only. Downloaded bytes are kept in a small LRU cache so
 * re-searching or scrolling back does not download again.
 */
object GifThumbnails {
    private const val MAX_BYTES = 1024 * 1024         // one preview above this is skipped; the cell keeps its caption
    private const val CACHE_BYTES = 10 * 1024 * 1024  // ~10 MB of downloaded preview files

    private val bytesCache = object : LruCache<String, ByteArray>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    /** Returns a drawable (animated on API 28+), or null if it cannot be loaded. Safe to call from any dispatcher. */
    suspend fun load(resources: Resources, url: String, targetHeightPx: Int): Drawable? {
        if (!url.startsWith("https://")) return null
        return withContext(Dispatchers.IO) {
            try {
                val bytes = bytesCache.get(url)
                    ?: download(url)?.also { bytesCache.put(url, it) }
                    ?: return@withContext null
                decode(resources, bytes, targetHeightPx)
            } catch (_: Throwable) { null }
        }
    }

    private fun download(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try {
            if (conn.responseCode !in 200..299) return null
            if (conn.contentLengthLong > MAX_BYTES) return null
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            conn.inputStream.use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (out.size() + n > MAX_BYTES) return null
                    out.write(buf, 0, n)
                }
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        } finally { conn.disconnect() }
    }

    private fun decode(resources: Resources, bytes: ByteArray, targetHeightPx: Int): Drawable? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { decodeAnimated(bytes, targetHeightPx)?.let { return it } } catch (_: Throwable) { /* fall back to a still frame */ }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outHeight <= 0 || bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outHeight, targetHeightPx) }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return BitmapDrawable(resources, bmp)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeAnimated(bytes: ByteArray, targetHeightPx: Int): Drawable? {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            val h = info.size.height
            val limit = targetHeightPx * 2   // keep a little extra sharpness for high-density screens
            if (targetHeightPx > 0 && h > limit) {
                val scale = limit.toFloat() / h
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    limit,
                )
            }
        }
        if (drawable is AnimatedImageDrawable) drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        return drawable
    }

    /** Largest power-of-two shrink that keeps the image at least [targetHeightPx] tall. */
    internal fun sampleSize(sourceHeight: Int, targetHeightPx: Int): Int {
        if (targetHeightPx <= 0) return 1
        var sample = 1
        while (sourceHeight / (sample * 2) >= targetHeightPx) sample *= 2
        return sample
    }
}
