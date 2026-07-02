package com.mine.player.data

import android.content.Context

/**
 * Play-count statistics: how many times each song and each artist has been listened to.
 * Backed by SharedPreferences (one key per song / artist) so it can be updated without driving
 * any reactive recomposition. A "play" is counted once a track has been listened to past a
 * threshold (see [PlayerViewModel]).
 */
class ListenStatsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("mineplayer_stats", Context.MODE_PRIVATE)

    fun recordPlay(trackId: Long, artist: String) {
        val a = artist.ifBlank { "未知艺术家" }
        prefs.edit()
            .putInt(songKey(trackId), songPlays(trackId) + 1)
            .putInt(artistKey(a), artistPlays(a) + 1)
            .putInt(KEY_TOTAL, totalPlays() + 1)
            .apply()
    }

    fun songPlays(id: Long): Int = prefs.getInt(songKey(id), 0)
    fun artistPlays(name: String): Int = prefs.getInt(artistKey(name), 0)
    fun totalPlays(): Int = prefs.getInt(KEY_TOTAL, 0)

    fun distinctSongs(): Int = prefs.all.entries.count { it.key.startsWith(SONG_PREFIX) && (it.value as? Int ?: 0) > 0 }
    fun distinctArtists(): Int = prefs.all.entries.count { it.key.startsWith(ARTIST_PREFIX) && (it.value as? Int ?: 0) > 0 }

    /** Most-played songs as (trackId, count), highest first. */
    fun topSongs(limit: Int): List<Pair<Long, Int>> =
        prefs.all.entries
            .filter { it.key.startsWith(SONG_PREFIX) && it.value is Int }
            .mapNotNull { e -> e.key.removePrefix(SONG_PREFIX).toLongOrNull()?.let { it to (e.value as Int) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)

    /** Most-played artists as (name, count), highest first. */
    fun topArtists(limit: Int): List<Pair<String, Int>> =
        prefs.all.entries
            .filter { it.key.startsWith(ARTIST_PREFIX) && it.value is Int }
            .map { e -> e.key.removePrefix(ARTIST_PREFIX) to (e.value as Int) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)

    fun clear() = prefs.edit().clear().apply()

    private fun songKey(id: Long) = "$SONG_PREFIX$id"
    private fun artistKey(name: String) = "$ARTIST_PREFIX$name"

    private companion object {
        const val SONG_PREFIX = "song_"
        const val ARTIST_PREFIX = "artist_"
        const val KEY_TOTAL = "total_plays"
    }
}
