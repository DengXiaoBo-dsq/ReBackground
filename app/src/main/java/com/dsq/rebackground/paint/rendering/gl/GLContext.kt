package com.dsq.rebackground.paint.rendering.gl

/** Tracks the GL surface lifecycle on its owning GLSurfaceView thread. */
class GLContext {
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    var isReady: Boolean = false
        private set

    fun markCreated() { isReady = true }
    fun resize(width: Int, height: Int) {
        require(width > 0 && height > 0)
        this.width = width
        this.height = height
    }
    fun markReleased() { isReady = false; width = 0; height = 0 }
    fun requireReady() { check(isReady) { "An active OpenGL context is required" } }
}
