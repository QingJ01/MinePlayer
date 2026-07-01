package com.mine.player.lyrics

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mine.player.audio.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

/**
 * Resolves lyrics for a local track by locating a sidecar `.lrc` file next to the audio
 * (e.g. `Song.mp3` -> `Song.lrc`). MediaStore does not expose embedded lyrics, so the sidecar
 * convention is the reliable local source.
 */
class LyricRepository(private val context: Context) {

    suspend fun load(track: Track): List<LyricLine> = withContext(Dispatchers.IO) {
        val sidecar = findSidecar(track)?.let { LrcParser.parse(it) }
        if (!sidecar.isNullOrEmpty()) {
            android.util.Log.d("MinePlayerLyric", "sidecar hit '${track.title}': ${sidecar.size} lines")
            return@withContext sidecar
        }
        val embeddedText = EmbeddedLyrics.read(context, track)
        val embedded = embeddedText?.let { LrcParser.parse(it) }
        android.util.Log.d(
            "MinePlayerLyric",
            "'${track.title}' uri=${track.uri}: sidecar=${sidecar?.size ?: 0}, " +
                "embeddedTextLen=${embeddedText?.length ?: -1}, embeddedLines=${embedded?.size ?: 0}",
        )
        embedded ?: emptyList()
    }

    private fun findSidecar(track: Track): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) findViaMediaStore(track)
        else findViaDataPath(track)

    private fun findViaMediaStore(track: Track): String? {
        val audioUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id,
        )
        val meta = context.contentResolver.query(
            audioUri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0)
                val rel = c.getString(1)
                if (name != null && rel != null) name to rel else null
            } else null
        } ?: return null

        val base = meta.first.substringBeforeLast('.', meta.first)
        val lrcName = "$base.lrc"
        val filesUri = MediaStore.Files.getContentUri("external")
        val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH}=? AND " +
            "${MediaStore.Files.FileColumns.DISPLAY_NAME}=?"
        context.contentResolver.query(
            filesUri,
            arrayOf(MediaStore.Files.FileColumns._ID),
            selection,
            arrayOf(meta.second, lrcName),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                return readText(ContentUris.withAppendedId(filesUri, id))
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun findViaDataPath(track: Track): String? {
        val audioUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id,
        )
        context.contentResolver.query(
            audioUri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val data = c.getString(0) ?: return null
                val lrcPath = data.substringBeforeLast('.', data) + ".lrc"
                val f = File(lrcPath)
                if (f.exists()) return decode(f.readBytes())
            }
        }
        return null
    }

    private fun readText(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { decode(it.readBytes()) }
    } catch (_: Throwable) {
        null
    }

    /** Decode as UTF-8, falling back to GBK if replacement characters appear (common for CN lrc). */
    private fun decode(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        if (!utf8.contains('�')) return utf8
        return try {
            bytes.toString(Charset.forName("GBK"))
        } catch (_: Throwable) {
            utf8
        }
    }
}
