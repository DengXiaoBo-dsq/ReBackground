package com.dsq.rebackground.paint.input

import com.dsq.rebackground.paint.stroke.StrokePoint

data class ResamplingConfig(val spacingDocumentUnits: Float = 2f, val maximumIntervalMillis: Long = 16L) {
    init { require(spacingDocumentUnits > 0f && maximumIntervalMillis > 0L) }
}

/** Streaming distance/time resampler. It preserves every endpoint while bounding stamp spacing. */
class StrokeResampler(private val config: ResamplingConfig = ResamplingConfig()) {
    private val interpolator = StrokeInterpolator()
    private var lastInput: StrokePoint? = null
    private var lastEmitted: StrokePoint? = null

    fun reset() { lastInput = null; lastEmitted = null }
    fun add(point: StrokePoint): List<StrokePoint> {
        val previous = lastInput
        if (previous == null) { lastInput = point; lastEmitted = point; return listOf(point) }
        if (point.timestampMillis < previous.timestampMillis) return emptyList()
        lastInput = point
        val result = ArrayList<StrokePoint>()
        val start = lastEmitted ?: previous
        val distance = start.position.distanceTo(point.position)
        val elapsed = point.timestampMillis - start.timestampMillis
        if (distance >= config.spacingDocumentUnits || elapsed >= config.maximumIntervalMillis) {
            val spacing = if (distance == 0f) 1 else kotlin.math.ceil(distance / config.spacingDocumentUnits).toInt()
            for (index in 1..spacing) result += interpolator.pointAt(start, point, index.toFloat() / spacing)
            lastEmitted = point
        }
        return result
    }

    fun finish(point: StrokePoint): List<StrokePoint> {
        val emitted = add(point).toMutableList()
        val last = lastEmitted
        if (last == null || !sameDynamicsAndPosition(last, point)) { emitted += point; lastEmitted = point }
        return emitted
    }

    private fun sameDynamicsAndPosition(first: StrokePoint, second: StrokePoint): Boolean =
        first.position == second.position && first.pressure == second.pressure &&
            first.tiltX == second.tiltX && first.tiltY == second.tiltY
}
