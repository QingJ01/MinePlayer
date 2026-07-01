package com.mine.player.visual

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * A pass-through ExoPlayer [AudioProcessor] that taps the PCM stream and derives the
 * audio-reactive uniforms the visual shaders expect: bass / mid / treble / beat / energy.
 *
 * Frequency bands mirror the desktop original (index.html beatBandRms):
 *   sub 38-74Hz, kick 52-165Hz, body 165-420Hz, vocal 420-2600Hz, snap 1800-9200Hz.
 *
 * [queueInput] runs on the audio thread; [poll] runs on the GL render thread. The shared
 * ring buffer is guarded by [lock]; the published band values are @Volatile.
 */
@OptIn(UnstableApi::class)
class AudioAnalyzer : BaseAudioProcessor() {

    private val fftSize = 2048
    private val fft = Fft(fftSize)
    private val ring = FloatArray(fftSize)
    private var ringPos = 0
    private val lock = Any()

    private var sampleRate = 44100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT

    private val hann = FloatArray(fftSize) { (0.5 - 0.5 * cos(2.0 * PI * it / (fftSize - 1))).toFloat() }
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)

    /** Visual sensitivity; tune on-device. Higher = more reactive. */
    var sensitivity = 9.0f

    @Volatile var bass = 0f
        private set
    @Volatile var mid = 0f
        private set
    @Volatile var treble = 0f
        private set
    @Volatile var energy = 0f
        private set
    @Volatile var beat = 0f
        private set

    private var kickAvg = 0f
    private var beatEnv = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        synchronized(lock) {
            this.sampleRate = inputAudioFormat.sampleRate
            this.channels = max(1, inputAudioFormat.channelCount)
            this.encoding = inputAudioFormat.encoding
            ring.fill(0f)
            ringPos = 0
        }
        // Pass audio through unchanged; we only observe it.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return
        // Tap a duplicate so reading does not disturb the buffer we pass downstream.
        readPcm(inputBuffer.duplicate().order(ByteOrder.nativeOrder()))
        // Pass-through: copy the (unchanged) input to our output buffer, consuming the input.
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    private fun readPcm(view: ByteBuffer) {
        synchronized(lock) {
            val ch = channels
            when (encoding) {
                C.ENCODING_PCM_16BIT -> {
                    val sb = view.asShortBuffer()
                    val frames = sb.remaining() / ch
                    var f = 0
                    while (f < frames) {
                        var sum = 0f
                        var c = 0
                        while (c < ch) { sum += sb.get() / 32768f; c++ }
                        ring[ringPos] = sum / ch
                        ringPos = if (ringPos + 1 >= fftSize) 0 else ringPos + 1
                        f++
                    }
                }
                C.ENCODING_PCM_FLOAT -> {
                    val fb = view.asFloatBuffer()
                    val frames = fb.remaining() / ch
                    var f = 0
                    while (f < frames) {
                        var sum = 0f
                        var c = 0
                        while (c < ch) { sum += fb.get(); c++ }
                        ring[ringPos] = sum / ch
                        ringPos = if (ringPos + 1 >= fftSize) 0 else ringPos + 1
                        f++
                    }
                }
                else -> { /* unsupported encoding: leave bands decaying */ }
            }
        }
    }

    /** Recompute bands from the latest window. Call once per rendered frame. */
    fun poll() {
        synchronized(lock) {
            var idx = ringPos
            for (i in 0 until fftSize) {
                re[i] = ring[idx] * hann[i]
                im[i] = 0f
                idx = if (idx + 1 >= fftSize) 0 else idx + 1
            }
        }
        fft.transform(re, im)

        val binHz = sampleRate.toFloat() / fftSize
        val sub = bandRms(38f, 74f, binHz)
        val kick = bandRms(52f, 165f, binHz)
        val body = bandRms(165f, 420f, binHz)
        val vocal = bandRms(420f, 2600f, binHz)
        val snap = bandRms(1800f, 9200f, binHz)

        val g = sensitivity
        val nBass = compress((sub + kick) * 0.5f * g)
        val nMid = compress((body + vocal) * 0.5f * g)
        val nTreble = compress(snap * g)
        val nEnergy = compress((sub + kick + body + vocal + snap) * 0.2f * g)

        // Attack fast, release slow, for a lively-but-smooth response.
        bass = smooth(bass, nBass)
        mid = smooth(mid, nMid)
        treble = smooth(treble, nTreble)
        energy = smooth(energy, nEnergy)

        // Beat: kick energy rising above its moving average.
        val kickLevel = (sub + kick) * 0.5f * g
        kickAvg += (kickLevel - kickAvg) * 0.08f
        val onset = kickLevel - kickAvg * 1.35f
        if (onset > 0.04f) beatEnv = 1f
        beatEnv *= 0.86f
        beat = beatEnv
    }

    private fun bandRms(hz0: Float, hz1: Float, binHz: Float): Float {
        val b0 = max(1, (hz0 / binHz).toInt())
        val b1 = min(fftSize / 2, (hz1 / binHz).toInt())
        if (b1 < b0) return 0f
        var s = 0f
        var n = 0
        for (b in b0..b1) {
            val m = hypot(re[b], im[b]) * (2f / fftSize)
            s += m * m
            n++
        }
        return if (n > 0) sqrt(s / n) else 0f
    }

    private fun compress(x: Float): Float = tanh(x).coerceIn(0f, 1f)

    private fun smooth(current: Float, target: Float): Float {
        val k = if (target > current) 0.5f else 0.12f
        return current + (target - current) * k
    }
}
