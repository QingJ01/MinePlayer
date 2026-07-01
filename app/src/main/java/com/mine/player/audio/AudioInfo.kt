package com.mine.player.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioInfo(
    val format: String,
    val channels: Int,
    val sampleRate: Int,
    val bitrate: Int,
) {
    /** e.g. "MP3 · 2 声道 · 44100 Hz · 128 kbps" */
    fun display(): String = buildString {
        append(prettyFormat(format))
        if (channels > 0) append(" · ${channels} 声道")
        if (sampleRate > 0) append(" · ${sampleRate} Hz")
        if (bitrate > 0) append(" · ${bitrate / 1000} kbps")
    }

    private fun prettyFormat(mime: String): String = when {
        mime.contains("mpeg", true) -> "MP3"
        mime.contains("flac", true) -> "FLAC"
        mime.contains("mp4a", true) || mime.contains("aac", true) -> "AAC"
        mime.contains("vorbis", true) -> "OGG"
        mime.contains("opus", true) -> "Opus"
        mime.contains("wav", true) || mime.contains("raw", true) -> "WAV"
        else -> mime.substringAfter('/').uppercase()
    }
}

suspend fun readAudioInfo(context: Context, uri: Uri): AudioInfo? = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("audio/")) continue
            val ch = if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0
            val sr = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
            val br = if (f.containsKey(MediaFormat.KEY_BIT_RATE)) f.getInteger(MediaFormat.KEY_BIT_RATE) else 0
            return@withContext AudioInfo(mime, ch, sr, br)
        }
        null
    } catch (_: Throwable) {
        null
    } finally {
        try { extractor.release() } catch (_: Throwable) {}
    }
}
