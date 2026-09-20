package com.dsq.rebackground.paint.rendering

/** Rendering boundary. Core documents and brush models do not depend on Android Canvas or OpenGL classes. */
interface PaintRenderer {
    fun onSurfaceCreated()
    fun onSurfaceChanged(width: Int, height: Int)
    fun renderFrame()
    fun release()
}
