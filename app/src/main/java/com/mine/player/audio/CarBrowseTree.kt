package com.mine.player.audio

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Builds the browsable media tree that a car head unit (Android Auto / Automotive) — or any other
 * [androidx.media3.session.MediaBrowser] — navigates. The tree is:
 *
 * ```
 * root
 *  ├─ 所有歌曲 → song…
 *  ├─ 专辑     → album → song…
 *  └─ 艺术家   → artist → song…
 * ```
 *
 * All state lives in the caller (the service holds the current track list); this object is pure.
 */
object CarBrowseTree {
    const val ROOT = "root"
    private const val SONGS = "songs"
    private const val ALBUMS = "albums"
    private const val ARTISTS = "artists"
    private const val ALBUM_PREFIX = "album:"
    private const val ARTIST_PREFIX = "artist:"
    const val SONG_PREFIX = "song:"

    /** Single source of truth for the top-level categories (id, title, media type). */
    private data class Category(val id: String, val title: String, val mediaType: Int)
    private val CATEGORIES = listOf(
        Category(SONGS, "所有歌曲", MediaMetadata.MEDIA_TYPE_PLAYLIST),
        Category(ALBUMS, "专辑", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
        Category(ARTISTS, "艺术家", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
    )

    /** Nodes worth re-notifying once the library finishes loading. */
    fun categoryIds(): List<String> = listOf(ROOT) + CATEGORIES.map { it.id }

    fun rootItem(): MediaItem = browsable(ROOT, "MinePlayer", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)

    private fun categoryItem(c: Category): MediaItem = browsable(c.id, c.title, c.mediaType)

    fun children(parentId: String, tracks: List<Track>): List<MediaItem> =
        childrenPaged(parentId, tracks, page = 0, pageSize = Int.MAX_VALUE)

    /** Children of [parentId], building MediaItems only for the requested page. */
    fun childrenPaged(parentId: String, tracks: List<Track>, page: Int, pageSize: Int): List<MediaItem> {
        val size = if (pageSize > 0) pageSize else Int.MAX_VALUE
        val from = (page.toLong() * size).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return when {
            parentId == ROOT -> CATEGORIES.map { categoryItem(it) }.pageSlice(from, size)
            parentId == SONGS -> tracks.pageSlice(from, size).map { songItem(it) }
            parentId == ALBUMS -> groupAlbums(tracks).pageSlice(from, size).map { albumItem(it) }
            parentId == ARTISTS -> groupArtists(tracks).pageSlice(from, size).map { artistItem(it) }
            parentId.startsWith(ALBUM_PREFIX) -> {
                val id = parentId.removePrefix(ALBUM_PREFIX).toLongOrNull()
                tracks.filter { it.albumId == id }.pageSlice(from, size).map { songItem(it) }
            }
            parentId.startsWith(ARTIST_PREFIX) -> {
                val name = Uri.decode(parentId.removePrefix(ARTIST_PREFIX))
                tracks.filter { it.displayArtist == name }.pageSlice(from, size).map { songItem(it) }
            }
            else -> emptyList()
        }
    }

    /** Child count of [parentId] without building any MediaItem. */
    fun childCount(parentId: String, tracks: List<Track>): Int = when {
        parentId == ROOT -> CATEGORIES.size
        parentId == SONGS -> tracks.size
        parentId == ALBUMS -> groupAlbums(tracks).size
        parentId == ARTISTS -> groupArtists(tracks).size
        parentId.startsWith(ALBUM_PREFIX) -> {
            val id = parentId.removePrefix(ALBUM_PREFIX).toLongOrNull()
            tracks.count { it.albumId == id }
        }
        parentId.startsWith(ARTIST_PREFIX) -> {
            val name = Uri.decode(parentId.removePrefix(ARTIST_PREFIX))
            tracks.count { it.displayArtist == name }
        }
        else -> 0
    }

    fun item(mediaId: String, tracks: List<Track>): MediaItem? = when {
        mediaId == ROOT -> rootItem()
        mediaId.startsWith(SONG_PREFIX) -> trackOf(mediaId, tracks)?.let { songItem(it) }
        mediaId.startsWith(ALBUM_PREFIX) -> {
            val id = mediaId.removePrefix(ALBUM_PREFIX).toLongOrNull()
            val albumTracks = tracks.filter { it.albumId == id }
            groupAlbums(albumTracks).firstOrNull()?.let { albumItem(it) }
        }
        mediaId.startsWith(ARTIST_PREFIX) -> {
            val name = Uri.decode(mediaId.removePrefix(ARTIST_PREFIX))
            val artistTracks = tracks.filter { it.displayArtist == name }
            groupArtists(artistTracks).firstOrNull()?.let { artistItem(it) }
        }
        else -> CATEGORIES.firstOrNull { it.id == mediaId }?.let { categoryItem(it) }
    }

    /**
     * Turn a (possibly URI-less) item sent by a car controller into a directly-playable one.
     * The app's own controller already supplies a URI, so those pass through untouched; Android Auto
     * strips [MediaItem.localConfiguration] across the process boundary, so we re-resolve by media id.
     * Returns null when the id no longer resolves (deleted track) — a URI-less item must never
     * reach the player, whose media source factory would throw.
     */
    fun resolvePlayable(item: MediaItem, tracks: List<Track>): MediaItem? {
        if (item.localConfiguration != null) return item
        val track = trackOf(item.mediaId, tracks) ?: return null
        return songItem(track)
    }

    private fun <T> List<T>.pageSlice(from: Int, count: Int): List<T> {
        if (from >= size || count <= 0) return emptyList()
        val to = if (count >= size - from) size else from + count
        return subList(from, to)
    }

    private fun trackOf(mediaId: String, tracks: List<Track>): Track? {
        val id = mediaId.removePrefix(SONG_PREFIX).toLongOrNull() ?: return null
        return tracks.firstOrNull { it.id == id }
    }

    fun songItem(track: Track): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.displayArtist)
            .setAlbumTitle(track.album.ifBlank { null })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { track.albumArtUri?.let { setArtworkUri(it) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(SONG_PREFIX + track.id)
            .setUri(track.uri)
            .setMediaMetadata(meta)
            .build()
    }

    private fun albumItem(a: Album): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(a.name)
            .setArtist(a.artist)
            .setSubtitle("${a.count} 首")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
            .apply { a.cover.albumArtUri?.let { setArtworkUri(it) } }
            .build()
        return MediaItem.Builder().setMediaId(ALBUM_PREFIX + a.id).setMediaMetadata(meta).build()
    }

    private fun artistItem(a: Artist): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(a.name)
            .setSubtitle("${a.count} 首")
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
            .apply { a.cover.albumArtUri?.let { setArtworkUri(it) } }
            .build()
        return MediaItem.Builder().setMediaId(ARTIST_PREFIX + Uri.encode(a.name)).setMediaMetadata(meta).build()
    }

    private fun browsable(id: String, title: String, mediaType: Int): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(meta).build()
    }
}
