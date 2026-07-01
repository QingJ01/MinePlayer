package com.mine.player.visual.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.Matrix
import com.mine.player.visual.AudioAnalyzer
import com.mine.player.visual.GLTextureView
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * Drives the cover-particle stage each frame: advances time, pulls audio bands from the
 * [AudioAnalyzer], frames the 4.8-unit cover plane with a gentle cinematic drift, and renders
 * the bloom + main particle passes.
 */
class VisualRenderer(
    private val context: Context,
    private val analyzer: AudioAnalyzer,
) : GLTextureView.Renderer {

    private var system: CoverParticleSystem? = null
    private var skull: SkullPointCloud? = null
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val density: Float = context.resources.displayMetrics.density.coerceIn(1f, 3f)

    private var width = 0
    private var height = 0
    private var startNanos = 0L
    private var lastNanos = 0L

    @Volatile private var pendingCover: Bitmap? = null
    @Volatile private var lastCover: Bitmap? = null
    @Volatile var preset: Float = 0f
    @Volatile var skullMode: Boolean = false
    private var vinylSpin = 0f

    @Volatile private var touchNx = 0.5f
    @Volatile private var touchNy = 0.5f
    @Volatile private var touchActive = false

    // Backdrop clear color, tinted from the current cover.
    @Volatile private var bgR = 0.02f
    @Volatile private var bgG = 0.02f
    @Volatile private var bgB = 0.03f

    /** Fraction of the visible half-height to raise the cover into the upper-center. */
    @Volatile private var coverLift = 0.28f

    /** Set the stage background (sRGB 0..1), typically sampled from the album cover. */
    fun setBackground(r: Float, g: Float, b: Float) {
        bgR = r; bgG = g; bgB = b
    }

    /** How far to raise the cover (fraction of half the visible height). */
    fun setCoverLift(fraction: Float) {
        coverLift = fraction
    }

    /** Normalized touch (0..1 from top-left); drives the SILK-preset particle push. */
    fun setTouch(nx: Float, ny: Float, active: Boolean) {
        touchNx = nx
        touchNy = ny
        touchActive = active
    }

    /** Hand a freshly loaded album cover to the GL thread. */
    fun submitCover(bmp: Bitmap?) {
        if (bmp != null) {
            pendingCover = bmp
            lastCover = bmp
        }
    }

    override fun onSurfaceCreated() {
        GLES20.glClearColor(0.02f, 0.02f, 0.03f, 1f)
        val s = CoverParticleSystem(context)
        s.init()
        system = s
        val sk = SkullPointCloud(context)
        sk.init()
        skull = sk
        lastCover?.let { pendingCover = it } // re-apply cover after a context loss
        startNanos = System.nanoTime()
        lastNanos = startNanos
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        this.width = width
        this.height = height
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height > 0) width.toFloat() / height else 1f
        Matrix.perspectiveM(proj, 0, 45f, aspect, 0.1f, 100f)
    }

    override fun onDrawFrame() {
        val s = system ?: return
        val now = System.nanoTime()
        val t = (now - startNanos) / 1e9f
        val dt = ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = now

        pendingCover?.let {
            s.setCover(it)
            pendingCover = null
        }

        analyzer.poll()
        vinylSpin += dt * 0.6f

        // Wallpaper preset spreads particles across a huge volume — pull the camera back so
        // the aurora field is actually in frame.
        val dist = if (preset > 4.5f) 30f else cameraDistance()
        val beatDolly = analyzer.beat * 0.45f
        val camX = sin(t * 0.08f) * 0.45f
        val camY = sin(t * 0.055f) * 0.22f
        val camZ = dist - beatDolly
        // Lift the cover into the upper-center so it isn't perceived as sitting low behind the
        // bottom controls. Pure world-space translation (no camera tilt).
        val halfH = camZ * tan(Math.toRadians(22.5).toFloat())
        val liftWorld = halfH * coverLift
        Matrix.setLookAtM(view, 0, camX, camY, camZ, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.translateM(view, 0, 0f, liftWorld, 0f)

        // Map the current touch to plane coordinates for the SILK-preset particle push.
        var mouseX = 0f
        var mouseY = 0f
        var mouseActive = 0f
        if (touchActive) {
            val aspect = if (height > 0) width.toFloat() / height else 1f
            val halfW = halfH * aspect
            mouseX = (touchNx * 2f - 1f) * halfW + camX
            mouseY = (1f - touchNy * 2f) * halfH + camY - liftWorld
            mouseActive = 1f
        }

        GLES20.glClearColor(bgR, bgG, bgB, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val frame = FrameUniforms(
            time = t,
            bass = analyzer.bass,
            mid = analyzer.mid,
            treble = analyzer.treble,
            beat = analyzer.beat,
            energy = analyzer.energy,
            burst = 0f,
            pixel = density,
            preset = preset,
            alpha = 1f,
            loading = 0f,
            vinylSpin = vinylSpin,
            mouseX = mouseX,
            mouseY = mouseY,
            mouseActive = mouseActive,
        )
        if (skullMode) {
            skull?.draw(proj, view, frame)
        } else {
            s.draw(proj, view, dt, frame)
        }
    }

    /** Distance that frames the 4.8-unit plane (with margin) for the current aspect. */
    private fun cameraDistance(): Float {
        val half = 2.55f
        val vHalf = Math.toRadians(22.5).toFloat()
        val aspect = if (height > 0) width.toFloat() / height else 1f
        val tanV = tan(vHalf)
        val tanH = tanV * aspect
        val dV = half / tanV
        val dH = half / tanH
        return max(dV, dH).coerceIn(4.5f, 14f)
    }
}
