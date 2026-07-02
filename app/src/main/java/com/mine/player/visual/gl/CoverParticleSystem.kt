package com.mine.player.visual.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random

/** Per-frame audio/animation values fed to the shaders. */
data class FrameUniforms(
    val time: Float,
    val bass: Float,
    val mid: Float,
    val treble: Float,
    val beat: Float,
    val energy: Float,
    val burst: Float,
    val pixel: Float,
    val preset: Float,
    val alpha: Float,
    val loading: Float,
    val vinylSpin: Float,
    val mouseX: Float = 0f,
    val mouseY: Float = 0f,
    val mouseActive: Float = 0f,
    /** Particle-flow amplitude (uIntensity); must match the settings default (flowStrength). */
    val intensity: Float = 1.4f,
)

/**
 * The signature visual: the album cover sampled into an NxN particle grid, plus an additive
 * bloom pass. Vertex/fragment shaders are the original MinePlayer GLSL ported verbatim
 * (assets/shaders/cover*.{vert,frag}); this class supplies geometry, textures and uniforms.
 */
class CoverParticleSystem(private val context: Context) {

    private lateinit var main: GlProgram
    private lateinit var bloom: GlProgram

    private var posVbo = 0
    private var uvVbo = 0
    private var randVbo = 0
    private var count = 0
    private var grid = 119

    private var dotTex = 0
    private var edgeTex = 0
    private var rippleTex = 0
    private var tex0 = 0
    private var tex1 = 0
    private var currentIsT0 = true

    private var hasCover = 0f
    private var colorMixT = 1f

    fun init() {
        main = GlProgram(
            GlUtil.buildProgram(
                GlUtil.loadAsset(context, "shaders/cover.vert"),
                GlUtil.loadAsset(context, "shaders/cover.frag"),
            )
        )
        bloom = GlProgram(
            GlUtil.buildProgram(
                GlUtil.loadAsset(context, "shaders/cover_bloom.vert"),
                GlUtil.loadAsset(context, "shaders/cover_bloom.frag"),
            )
        )
        buildGeometry(grid)
        dotTex = GlUtil.createTextureFromBitmap(DotTexture.makeBitmap())
        edgeTex = GlUtil.create1x1(128, 0, 0, 255) // R=depth .5, G=edge 0, B=fg 0, A=lum 1
        rippleTex = GlUtil.create1x1(0, 0, 0, 0)
        tex0 = placeholderCover()
        tex1 = placeholderCover()
        GlUtil.checkGlError("CoverParticleSystem.init")
    }

