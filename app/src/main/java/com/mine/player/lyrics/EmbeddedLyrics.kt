package com.mine.player.lyrics

import android.content.Context
import com.mine.player.audio.Track
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.math.min

/**
 * Extracts lyrics embedded in the audio file's ID3v2 tag (USLT / SYLT frames). Most Chinese
 * music downloaded from NetEase / QQ embeds LRC text this way rather than as a sidecar file.
 */
object EmbeddedLyrics {

    fun read(context: Context, track: Track): String? {
        val bytes = try {
            context.contentResolver.openInputStream(track.uri)?.use { readPrefix(it, 3 * 1024 * 1024) }
        } catch (_: Throwable) {
            null
        } ?: return null
        parseId3v2(bytes)?.let { return it }       // MP3 (ID3v2 USLT)
        parseFlacVorbis(bytes)?.let { return it }  // FLAC (Vorbis comment LYRICS)
        return null
    }

    /** FLAC: walk metadata blocks to the VORBIS_COMMENT (type 4) and read its LYRICS field. */
    private fun parseFlacVorbis(b: ByteArray): String? {
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

    private fun parseVorbisComment(b: ByteArray, start: Int, len: Int): String? {
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
        var i = 0
        while (i < count && p + 4 <= end) {
            val clen = u32()
            if (clen < 0 || p + clen > end) break
            val comment = String(b, p, clen, Charsets.UTF_8)
            p += clen
            val eq = comment.indexOf('=')
            if (eq > 0) {
                val key = comment.substring(0, eq).uppercase()
                if (key == "LYRICS" || key == "UNSYNCEDLYRICS") return comment.substring(eq + 1)
            }
            i++
        }
        return null
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

    private fun parseId3v2(b: ByteArray): String? {
        if (b.size < 10) return null
        if (b[0] != 'I'.code.toByte() || b[1] != 'D'.code.toByte() || b[2] != '3'.code.toByte()) return null
        val major = b[3].toInt() and 0xFF
        val tagSize = syncsafe(b, 6)
        var pos = 10
        val end = min(b.size, 10 + tagSize)
        while (pos + 10 <= end) {
            val id = String(b, pos, 4, Charsets.ISO_8859_1)
            if (!id.all { it.isLetterOrDigit() }) break
            val size = if (major >= 4) syncsafe(b, pos + 4) else beInt(b, pos + 4)
            val frameStart = pos + 10
            if (size <= 0 || frameStart + size > end) break
            if (id == "USLT" || id == "SYLT") {
                decodeLyricFrame(b, frameStart, size)?.let { if (it.isNotBlank()) return it }
            }
            pos = frameStart + size
        }
        return null
    }

    /** USLT layout: encoding(1) + language(3) + descriptor(\0-terminated) + lyrics. */
    private fun decodeLyricFrame(b: ByteArray, start: Int, size: Int): String? {
        val enc = b[start].toInt() and 0xFF
        val dataEnd = start + size
        var p = start + 1 + 3 // skip encoding + language
        if (p >= dataEnd) return null
        val utf16 = enc == 1 || enc == 2
        // Skip the null-terminated descriptor.
        p = if (utf16) {
            var q = p
            while (q + 1 < dataEnd && !(b[q] == 0.toByte() && b[q + 1] == 0.toByte())) q += 2
            q + 2
        } else {
            var q = p
            while (q < dataEnd && b[q] != 0.toByte()) q++
            q + 1
        }
        if (p >= dataEnd) return null
        val textBytes = b.copyOfRange(p, dataEnd)
        val text = when (enc) {
            1 -> String(textBytes, Charsets.UTF_16)   // UTF-16 with BOM
            2 -> String(textBytes, Charsets.UTF_16BE)
            3 -> String(textBytes, Charsets.UTF_8)
            else -> decodeLatinOrGbk(textBytes)        // enc 0: ISO-8859-1, often actually GBK for CN
        }
        return text.trim()
    }

    private fun decodeLatinOrGbk(bytes: ByteArray): String {
        // If it has bytes >= 0x80 it's very likely GBK-encoded CJK, not Latin-1.
        val looksHighByte = bytes.any { (it.toInt() and 0x80) != 0 }
        if (looksHighByte) {
            try {
                return bytes.toString(Charset.forName("GBK"))
            } catch (_: Throwable) {
            }
        }
        return bytes.toString(Charsets.ISO_8859_1)
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
