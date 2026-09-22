package com.dsq.rebackground.painttest.gpu

import android.opengl.GLES30

/**
 * G0-03: FBO Completeness Probe。
 *
 * 创建 RGBA16F 与 RG16F 测试 FBO，调用 glCheckFramebufferStatus，
 * 释放资源后恢复所有被改动的 GL state。
 *
 * 必须在 GL thread 执行。
 */
class FboProbe {

    data class Result(
        val rgba16fComplete: Boolean,
        val rgba16fStatus: Int,
        val rg16fComplete: Boolean,
        val rg16fStatus: Int,
        val glError: Int
    )

    fun probe(width: Int = 256, height: Int = 256): Result {
        // ---- 备份 GL state ----
        val prevFbo = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, prevFbo, 0)
        val prevTex = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_TEXTURE_BINDING_2D, prevTex, 0)
        val prevViewport = IntArray(4)
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, prevViewport, 0)

        // ---- 清空 pending error ----
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR) { /* drain */ }

        val rgba16f = testFbo(width, height, GLES30.GL_RGBA16F, GLES30.GL_RGBA)
        val rg16f = testFbo(width, height, GLES30.GL_RG16F, GLES30.GL_RG)

        val err = GLES30.glGetError()

        // ---- 恢复 GL state ----
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, prevFbo[0])
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, prevTex[0])
        GLES30.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3])

        return Result(
            rgba16fComplete = rgba16f.first,
            rgba16fStatus = rgba16f.second,
            rg16fComplete = rg16f.first,
            rg16fStatus = rg16f.second,
            glError = err
        )
    }

    private fun testFbo(
        width: Int,
        height: Int,
        internalFormat: Int,
        format: Int
    ): Pair<Boolean, Int> {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, internalFormat,
            width, height, 0,
            format, GLES30.GL_HALF_FLOAT, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        val fboId = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, texId, 0
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        val complete = status == GLES30.GL_FRAMEBUFFER_COMPLETE

        // ---- 释放临时资源 ----
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDeleteFramebuffers(1, fboIds, 0)
        GLES30.glDeleteTextures(1, texIds, 0)

        return complete to status
    }

    companion object {
        const val TAG = "G0-FBO-PROBE"
    }
}