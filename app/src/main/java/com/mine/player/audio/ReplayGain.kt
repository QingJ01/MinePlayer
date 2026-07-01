package com.mine.player.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.min

/**
 * Reads ReplayGain gain (in dB) from an audio file's tags so playback volume can
 * be normalized ("音量平衡"):
 *
 *  - FLAC / Ogg Vorbis comments: `REPLAYGAIN_TRACK_GAIN` / `REPLAYGAIN_ALBUM_GAIN`
 *  - MP3 ID3v2 `TXXX` frames with those same descriptions
 *
 * Track gain is preferred, album gain is the fallback. Returns null when the file
 * carries no ReplayGain tags (caller then leaves the volume untouched).
 */
object ReplayGain {

    suspend fun readGainDb(context: Context, uri: Uri): Float? = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { readPrefix(it, 1 shl 20) }
        } catch (_: Throwable) {
            null
        } ?: return@withContext null

        parseFlacVorbis(bytes)?.let { return@withContext it }
        parseId3v2(bytes)?.let { return@withContext it }
        null
    }

    /** "-7.89 dB" / "-7.89" → -7.89f */
    private fun parseGainValue(raw: String): Float? =
        raw.trim().substringBefore(' ').substringBefore("dB").trim().toFloatOrNull()

    // ---- FLAC / Vorbis ------------------------------------------------------

    private fun parseFlacVorbis(b: ByteArray): Float? {
        if (b.size < 4 || b[0] != 'f'.code.toByte() || b[1] != 'L'.code.toByte() ||
            b[2] != 'a'.code.toByte() || b[3] != 'C'.code.toByte()
        ) return null
        var pos = 4
        while (pos + 4 <= b.size) {
            val header = b[pos].toInt() and 0xFF
            val last = (header and 0x80) != 0
            val type = header and 0x7F
            val len = ((b[pos + 1].toInt() and 0xFF) shl 16) or
                ((b[pos + 2].toInt() and 0xFF) shl 8) or (b[pos + 3].toInt() and 0xFF)
            val dataStart = pos + 4
            if (dataStart + len > b.size) break
            if (type == 4) parseVorbisComment(b, dataStart, len)?.let { return it }
            if (last) break
            pos = dataStart + len
        }
        return null
    }

    private fun parseVorbisComment(b: ByteArray, start: Int, len: Int): Float? {
        var p = start
        val end = start + len
        fun u32(): Int {
            val v = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or
                ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)
            p += 4
            return v
        }
        if (p + 4 > end) return null
        val vendorLen = u32()
        p += vendorLen
        if (p + 4 > end) return null
        val count = u32()
        var track: Float? = null
        var album: Float? = null
        var i = 0
        while (i < count && p + 4 <= end) {
            val clen = u32()
            if (clen < 0 || p + clen > end) break
            val comment = String(b, p, clen, Charsets.UTF_8)
            p += clen
            val eq = comment.indexOf('=')
            if (eq > 0) {
                when (comment.substring(0, eq).uppercase()) {
                    "REPLAYGAIN_TRACK_GAIN" -> track = parseGainValue(comment.substring(eq + 1))
                    "REPLAYGAIN_ALBUM_GAIN" -> album = parseGainValue(comment.substring(eq + 1))
                }
            }
            i++
        }
        return track ?: album
    }

    // ---- MP3 / ID3v2 TXXX ---------------------------------------------------

    private fun parseId3v2(b: ByteArray): Float? {
        if (b.size < 10) return null
        if (b[0] != 'I'.code.toByte() || b[1] != 'D'.code.toByte() || b[2] != '3'.code.toByte()) return null
        val major = b[3].toInt() and 0xFF
        val tagSize = syncsafe(b, 6)
        var pos = 10
        val end = min(b.size, 10 + tagSize)
        var track: Float? = null
        var album: Float? = null
        while (pos + 10 <= end) {
            val id = String(b, pos, 4, Charsets.ISO_8859_1)
            if (!id.all { it.isLetterOrDigit() }) break
            val size = if (major >= 4) syncsafe(b, pos + 4) else beInt(b, pos + 4)
            val frameStart = pos + 10
            if (size <= 0 || frameStart + size > end) break
            if (id == "TXXX") {
                val (desc, value) = decodeTxxx(b, frameStart, size)
                when (desc.uppercase()) {
                    "REPLAYGAIN_TRACK_GAIN" -> track = parseGainValue(value)
                    "REPLAYGAIN_ALBUM_GAIN" -> album = parseGainValue(value)
                }
            }
            pos = frameStart + size
        }
        return track ?: album
    }

    /** TXXX layout: encoding(1) + description(\0-terminated) + value. */
    private fun decodeTxxx(b: ByteArray, start: Int, size: Int): Pair<String, String> {
        val enc = b[start].toInt() and 0xFF
        val dataEnd = start + size
        val utf16 = enc == 1 || enc == 2
        var p = start + 1
        val descStart = p
        val descEnd: Int
        if (utf16) {
            while (p + 1 < dataEnd && !(b[p] == 0.toByte() && b[p + 1] == 0.toByte())) p += 2
            descEnd = p
            p += 2
        } else {
            while (p < dataEnd && b[p] != 0.toByte()) p++
            descEnd = p
            p += 1
        }
        if (descStart > dataEnd || p > dataEnd) return "" to ""
        val charset = when (enc) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        val desc = String(b, descStart, (descEnd - descStart).coerceAtLeast(0), charset)
        val value = String(b, p, (dataEnd - p).coerceAtLeast(0), charset)
        return desc.trim() to value.trim()
    }

    private fun readPrefix(input: InputStream, max: Int): ByteArray {
        val buf = ByteArray(max)
        var total = 0
        while (total < max) {
            val r = input.read(buf, total, max - total)
            if (r < 0) break
            total += r
        }
        return if (total == max) buf else buf.copyOf(total)
    }

    private fun syncsafe(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or
            ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or
            (b[off + 3].toInt() and 0x7F)

    private fun beInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)
}
