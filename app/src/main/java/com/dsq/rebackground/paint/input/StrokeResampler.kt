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
    private var distanceUntilNext = config.spacingDocumentUnits

    fun reset() {
        lastInput = null
        lastEmitted = null
        distanceUntilNext = config.spacingDocumentUnits
    }

    fun add(point: StrokePoint): List<StrokePoint> {
        val previous = lastInput
        if (previous == null) {
            lastInput = point
            lastEmitted = point
            distanceUntilNext = config.spacingDocumentUnits
            return listOf(point)
        }
        if (point.timestampMillis < previous.timestampMillis) return emptyList()
        lastInput = point
        val result = ArrayList<StrokePoint>()

        val segmentLength = previous.position.distanceTo(point.position)
        var consumed = 0f
        while (segmentLength - consumed + 1e-5f >= distanceUntilNext) {
            consumed += distanceUntilNext
            val amount = if (segmentLength == 0f) 1f else (consumed / segmentLength).coerceIn(0f, 1f)
            val emitted = interpolator.pointAt(previous, point, amount)
            if (!sameDynamicsAndPosition(lastEmitted ?: previous, emitted)) {
                result += emitted
                lastEmitted = emitted
            }
            distanceUntilNext = config.spacingDocumentUnits
        }
        distanceUntilNext -= (segmentLength - consumed).coerceAtLeast(0f)

        val emitted = lastEmitted
        if (result.isEmpty() && emitted != null &&
            point.timestampMillis - emitted.timestampMillis >= config.maximumIntervalMillis) {
            result += point
            lastEmitted = point
            distanceUntilNext = config.spacingDocumentUnits
        }
        return result
    }

    fun finish(point: StrokePoint): List<StrokePoint> {
        val emitted = add(point).toMutableList()
        val last = lastEmitted
        if (last == null || !sameDynamicsAndPosition(last, point)) {
            emitted += point
            lastEmitted = point
            distanceUntilNext = config.spacingDocumentUnits
        }
        return emitted
    }

    private fun sameDynamicsAndPosition(first: StrokePoint, second: StrokePoint): Boolean =
        first.position == second.position && first.pressure == second.pressure &&
            first.tiltX == second.tiltX && first.tiltY == second.tiltY
}
