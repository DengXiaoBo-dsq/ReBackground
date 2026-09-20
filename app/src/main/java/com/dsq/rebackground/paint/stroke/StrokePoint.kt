package com.dsq.rebackground.paint.stroke

import com.dsq.rebackground.paint.math.Vec2

/** Immutable, normalized input point in document space. */
data class StrokePoint(
    val position: Vec2,
    val pressure: Float,
    val tiltX: Float,
    val tiltY: Float,
    val timestampMillis: Long
) {
    init {
        require(position.isFinite) { "Stroke positions must be finite" }
        require(pressure.isFinite() && pressure in 0f..1f) { "Pressure must be normalized" }
        require(tiltX.isFinite() && tiltY.isFinite()) { "Tilt must be finite" }
    }
}
