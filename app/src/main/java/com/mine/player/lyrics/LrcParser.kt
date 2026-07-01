package com.mine.player.lyrics

/** One timed lyric line. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Parses LRC lyric text. Supports multiple time tags per line ([mm:ss.xx]...), with
 * 2- or 3-digit fractional seconds, and ignores ID3-style metadata tags ([ti:], [ar:], ...).
 */
object LrcParser {

    private val TIME = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(content: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        content.lineSequence().forEach { raw ->
            val matches = TIME.findAll(raw).toList()
            if (matches.isEmpty()) return@forEach
            val text = raw.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: continue
                val sec = m.groupValues[2].toLongOrNull() ?: continue
                val frac = m.groupValues[3]
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                out += LyricLine(min * 60_000 + sec * 1_000 + fracMs, text)
            }
        }
        val sorted = out.sortedBy { it.timeMs }
        // Merge lines that share a timestamp (bilingual LRC: original + translation).
        val merged = ArrayList<LyricLine>(sorted.size)
        for (line in sorted) {
            val last = merged.lastOrNull()
            if (last != null && last.timeMs == line.timeMs) {
                merged[merged.size - 1] = last.copy(text = last.text + "\n" + line.text)
            } else {
                merged += line
            }
        }
        return merged
    }

    /** Index of the active line for [positionMs], or -1 before the first line. */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }
}
