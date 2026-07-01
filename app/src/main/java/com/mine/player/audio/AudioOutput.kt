package com.mine.player.audio

import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi

/**
 * Controls the audio output sample rate by resampling with a [SonicAudioProcessor] inserted in
 * the ExoPlayer audio chain. The [PlaybackService] adds [sonic] to the sink; the UI calls
 * [setOutputSampleRate]. (Output *engine* switching — OpenSL ES / AAudio / Direct — is not
 * possible with ExoPlayer's AudioTrack-based sink.)
 */
@OptIn(UnstableApi::class)
object AudioOutput {

    val sonic = SonicAudioProcessor()

    /** hz <= 0 means "follow source" (no resampling). Applies on the next track/reconfigure. */
    fun setOutputSampleRate(hz: Int) {
        sonic.setOutputSampleRateHz(if (hz <= 0) SonicAudioProcessor.SAMPLE_RATE_NO_CHANGE else hz)
    }
}
