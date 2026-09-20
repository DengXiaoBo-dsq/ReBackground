package com.dsq.rebackground.paint.input

import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.StrokePoint

/** One sanitized pointer observation, already mapped into document space. */
data class PointerSample(
    val pointerId: Int,
    val position: Vec2,
    val pressure: Float,
    val tiltX: Float,
    val tiltY: Float,
    val timestampMillis: Long
) {
    init { require(pointerId >= 0 && position.isFinite) { "Pointer sample must have a valid id and position" } }
    fun toStrokePoint(): StrokePoint = StrokePoint(
        position,
        if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 1f,
        if (tiltX.isFinite()) tiltX else 0f,
        if (tiltY.isFinite()) tiltY else 0f,
        timestampMillis.coerceAtLeast(0L)
    )
}

//

