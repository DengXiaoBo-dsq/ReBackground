package com.dsq.rebackground.painttest.gpu

import android.opengl.GLES30
import android.util.Log

/**
 * G0-02: GL Capability Probe。
 *
 * 必须在 GL thread 执行。执行前调用方需保证 GL context 已 ready。
 * 执行后不修改 GL state。
 */
class GpuCapabilityProbe {

    data class Result(
        val glVersion: String,
        val glslVersion: String,
        val maxTextureSize: Int,
        val maxTextureImageUnits: Int,
        val maxCombinedTextureUnits: Int,
        val maxRenderbufferSize: Int,
        val glError: Int
    )

    fun probe(): Result {
        val glVersion = GLES30.glGetString(GLES30.GL_VERSION) ?: "UNKNOWN"
        val glslVersion = GLES30.glGetString(GLES30.GL_SHADING_LANGUAGE_VERSION) ?: "UNKNOWN"

        val maxTexSize = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTexSize, 0)

        val maxTexImageUnits = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_IMAGE_UNITS, maxTexImageUnits, 0)

        val maxCombinedUnits = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, maxCombinedUnits, 0)

        val maxRbSize = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_RENDERBUFFER_SIZE, maxRbSize, 0)

        val err = GLES30.glGetError()

        return Result(
            glVersion = glVersion,
            glslVersion = glslVersion,
            maxTextureSize = maxTexSize[0],
            maxTextureImageUnits = maxTexImageUnits[0],
            maxCombinedTextureUnits = maxCombinedUnits[0],
            maxRenderbufferSize = maxRbSize[0],
            glError = err
        )
    }

    companion object {
        const val TAG = "G0-GL-PROBE"
    }
}