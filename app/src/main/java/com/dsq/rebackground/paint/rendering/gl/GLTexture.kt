package com.dsq.rebackground.paint.rendering.gl

import android.opengl.GLES20
import android.opengl.GLES30
import com.dsq.rebackground.paint.rendering.RenderTarget

/** Owns one actual GPU RGBA8 texture. It must only be used on the renderer's GL thread. */
class GLTexture(private val context: GLContext, val target: RenderTarget) {
    var handle: Int = 0
        private set

    fun create() {
        context.requireReady()
        check(handle == 0) { "Texture already created" }
        val handles = IntArray(1)
        GLES20.glGenTextures(1, handles, 0)
        check(handles[0] != 0) { "Unable to allocate OpenGL texture" }
        handle = handles[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, handle)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        when (target.format) {
            RenderTarget.Format.RGBA8 -> {
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    target.width, target.height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
                )
            }
            RenderTarget.Format.RGBA16F -> {
                GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F,
                    target.width, target.height, 0,
                    GLES20.GL_RGBA, GLES30.GL_HALF_FLOAT, null
                )
            }
        }
    }

    fun release() {
        if (handle != 0) GLES20.glDeleteTextures(1, intArrayOf(handle), 0)
        handle = 0
    }
}
