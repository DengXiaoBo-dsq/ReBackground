package com.dsq.rebackground.paint.math

import kotlin.math.sqrt

/** A point or vector expressed in document space. */
data class Vec2(val x: Float, val y: Float) {
    fun distanceTo(other: Vec2): Float {
        val dx = other.x - x
        val dy = other.y - y
        return sqrt(dx * dx + dy * dy)
    }

    fun lerp(to: Vec2, amount: Float): Vec2 =
        Vec2(x + (to.x - x) * amount, y + (to.y - y) * amount)

    val isFinite: Boolean get() = x.isFinite() && y.isFinite()
}
