package com.dsq.rebackground.paint.input

import com.dsq.rebackground.paint.math.Vec2

/** Boundary adapter: converts Android View coordinates once, before the core pipeline. */
fun interface DocumentSpaceTransform {
    fun toDocument(viewX: Float, viewY: Float): Vec2

    companion object { val Identity = DocumentSpaceTransform { x, y -> Vec2(x, y) } }
}
