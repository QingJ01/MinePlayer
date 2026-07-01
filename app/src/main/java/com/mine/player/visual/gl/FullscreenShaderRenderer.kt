package com.mine.player.visual.gl

import android.content.Context
import android.opengl.GLES20
import com.mine.player.visual.GLTextureView

/**
 * A [GLTextureView.Renderer] that runs a single fullscreen fragment shader (splash, galaxy).
 * Exposes the elapsed time so the host can react (e.g. show a "tap to enter" hint).
 */
class FullscreenShaderRenderer(
    private val context: Context,
    private val vertAsset: String,
    private val fragAsset: String,
) : GLTextureView.Renderer {

    private var shader: FullscreenShader? = null
    private var width = 0
    private var height = 0
    private var startNanos = 0L

    @Volatile var elapsedSeconds: Float = 0f
        private set

    override fun onSurfaceCreated() {
        GLES20.glClearColor(0.004f, 0.012f, 0.016f, 1f)
        shader = FullscreenShader(context, vertAsset, fragAsset).also { it.init() }
        startNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        this.width = width
        this.height = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame() {
        val t = (System.nanoTime() - startNanos) / 1e9f
        elapsedSeconds = t
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        shader?.draw(t, width, height)
    }
}
