package com.dsq.rebackground.paint.rendering.gl

import android.opengl.GLES20

/** Avoids redundant state changes without exposing GL state outside the renderer package. */
class GLStateCache {
    private var viewportWidth = -1
    private var viewportHeight = -1
    private var framebuffer = Int.MIN_VALUE

    fun bindFramebuffer(handle: Int) {
        if (framebuffer != handle) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, handle)
            framebuffer = handle
        }
    }

    fun viewport(width: Int, height: Int) {
        if (viewportWidth != width || viewportHeight != height) {
            GLES20.glViewport(0, 0, width, height)
            viewportWidth = width
            viewportHeight = height
        }
    }

    fun invalidate() { viewportWidth = -1; viewportHeight = -1; framebuffer = Int.MIN_VALUE }

    fun invalidateViewport() { viewportWidth = -1; viewportHeight = -1 }
}
