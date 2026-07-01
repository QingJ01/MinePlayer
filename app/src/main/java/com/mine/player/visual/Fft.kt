package com.mine.player.visual

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 Cooley–Tukey FFT. [n] must be a power of two.
 * Precomputes twiddle tables so per-frame transforms are allocation-free.
 */
class Fft(private val n: Int) {

    init {
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be a power of two, got $n" }
    }

    private val cosTable = FloatArray(n / 2)
    private val sinTable = FloatArray(n / 2)

    init {
        for (i in 0 until n / 2) {
            cosTable[i] = cos(-2.0 * PI * i / n).toFloat()
            sinTable[i] = sin(-2.0 * PI * i / n).toFloat()
        }
    }

    /** Transforms the complex signal held in [re]/[im] in place. */
    fun transform(re: FloatArray, im: FloatArray) {
        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        // Butterfly stages.
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val step = n / len
            var i = 0
            while (i < n) {
                var k = 0
                var twiddle = 0
                while (k < half) {
                    val c = cosTable[twiddle]
                    val s = sinTable[twiddle]
                    val a = i + k
                    val b = a + half
                    val tre = re[b] * c - im[b] * s
                    val tim = re[b] * s + im[b] * c
                    re[b] = re[a] - tre
                    im[b] = im[a] - tim
                    re[a] += tre
                    im[a] += tim
                    k++
                    twiddle += step
                }
                i += len
            }
            len = len shl 1
        }
    }
}
