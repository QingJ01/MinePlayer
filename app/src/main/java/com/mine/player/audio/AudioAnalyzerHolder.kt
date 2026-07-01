package com.mine.player.audio

import androidx.media3.common.util.UnstableApi
import com.mine.player.visual.AudioAnalyzer

/**
 * Single shared [AudioAnalyzer] instance. The [PlaybackService] inserts it into the player's
 * audio sink; the UI's visual renderer reads its bands. Both live in the same process, so a
 * singleton keeps them pointed at the same PCM tap.
 */
@OptIn(UnstableApi::class)
object AudioAnalyzerHolder {
    val instance: AudioAnalyzer = AudioAnalyzer()
}
