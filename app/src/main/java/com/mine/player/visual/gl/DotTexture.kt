package com.mine.player.visual.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Soft radial particle sprite, reproducing the original `makeDotTexture()`:
 * 64x64, white radial gradient with alpha stops 0.96 → 0.78 → 0.22 → 0 at radius 31.
 */
object DotTexture {
    fun makeBitmap(): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = 32f
        val cy = 32f
        val radius = 31f
        val colors = intArrayOf(
            0xF5FFFFFF.toInt(), // a=0.96
            0xC7FFFFFF.toInt(), // a=0.78
            0x38FFFFFF.toInt(), // a=0.22
            0x00FFFFFF,         // a=0.0
        )
        val stops = floatArrayOf(0.00f, 0.42f, 0.72f, 1.00f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, radius, colors, stops, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bmp
    }
}
