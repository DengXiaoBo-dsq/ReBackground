package com.dsq.rebackground.paint.rendering.gl

import android.opengl.GLES20

/** Compiles and owns a linked GLES 2 program with actionable compiler diagnostics. */
class GLShader(private val context: GLContext, private val vertexSource: String, private val fragmentSource: String) {
    var program: Int = 0
        private set

    fun create() {
        context.requireReady()
        check(program == 0) { "Shader program already created" }
        var vertex = 0
        var fragment = 0
        try {
            vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
            fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            program = GLES20.glCreateProgram()
            check(program != 0) { "Unable to create OpenGL program" }
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "OpenGL program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
        } finally {
            if (vertex != 0) GLES20.glDeleteShader(vertex)
            if (fragment != 0) GLES20.glDeleteShader(fragment)
        }
    }

    fun release() {
        if (program != 0) GLES20.glDeleteProgram(program)
        program = 0
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "Unable to create OpenGL shader" }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("OpenGL shader compilation failed: $error")
        }
        return shader
    }
}
