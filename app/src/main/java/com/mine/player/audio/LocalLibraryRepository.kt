package com.mine.player.audio

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scans the device's MediaStore for local music tracks. */
class LocalLibraryRepository(private val context: Context) {

    private val albumArtBase: Uri = Uri.parse("content://media/external/audio/albumart")

    suspend fun loadTracks(
        minDurationMs: Long = 10_000L,
        useMediaStore: Boolean = true,
        customFolders: Set<String> = emptySet(),
        blockedFolders: Set<String> = emptySet(),
    ): List<Track> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        // BITRATE is only a valid query column on API 30+ (older DBs throw if it's requested).
        val hasBitrate = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATA)
            if (hasBitrate) add(MediaStore.Audio.Media.BITRATE)
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= $minDurationMs"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        // Normalize folders to "path/" so startsWith matches whole path segments.
        val whitelist = customFolders.map { it.trimEnd('/') + "/" }
        val blacklist = blockedFolders.map { it.trimEnd('/') + "/" }

        val result = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val bitrateCol = if (hasBitrate) c.getColumnIndex(MediaStore.Audio.Media.BITRATE) else -1

            val hasWhitelist = whitelist.isNotEmpty()
            while (c.moveToNext()) {
                val path = c.getString(dataCol)
                // Always exclude blocked folders.
                if (path != null && blacklist.any { path.startsWith(it) }) continue
                // Custom folders, once added, scope the library to only those folders — regardless
                // of the media-library switch. With none set, the switch decides whole-library vs empty.
                val included = when {
                    hasWhitelist -> path != null && whitelist.any { path.startsWith(it) }
                    useMediaStore -> true
                    else -> false
                }
                if (!included) continue
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                result += Track(
                    id = id,
                    title = c.getString(titleCol) ?: "未知曲目",
                    artist = c.getString(artistCol) ?: "",
                    album = c.getString(albumCol) ?: "",
                    albumId = albumId,
                    durationMs = c.getLong(durationCol),
                    uri = ContentUris.withAppendedId(collection, id),
                    albumArtUri = ContentUris.withAppendedId(albumArtBase, albumId),
                    dateAdded = c.getLong(dateCol),
                    filePath = path,
                    bitrate = if (bitrateCol >= 0) c.getInt(bitrateCol) else 0,
                )
            }
        }
        result
    }
}
