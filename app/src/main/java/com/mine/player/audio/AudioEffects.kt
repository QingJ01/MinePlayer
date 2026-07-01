package com.mine.player.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.util.Log

/**
 * Holds the system [Equalizer] attached to the player's audio session. The [PlaybackService]
 * owns the player, so it calls [attach] with a fixed session id; the settings UI reads/writes
 * this singleton.
 */
object AudioEffects {

    @Volatile
    var equalizer: Equalizer? = null
        private set

    @Volatile
    var sessionId: Int = 0
        private set

    private var compressor: DynamicsProcessing? = null

    fun attach(sessionId: Int) {
        release()
        this.sessionId = sessionId
        try {
            equalizer = Equalizer(0, sessionId)
        } catch (e: Throwable) {
            Log.w("AudioEffects", "equalizer attach failed", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Throwable) {
        }
        try {
            compressor?.release()
        } catch (_: Throwable) {
        }
        equalizer = null
        compressor = null
        sessionId = 0
    }

    val compressorEnabled: Boolean get() = try { compressor?.enabled ?: false } catch (_: Throwable) { false }

    fun setCompressor(enabled: Boolean) {
        if (enabled) {
            if (compressor == null && sessionId != 0) {
                try {
                    val channels = 2
                    val cfg = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        channels,
                        false, 0,
                        false, 0,
                        false, 0,
                        true,
                    ).build()
                    val dp = DynamicsProcessing(0, sessionId, cfg)
                    val limiter = DynamicsProcessing.Limiter(
                        true, true, 0,
                        1f, 60f, 4f, -20f, 6f,
                    )
                    dp.setLimiterAllChannelsTo(limiter)
                    compressor = dp
                } catch (e: Throwable) {
                    Log.w("AudioEffects", "compressor init failed", e)
                }
            }
            try { compressor?.enabled = true } catch (_: Throwable) {}
        } else {
            try { compressor?.enabled = false } catch (_: Throwable) {}
        }
    }

    val available: Boolean get() = equalizer != null

    var enabled: Boolean
        get() = equalizer?.enabled ?: false
        set(v) {
            try {
                equalizer?.enabled = v
            } catch (_: Throwable) {
            }
        }

    val bandCount: Int get() = equalizer?.numberOfBands?.toInt() ?: 0

    /** min/max band level in millibels. */
    fun levelRange(): Pair<Int, Int> {
        val r = equalizer?.bandLevelRange ?: return -1500 to 1500
        return r[0].toInt() to r[1].toInt()
    }

    /** center frequency of a band in Hz. */
    fun centerHz(band: Int): Int = (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000

    fun level(band: Int): Int = (equalizer?.getBandLevel(band.toShort()) ?: 0).toInt()

    fun setLevel(band: Int, millibel: Int) {
        try {
            equalizer?.setBandLevel(band.toShort(), millibel.toShort())
        } catch (_: Throwable) {
        }
    }

    fun presetNames(): List<String> {
        val eq = equalizer ?: return emptyList()
        return (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()) }
    }

    fun usePreset(index: Int) {
        try {
            equalizer?.usePreset(index.toShort())
        } catch (_: Throwable) {
        }
    }
}
