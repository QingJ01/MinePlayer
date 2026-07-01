package com.mine.player.audio

import android.net.Uri

/** One playable local audio item, sourced from MediaStore. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    /** Hint URI for legacy album-art lookup; loaders may prefer loadThumbnail(uri) on Q+. */
    val albumArtUri: Uri?,
    val dateAdded: Long = 0L,
    /** Absolute file path (from MediaStore DATA), used for folder include/exclude filtering. */
    val filePath: String? = null,
) {
    val displayArtist: String
        get() = if (artist.isBlank() || artist == "<unknown>") "未知艺术家" else artist

    /** The folder this file lives in, or null if the path is unknown. */
    val folderPath: String?
        get() = filePath?.substringBeforeLast('/', missingDelimiterValue = "")?.ifEmpty { null }
}
