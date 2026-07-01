package com.mine.player.visual.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The skull point cloud, driven by `assets/skull-decimation-points.bin` (interleaved
 * x,y,z,kind,seed floats — 5 per point). Shaders ported verbatim from the original
 * (assets/shaders/skull.{vert,frag}); jaw + lighting + audio response preserved.
 */
class SkullPointCloud(private val context: Context) {

    private lateinit var program: GlProgram
    private var posVbo = 0
    private var seedVbo = 0
    private var kindVbo = 0
    private var count = 0
    private var dotTex = 0

    private val model = FloatArray(16)
    private val modelView = FloatArray(16)
    private val normalMat = FloatArray(9)
    private val inv = FloatArray(16)
    private val invT = FloatArray(16)

    fun init() {
        program = GlProgram(
            GlUtil.buildProgram(
                GlUtil.loadAsset(context, "shaders/skull.vert"),
                GlUtil.loadAsset(context, "shaders/skull.frag"),
            )
        )
        dotTex = GlUtil.createTextureFromBitmap(DotTexture.makeBitmap())
        loadGeometry()

        // Static model transform: base position (0, 0.22, 0.10), uniform scale 2.34.
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, 0.22f, 0.10f)
        Matrix.scaleM(model, 0, 2.34f, 2.34f, 2.34f)
        GlUtil.checkGlError("SkullPointCloud.init")
    }

    private fun loadGeometry() {
        val bytes = context.assets.open("skull-decimation-points.bin").use { it.readBytes() }
        val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val total = fb.remaining()
        count = total / 5
        val pos = FloatArray(count * 3)
        val seed = FloatArray(count)
        val kind = FloatArray(count)
        var i = 0
        while (i < count) {
            pos[i * 3] = fb.get()
            pos[i * 3 + 1] = fb.get()
            pos[i * 3 + 2] = fb.get()
            kind[i] = fb.get()
            seed[i] = fb.get()
            i++
        }
        posVbo = makeVbo(pos)
        seedVbo = makeVbo(seed)
        kindVbo = makeVbo(kind)
    }

    private fun makeVbo(data: FloatArray): Int {
        val buf = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(data).position(0)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, buf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return ids[0]
    }

    fun draw(proj: FloatArray, view: FloatArray, frame: FrameUniforms) {
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        computeNormalMatrix(modelView, normalMat)

        program.use()
        program.setMatrix("projectionMatrix", proj)
        program.setMatrix("modelViewMatrix", modelView)
        program.setMatrix3("normalMatrix", normalMat)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, dotTex)
        program.set1i("uMap", 0)

        program.set1f("uTime", frame.time)
        program.set1f("uPixel", frame.pixel)
        program.set1f("uPointScale", 1.0f)
        program.set1f("uBloomStrength", 0.62f)
        program.set1f("uColorBoost", 1.1f)
        program.set1f("uBass", frame.bass)
        program.set1f("uMid", frame.mid)
        program.set1f("uTreble", frame.treble)
        program.set1f("uBeat", frame.beat)
        // Jaw opens with the low end; flash on the beat.
        program.set1f("uJawOpen", (frame.bass * 0.6f + frame.beat * 0.5f).coerceIn(0f, 1.2f))
        program.set1f("uSkullFlash", frame.beat)
        program.set1f("uOpacity", 1.0f)
        program.set3f("uColorA", 0.722f, 0.682f, 0.596f) // #b8ae98
        program.set3f("uColorB", 1.0f, 0.957f, 0.847f)   // #fff4d8
        program.set3f("uShadow", 0.063f, 0.051f, 0.051f) // #100d0d
        program.set3f("uLight", 1.0f, 0.890f, 0.627f)    // #ffe3a0

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        enableAttrib("position", posVbo, 3)
        enableAttrib("seed", seedVbo, 1)
        enableAttrib("kind", kindVbo, 1)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)
        disableAttrib("position")
        disableAttrib("seed")
        disableAttrib("kind")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    }

    private fun enableAttrib(name: String, vbo: Int, size: Int) {
        val loc = program.attrib(name)
        if (loc < 0) return
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(loc)
        GLES20.glVertexAttribPointer(loc, size, GLES20.GL_FLOAT, false, 0, 0)
    }

    private fun disableAttrib(name: String) {
        val loc = program.attrib(name)
        if (loc >= 0) GLES20.glDisableVertexAttribArray(loc)
    }

    /** normalMatrix = transpose(inverse(modelView_3x3)). */
    private fun computeNormalMatrix(mv: FloatArray, out: FloatArray) {
        Matrix.invertM(inv, 0, mv, 0)
        Matrix.transposeM(invT, 0, inv, 0)
        out[0] = invT[0]; out[1] = invT[1]; out[2] = invT[2]
        out[3] = invT[4]; out[4] = invT[5]; out[5] = invT[6]
        out[6] = invT[8]; out[7] = invT[9]; out[8] = invT[10]
    }
}