    private fun placeholderCover(): Int {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF1C1C28.toInt())
        val t = GlUtil.createTextureFromBitmap(bmp)
        bmp.recycle()
        return t
    }

    private fun buildGeometry(g: Int) {
        grid = g
        count = g * g
        val pos = FloatArray(count * 3)
        val uv = FloatArray(count * 2)
        val rnd = FloatArray(count)
        val texelStep = 1f / g
        val plane = 4.8f
        val rng = Random(1234L)
        for (i in 0 until count) {
            val gx = i % g
            val gy = i / g
            val u = (gx + 0.5f) * texelStep
            val v = (gy + 0.5f) * texelStep
            val px = gx.toFloat() / (g - 1)
            val py = gy.toFloat() / (g - 1)
            pos[i * 3] = (px - 0.5f) * plane
            pos[i * 3 + 1] = (py - 0.5f) * plane
            pos[i * 3 + 2] = 0f
            uv[i * 2] = u
            uv[i * 2 + 1] = v
            rnd[i] = rng.nextFloat()
        }
        posVbo = makeVbo(pos)
        uvVbo = makeVbo(uv)
        randVbo = makeVbo(rnd)
    }

    private fun makeVbo(data: FloatArray): Int {
        val fb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(data).position(0)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, fb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return ids[0]
    }

    /** Upload a new album cover; the previous one is kept for the crossfade. Call on GL thread. */
    fun setCover(bmp: Bitmap) {
        val target = if (currentIsT0) tex1 else tex0
        // GL texture V axis is bottom-up while Android bitmaps are top-down; flip so the cover
        // shows upright (matches Three.js texture.flipY = true in the original).
        val flipped = flipVertical(bmp)
        GlUtil.uploadBitmap(target, flipped)
        if (flipped !== bmp) flipped.recycle()
        currentIsT0 = !currentIsT0
        colorMixT = 0f
        hasCover = 1f
    }

    private fun flipVertical(src: Bitmap): Bitmap = try {
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postScale(1f, -1f) }, false)
    } catch (_: Throwable) {
        src
    }

    fun draw(proj: FloatArray, modelView: FloatArray, dt: Float, frame: FrameUniforms) {
        if (colorMixT < 1f) colorMixT = (colorMixT + dt / 0.9f).coerceAtMost(1f)
        val coverTex = if (currentIsT0) tex0 else tex1
        val prevTex = if (currentIsT0) tex1 else tex0

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE) // additive bloom
        drawPass(bloom, proj, modelView, frame, coverTex, prevTex, isBloom = true)

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA) // normal
        drawPass(main, proj, modelView, frame, coverTex, prevTex, isBloom = false)
    }

    private fun drawPass(
        p: GlProgram,
        proj: FloatArray,
        modelView: FloatArray,
        frame: FrameUniforms,
        coverTex: Int,
        prevTex: Int,
        isBloom: Boolean,
    ) {
        p.use()
        p.setMatrix("projectionMatrix", proj)
        p.setMatrix("modelViewMatrix", modelView)

        bindTex(p, "uDotTex", 0, dotTex)
        bindTex(p, "uCoverTex", 1, coverTex)
        bindTex(p, "uPrevCoverTex", 2, prevTex)
        bindTex(p, "uEdgeTex", 3, edgeTex)
        bindTex(p, "uRippleTex", 4, rippleTex)

        // Frame-varying
        p.set1f("uTime", frame.time)
        p.set1f("uBass", frame.bass)
        p.set1f("uMid", frame.mid)
        p.set1f("uTreble", frame.treble)
        p.set1f("uBeat", frame.beat)
        p.set1f("uEnergy", frame.energy)
        p.set1f("uBurstAmt", frame.burst)
        p.set1f("uPreset", frame.preset)
        p.set1f("uPixel", frame.pixel)
        p.set1f("uAlpha", frame.alpha)
        p.set1f("uParticleDim", 1f)
        p.set1f("uLoading", frame.loading)
        p.set1f("uVinylSpin", frame.vinylSpin)
        p.set1f("uColorMixT", colorMixT)
        p.set1f("uHasCover", hasCover)

        // Constants (mirror the original uniform defaults)
        p.set1f("uIntensity", frame.intensity)
        p.set1f("uDepth", 1.0f)
        p.set1f("uPointScale", 1.0f)
        p.set1f("uSpeed", 1.0f)
        p.set1f("uTwist", 0f)
        p.set1f("uColorBoost", 1.1f)
        p.set1f("uScatter", 0f)
        p.set1f("uCoverRes", 1.0f)
        p.set1f("uBgFade", 0.20f)
        p.set1f("uHasDepth", 0f)
        p.set1f("uEdgeEnabled", 1f)
        p.set1f("uAiBoost", 0f)
        p.set1f("uMouseActive", frame.mouseActive)
        p.set2f("uMouseXY", frame.mouseX, frame.mouseY)
        p.set2f("uHandXY", -999f, -999f)
        p.set1f("uHandActive", 0f)
        p.set1f("uGestureGrip", 0f)
        p.set3f("uTintColor", 0.615f, 0.722f, 0.812f) // #9db8cf
        p.set1f("uTintStrength", 0f)
        p.set1i("uRippleCount", 0)
        p.set1f("uBloomStrength", 0.62f)
        if (isBloom) p.set1f("uBloomSize", 2.65f)

        enableAttrib(p, "position", posVbo, 3)
        enableAttrib(p, "aUv", uvVbo, 2)
        enableAttrib(p, "aRand", randVbo, 1)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)

        disableAttrib(p, "position")
        disableAttrib(p, "aUv")
        disableAttrib(p, "aRand")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun bindTex(p: GlProgram, name: String, unit: Int, tex: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        p.set1i(name, unit)
    }

    private fun enableAttrib(p: GlProgram, name: String, vbo: Int, size: Int) {
        val loc = p.attrib(name)
        if (loc < 0) return
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(loc)
        GLES20.glVertexAttribPointer(loc, size, GLES20.GL_FLOAT, false, 0, 0)
    }

    private fun disableAttrib(p: GlProgram, name: String) {
        val loc = p.attrib(name)
        if (loc >= 0) GLES20.glDisableVertexAttribArray(loc)
    }
}
