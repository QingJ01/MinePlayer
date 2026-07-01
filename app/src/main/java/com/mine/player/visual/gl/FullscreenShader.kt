package com.mine.player.visual.gl

import android.content.Context
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders a single fullscreen triangle through a fragment shader with `uResolution` + `uTime`.
 * Reused by the WebGL-style splash and the idle galaxy background — both are fullscreen shaders
 * ported verbatim from the original.
 */
class FullscreenShader(
    private val context: Context,
    private val vertAsset: String,
    private val fragAsset: String,
) {
    private lateinit var program: GlProgram
    private var vbo = 0

    fun init() {
        program = GlProgram(
            GlUtil.buildProgram(
                GlUtil.loadAsset(context, vertAsset),
                GlUtil.loadAsset(context, fragAsset),
            )
        )
        // Oversized triangle that covers the whole viewport (matches the original).
        val data = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val buf = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(data).position(0)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, buf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        vbo = ids[0]
        GlUtil.checkGlError("FullscreenShader.init")
    }

    fun draw(timeSeconds: Float, width: Int, height: Int) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        program.use()
        program.set1f("uTime", timeSeconds)
        program.set2f("uResolution", width.toFloat(), height.toFloat())
        val loc = program.attrib("aPosition")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(loc)
        GLES20.glVertexAttribPointer(loc, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        GLES20.glDisableVertexAttribArray(loc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
}
