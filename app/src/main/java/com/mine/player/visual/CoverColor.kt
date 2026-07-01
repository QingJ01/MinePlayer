package com.mine.player.visual

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Derives a calm, dark backdrop tint from an album cover so the now-playing stage picks up
 * the artwork's color instead of being flat black. The hue/saturation come from the cover's
 * most colorful pixels; the lightness is forced low so light text and particles stay readable
 * in both themes.
 */
object CoverColor {

    /** Default near-black used when there is no cover. */
    const val DEFAULT: Int = 0xFF0A0B0F.toInt()

    fun backdrop(bmp: Bitmap): Int {
        val small = try {
            Bitmap.createScaledBitmap(bmp, 28, 28, true)
        } catch (_: Throwable) {
            return DEFAULT
        }

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var weight = 0.0
        val hsv = FloatArray(3)
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val c = small.getPixel(x, y)
                Color.colorToHSV(c, hsv)
                // Favor colorful, mid-bright pixels; still count everything a little.
                val w = 0.12 + hsv[1].toDouble() * hsv[2].toDouble()
                sumR += Color.red(c) * w
                sumG += Color.green(c) * w
                sumB += Color.blue(c) * w
                weight += w
            }
        }
        if (small != bmp) small.recycle()
        if (weight <= 0.0) return DEFAULT

        val avg = Color.rgb((sumR / weight).toInt(), (sumG / weight).toInt(), (sumB / weight).toInt())
        Color.colorToHSV(avg, hsv)
        hsv[1] = hsv[1].coerceAtMost(0.55f) // cap saturation so it never turns neon
        hsv[2] = 0.20f                       // fixed dark value keeps the stage readable
        return Color.HSVToColor(0xFF, hsv)
    }
}
