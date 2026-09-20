package com.dsq.rebackground.paint.pigment

import android.opengl.GLES20
import android.opengl.GLES30

/** RG16F velocity field for pigment advection. */
class VelocityField {
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    private var textureHandle = 0
    private var framebufferHandle = 0
    private val savedViewport = IntArray(4)

    val texture: Int get() = textureHandle

    fun create(width: Int, height: Int) {
        require(width > 0 && height > 0)
        if (textureHandle != 0) release()

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureHandle = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES30.GL_RG16F,
            width,
            height,
            0,
            GLES30.GL_RG,
            GLES30.GL_HALF_FLOAT,
            null
        )

        val framebuffers = IntArray(1)
        GLES20.glGenFramebuffers(1, framebuffers, 0)
        framebufferHandle = framebuffers[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferHandle)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            textureHandle,
            0
        )
        check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            "VelocityField framebuffer is incomplete"
        }
        this.width = width
        this.height = height
    }

    fun bindTarget() {
        check(framebufferHandle != 0) { "VelocityField is not created" }
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, savedViewport, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferHandle)
        GLES20.glViewport(0, 0, width, height)
    }

    fun unbind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3])
    }

    fun release() {
        if (framebufferHandle != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebufferHandle), 0)
            framebufferHandle = 0
        }
        if (textureHandle != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureHandle), 0)
            textureHandle = 0
        }
        width = 0
        height = 0
    }
}
