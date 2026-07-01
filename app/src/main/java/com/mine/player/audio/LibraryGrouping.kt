package com.mine.player.audio

/** An album grouped from the scanned tracks. */
data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val cover: Track,
    val count: Int,
)

/** An artist grouped from the scanned tracks. */
data class Artist(
    val name: String,
    val cover: Track,
    val count: Int,
)

fun groupAlbums(tracks: List<Track>): List<Album> =
    tracks.groupBy { it.albumId }
        .map { (id, list) ->
            val first = list.first()
            Album(
                id = id,
                name = first.album.ifBlank { "未知专辑" },
                artist = first.displayArtist,
                cover = first,
                count = list.size,
            )
        }
        .sortedBy { it.name }

fun groupArtists(tracks: List<Track>): List<Artist> =
    tracks.groupBy { it.displayArtist }
        .map { (name, list) -> Artist(name = name, cover = list.first(), count = list.size) }
        .sortedBy { it.name }
