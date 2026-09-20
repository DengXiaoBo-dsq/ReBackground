package com.dsq.rebackground.paint.rendering.gl

import android.opengl.GLES20

/** Framebuffer whose color attachment is an engine-managed GLTexture. */
class GLFramebuffer(private val context: GLContext, val colorTexture: GLTexture) {
    var handle: Int = 0
        private set

    fun create() {
        context.requireReady()
        check(colorTexture.handle != 0) { "Create the color texture before its framebuffer" }
        val handles = IntArray(1)
        GLES20.glGenFramebuffers(1, handles, 0)
        handle = handles[0]
        check(handle != 0) { "Unable to allocate OpenGL framebuffer" }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, handle)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, colorTexture.handle, 0)
        check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) { "Incomplete OpenGL framebuffer" }
    }

    fun release() {
        if (handle != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(handle), 0)
        handle = 0
    }
}
