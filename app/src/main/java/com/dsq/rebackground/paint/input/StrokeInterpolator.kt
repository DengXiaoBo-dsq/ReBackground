package com.dsq.rebackground.paint.input

import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.StrokePoint
import kotlin.math.ceil

/** Stable linear interpolation. Higher-order curves can be introduced without changing input semantics. */
class StrokeInterpolator {
    fun between(from: StrokePoint, to: StrokePoint, maximumSpacing: Float): List<StrokePoint> {
        require(maximumSpacing > 0f)
        val count = ceil(from.position.distanceTo(to.position) / maximumSpacing).toInt().coerceAtLeast(1)
        return (1..count).map { index -> pointAt(from, to, index.toFloat() / count) }
    }

    fun pointAt(from: StrokePoint, to: StrokePoint, t: Float): StrokePoint {
        val u = t.coerceIn(0f, 1f)
        return StrokePoint(
            Vec2(from.position.x + (to.position.x - from.position.x) * u, from.position.y + (to.position.y - from.position.y) * u),
            from.pressure + (to.pressure - from.pressure) * u,
            from.tiltX + (to.tiltX - from.tiltX) * u,
            from.tiltY + (to.tiltY - from.tiltY) * u,
            (from.timestampMillis + (to.timestampMillis - from.timestampMillis) * u).toLong()
        )
    }
}
