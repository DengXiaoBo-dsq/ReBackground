package com.dsq.rebackground.paint.stroke

import com.dsq.rebackground.paint.math.Vec2
import kotlin.math.acos

/** Pure, deterministic G1 metric helpers shared by JVM and device validation. */
object StrokeFrameMetrics {
    data class Summary(
        val sampleCount: Int,
        val meanDegrees: Float,
        val p99Degrees: Float,
        val maxDegrees: Float
    )

    fun angleErrorDegrees(actual: Vec2, reference: Vec2): Float {
        require(actual.isFinite && reference.isFinite) { "Tangents must be finite" }
        val actualLengthSquared = actual.x * actual.x + actual.y * actual.y
        val referenceLengthSquared = reference.x * reference.x + reference.y * reference.y
        require(actualLengthSquared > 0f && referenceLengthSquared > 0f) { "Tangents must be non-zero" }
        val dot = ((actual.x * reference.x + actual.y * reference.y) /
            kotlin.math.sqrt(actualLengthSquared * referenceLengthSquared)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(dot.toDouble())).toFloat()
    }

    fun summarize(errorsDegrees: List<Float>): Summary {
        require(errorsDegrees.isNotEmpty()) { "G1 metrics require at least one sample" }
        require(errorsDegrees.all { it.isFinite() && it >= 0f }) { "Angle errors must be finite and non-negative" }
        val sorted = errorsDegrees.sorted()
        val p99Index = kotlin.math.ceil(sorted.size * 0.99).toInt().coerceIn(1, sorted.size) - 1
        return Summary(
            sampleCount = sorted.size,
            meanDegrees = errorsDegrees.average().toFloat(),
            p99Degrees = sorted[p99Index],
            maxDegrees = sorted.last()
        )
    }
}
