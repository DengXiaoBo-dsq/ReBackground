package com.dsq.rebackground.paint.rendering.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Owns an ARRAY_BUFFER; callers upload documented float vertex data only. */
class GLBuffer(private val context: GLContext) {
    var handle: Int = 0
        private set

    fun upload(vertices: FloatArray) {
        context.requireReady()
        require(vertices.isNotEmpty()) { "A GPU buffer cannot be uploaded with an empty vertex array" }
        if (handle == 0) {
            val handles = IntArray(1)
            GLES20.glGenBuffers(1, handles, 0)
            handle = handles[0]
        }
        val data: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices).position(0) }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, handle)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * Float.SIZE_BYTES, data, GLES20.GL_STATIC_DRAW)
    }

    // ============================================================
    // [MOD PR-2.4] 重新填充 buffer（用于 SDF 胶囊每帧动态顶点数据）
    // 使用 glBufferData 重新分配，简单可靠
    // ============================================================
    fun update(data: FloatArray) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, handle)
        val buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(data)
        buffer.position(0)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            data.size * 4,
            buffer,
            GLES20.GL_DYNAMIC_DRAW
        )
    }
    fun release() {
        if (handle != 0) GLES20.glDeleteBuffers(1, intArrayOf(handle), 0)
        handle = 0
    }
}
