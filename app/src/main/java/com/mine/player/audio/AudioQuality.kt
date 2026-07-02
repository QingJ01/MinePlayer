package com.mine.player.audio

/** Perceived audio-quality tier, surfaced as a small badge (HR / SQ / HQ). */
enum class QualityTier { HIRES, SQ, HQ, STANDARD }

// m4a is deliberately absent: it's usually lossy AAC, only occasionally ALAC.
private val LOSSLESS_EXTS = setOf("flac", "wav", "ape", "alac", "aiff", "aif", "wv", "dsf", "dff", "tak")

private fun isLosslessMime(mime: String): Boolean {
    val m = mime.lowercase()
    return m.contains("flac") || m.contains("alac") || m.contains("wav") ||
        m.contains("raw") || m.contains("pcm") || m.contains("ape")
}

/** Accurate tier from the decoded stream (mime + sample rate + bitrate). */
fun qualityTier(mime: String, sampleRate: Int, bitrateBps: Int): QualityTier {
    val lossless = isLosslessMime(mime)
    val kbps = bitrateBps / 1000
    return when {
        lossless && sampleRate >= 88_200 -> QualityTier.HIRES // 88.2 / 96 / 192 kHz → Hi-Res
        lossless -> QualityTier.SQ
        kbps >= 256 -> QualityTier.HQ
        else -> QualityTier.STANDARD
    }
}

/** Accurate tier for a fully-read [AudioInfo]. */
val AudioInfo.tier: QualityTier
    get() = qualityTier(format, sampleRate, bitrate)

/**
 * Cheap tier estimate from MediaStore metadata alone (file extension + reported bitrate) — no
 * per-file decoding, so it is safe to call for every visible row while scrolling. Hi-Res is
 * inferred for compressed-lossless files whose bitrate is high enough to imply 24-bit / high-rate
 * content (an uncompressed WAV at CD quality already reaches ~1411 kbps, so it stays SQ).
 */
fun Track.qualityTier(): QualityTier {
    val ext = filePath?.substringAfterLast('.', "")?.lowercase().orEmpty()
    val lossless = ext in LOSSLESS_EXTS
    val kbps = bitrate / 1000
    val uncompressed = ext == "wav" || ext == "aiff" || ext == "aif"
    return when {
        lossless && !uncompressed && kbps >= 1400 -> QualityTier.HIRES
        lossless -> QualityTier.SQ
        kbps >= 256 -> QualityTier.HQ
        else -> QualityTier.STANDARD
    }
}
