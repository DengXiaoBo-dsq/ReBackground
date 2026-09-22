package com.dsq.rebackground.paint.stroke

import com.dsq.rebackground.paint.math.Vec2
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Deterministic G1 path solver for an already resampled path.
 *
 * Supplying the path as a batch gives the estimator look-ahead. Sharp corners
 * split the regression window so an incoming direction cannot bleed into the
 * outgoing segment.
 */
class StrokeFrameSolver(
    private val tangentRadius: Int = 7,
    private val lowSpeedEpsilon: Float = 0.1f,
    private val cornerThresholdDegrees: Float = 75f
) {
    init {
        require(tangentRadius >= 1)
        require(lowSpeedEpsilon > 0f)
        require(cornerThresholdDegrees in 0f..180f)
    }

    fun solve(points: List<StrokePoint>): List<StrokeFrame> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return listOf(zeroFrame(points.first()))

        val corners = detectCorners(points)
        val frames = ArrayList<StrokeFrame>(points.size)
        var arcLength = 0f
        var lastStable = Vec2(0f, 0f)
        var lastWrapped = 0f
        var lastUnwrapped = 0f
        var hasAngle = false

        points.indices.forEach { index ->
            if (index > 0) arcLength += points[index - 1].position.distanceTo(points[index].position)
            val estimate = estimateTangent(points, index, corners)
            val tangent = if (length(estimate) >= lowSpeedEpsilon) {
                normalized(estimate).also { lastStable = it }
            } else {
                lastStable
            }
            val angle = if (lengthSquared(tangent) == 0f) 0f else atan2(tangent.y, tangent.x)
            val unwrapped = when {
                lengthSquared(tangent) == 0f -> lastUnwrapped
                !hasAngle -> angle.also {
                    hasAngle = true
                    lastWrapped = angle
                    lastUnwrapped = angle
                }
                else -> (lastUnwrapped + wrapToPi(angle - lastWrapped)).also {
                    lastWrapped = angle
                    lastUnwrapped = it
                }
            }
            val distance = if (index == 0) 0f else points[index - 1].position.distanceTo(points[index].position)
            val elapsed = if (index == 0) 0L else
                (points[index].timestampMillis - points[index - 1].timestampMillis).coerceAtLeast(0L)
            frames += StrokeFrame(
                position = points[index].position,
                tangent = tangent,
                angle = angle,
                unwrappedAngle = unwrapped,
                arcLength = arcLength,
                speed = if (elapsed > 0L) distance * 1000f / elapsed else 0f,
                curvature = 0f,
                timestampMillis = points[index].timestampMillis
            )
        }
        return frames
    }

    private fun detectCorners(points: List<StrokePoint>): Set<Int> {
        if (points.size < 3) return emptySet()
        val result = mutableSetOf<Int>()
        val span = tangentRadius.coerceAtMost(3)
        for (i in 1 until points.lastIndex) {
            val left = (i - span).coerceAtLeast(0)
            val right = (i + span).coerceAtMost(points.lastIndex)
            val incoming = delta(points[left].position, points[i].position)
            val outgoing = delta(points[i].position, points[right].position)
            if (length(incoming) < lowSpeedEpsilon || length(outgoing) < lowSpeedEpsilon) continue
            val inUnit = normalized(incoming)
            val outUnit = normalized(outgoing)
            val dot = (inUnit.x * outUnit.x + inUnit.y * outUnit.y).coerceIn(-1f, 1f)
            val turn = Math.toDegrees(acos(dot.toDouble())).toFloat()
            if (turn >= cornerThresholdDegrees) result += i
        }
        return result
    }

    private fun estimateTangent(points: List<StrokePoint>, index: Int, corners: Set<Int>): Vec2 {
        // The corner sample starts the outgoing segment.
        val segmentStart = corners.filter { it <= index }.maxOrNull() ?: 0
        val segmentEnd = corners.filter { it > index }.minOrNull() ?: points.lastIndex
        val start = (index - tangentRadius).coerceAtLeast(segmentStart)
        val end = (index + tangentRadius).coerceAtMost(segmentEnd)
        if (end <= start) return Vec2(0f, 0f)

        val mean = (start + end) * 0.5f
        var x = 0f
        var y = 0f
        for (j in start..end) {
            val weight = j - mean
            x += weight * points[j].position.x
            y += weight * points[j].position.y
        }
        val estimate = Vec2(x, y)
        return if (length(estimate) >= lowSpeedEpsilon) estimate
        else delta(points[start].position, points[end].position)
    }

    private fun zeroFrame(point: StrokePoint) = StrokeFrame(
        point.position, Vec2(0f, 0f), 0f, 0f, 0f, 0f, 0f, point.timestampMillis
    )

    private fun delta(a: Vec2, b: Vec2) = Vec2(b.x - a.x, b.y - a.y)
    private fun lengthSquared(v: Vec2) = v.x * v.x + v.y * v.y
    private fun length(v: Vec2) = sqrt(lengthSquared(v))
    private fun normalized(v: Vec2): Vec2 {
        val magnitude = length(v)
        return if (magnitude == 0f) Vec2(0f, 0f) else Vec2(v.x / magnitude, v.y / magnitude)
    }

    private fun wrapToPi(value: Float): Float {
        var result = value
        val twoPi = (2.0 * PI).toFloat()
        while (result > PI.toFloat()) result -= twoPi
        while (result < -PI.toFloat()) result += twoPi
        return result
    }
}
