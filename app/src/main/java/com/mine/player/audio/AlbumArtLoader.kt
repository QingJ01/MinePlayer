package com.mine.player.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

/**
 * Loads album-art bitmaps from content URIs. Used both for UI thumbnails and, later,
 * as the source texture sampled by the M2 cover-particle system — so it returns real
 * [Bitmap]s rather than going through an image-loading library.
 */
object AlbumArtLoader {

    // Memory-bounded cache (~24 MB) sized by actual bitmap bytes, so many small thumbnails or a
    // few large covers coexist without thrashing when scrolling a big library.
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    // Cap concurrent decode / MediaProvider IPCs so a fast fling can't fire dozens at once and
    // starve the system (which shows up as dropped scroll frames). Rows scrolled past before they
    // get a slot are cancelled and never do the work.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val artDispatcher = Dispatchers.IO.limitedParallelism(4)

    private fun keyFor(track: Track, sizePx: Int) = "${track.albumId}@$sizePx"

    fun cached(key: String): Bitmap? = cache.get(key)

    /** Synchronous cache lookup so already-loaded thumbnails render on the first frame, no flicker. */
    fun cachedFor(track: Track, sizePx: Int): Bitmap? = cache.get(keyFor(track, sizePx))

    suspend fun load(context: Context, track: Track, sizePx: Int): Bitmap? {
        val key = keyFor(track, sizePx)
        cache.get(key)?.let { return it } // sync fast path — no dispatcher slot needed on a hit
        return withContext(artDispatcher) {
            cache.get(key)?.let { return@withContext it }
            val bmp = loadViaThumbnail(context, track, sizePx)
                ?: loadViaAlbumArtUri(context, track, sizePx)
            if (bmp != null) cache.put(key, bmp)
            bmp
        }
    }

    private fun loadViaThumbnail(context: Context, track: Track, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            context.contentResolver.loadThumbnail(track.uri, Size(sizePx, sizePx), null)
        } catch (_: Throwable) {
            null
        }
    }

    private fun loadViaAlbumArtUri(context: Context, track: Track, sizePx: Int): Bitmap? {
        val artUri = track.albumArtUri ?: return null
        return try {
            context.contentResolver.openInputStream(artUri)?.use { input ->
                val bytes = input.readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val sample = computeSampleSize(bounds.outWidth, bounds.outHeight, sizePx)
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun computeSampleSize(w: Int, h: Int, target: Int): Int {
        if (w <= 0 || h <= 0 || target <= 0) return 1
        var sample = 1
        val smaller = minOf(w, h)
        while (smaller / (sample * 2) >= target) sample *= 2
        return sample
    }
}
