package com.mine.player.visual

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView

/**
 * A TextureView that hosts an OpenGL ES 2.0 render loop on its own EGL thread. Unlike
 * GLSurfaceView, a TextureView composites as an ordinary view, so Compose glass controls
 * can be layered directly on top without window-translucency tricks.
 */
class GLTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    interface Renderer {
        fun onSurfaceCreated()
        fun onSurfaceChanged(width: Int, height: Int)
        fun onDrawFrame()
    }

    private var renderer: Renderer? = null
    private var renderThread: RenderThread? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun setRenderer(r: Renderer) {
        renderer = r
    }

    fun onActivityPause() = renderThread?.setPaused(true)
    fun onActivityResume() = renderThread?.setPaused(false)

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val r = renderer ?: return
        renderThread = RenderThread(surface, width, height, r).also { it.start() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.onSizeChanged(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderThread?.shutdown()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private class RenderThread(
        private val surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
        private val renderer: Renderer,
    ) : Thread("MinePlayerGL") {

        private val lock = Object()
        @Volatile private var running = true
        private var paused = false
        private var w = width
        private var h = height
        private var sizeDirty = true

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        fun onSizeChanged(width: Int, height: Int) = synchronized(lock) {
            w = width; h = height; sizeDirty = true; lock.notifyAll()
        }

        fun setPaused(p: Boolean) = synchronized(lock) {
            paused = p; lock.notifyAll()
        }

        fun shutdown() {
            synchronized(lock) { running = false; lock.notifyAll() }
            try {
                join()
            } catch (_: InterruptedException) {
            }
        }

        override fun run() {
            try {
                initEgl()
            } catch (e: Exception) {
                Log.e("MinePlayerGL", "EGL init failed", e)
                return
            }
            try {
                renderer.onSurfaceCreated()
                var lastW = -1
                var lastH = -1
                while (true) {
                    var cw = 0
                    var ch = 0
                    var dirty = false
                    synchronized(lock) {
                        while (running && paused) lock.wait()
                        cw = w; ch = h; dirty = sizeDirty; sizeDirty = false
                    }
                    if (!running) return
                    if (dirty || cw != lastW || ch != lastH) {
                        renderer.onSurfaceChanged(cw, ch)
                        lastW = cw; lastH = ch
                    }
                    renderer.onDrawFrame()
                    if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                        // surface gone; bail out
                        synchronized(lock) { running = false }
                    }
                }
            } catch (e: Exception) {
                Log.e("MinePlayerGL", "render loop error", e)
            } finally {
                cleanupEgl()
            }
        }

        private fun initEgl() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "no EGL display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfig = IntArray(1)
            check(
                EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfig, 0) &&
                    numConfig[0] > 0
            ) { "eglChooseConfig failed" }
            val config = configs[0]!!

            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

            val surfAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surfaceTexture, surfAttribs, 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }

            check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                "eglMakeCurrent failed"
            }
        }

        private fun cleanupEgl() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
                )
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }
}
