package com.mine.player.visual.gl

import android.opengl.GLES20

/** Thin wrapper over a linked GL program that caches attribute/uniform locations by name. */
class GlProgram(val id: Int) {
    private val uniformCache = HashMap<String, Int>()
    private val attribCache = HashMap<String, Int>()

    fun use() = GLES20.glUseProgram(id)

    fun uniform(name: String): Int =
        uniformCache.getOrPut(name) { GLES20.glGetUniformLocation(id, name) }

    fun attrib(name: String): Int =
        attribCache.getOrPut(name) { GLES20.glGetAttribLocation(id, name) }

    fun set1f(name: String, v: Float) = GLES20.glUniform1f(uniform(name), v)
    fun set1i(name: String, v: Int) = GLES20.glUniform1i(uniform(name), v)
    fun set2f(name: String, x: Float, y: Float) = GLES20.glUniform2f(uniform(name), x, y)
    fun set3f(name: String, x: Float, y: Float, z: Float) = GLES20.glUniform3f(uniform(name), x, y, z)
    fun setMatrix(name: String, m: FloatArray) =
        GLES20.glUniformMatrix4fv(uniform(name), 1, false, m, 0)

    fun setMatrix3(name: String, m: FloatArray) =
        GLES20.glUniformMatrix3fv(uniform(name), 1, false, m, 0)

    fun release() = GLES20.glDeleteProgram(id)
}
