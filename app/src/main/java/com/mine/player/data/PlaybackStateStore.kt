package com.mine.player.data

import android.content.Context

/**
 * Remembers the last-played track and position so playback can resume where it left off.
 * Uses SharedPreferences (not DataStore) so it can be written frequently without driving any
 * reactive recomposition.
 */
class PlaybackStateStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("mineplayer_playback", Context.MODE_PRIVATE)

    fun save(trackId: Long, positionMs: Long) {
        prefs.edit()
            .putLong(KEY_TRACK, trackId)
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun trackId(): Long = prefs.getLong(KEY_TRACK, -1L)

    fun positionMs(): Long = prefs.getLong(KEY_POSITION, 0L)

    private companion object {
        const val KEY_TRACK = "last_track_id"
        const val KEY_POSITION = "last_position_ms"
    }
}
