package com.dsq.rebackground.paint.brush

import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

data class SpeedSample(
    val rawDocumentUnitsPerSecond: Float,
    val clampedDocumentUnitsPerSecond: Float,
    val normalized: Float,
    val smoothedDocumentUnitsPerSecond: Float,
)

object SpeedSensor {
    fun sample(
        distanceDocumentUnits: Float,
        elapsedMillis: Long,
        maximumSpeed: Float,
        previousSmoothedSpeed: Float,
        smoothingTimeConstantMillis: Float,
    ): SpeedSample {
        require(distanceDocumentUnits.isFinite() && distanceDocumentUnits >= 0f)
        require(elapsedMillis >= 0L)
        require(maximumSpeed.isFinite() && maximumSpeed > 0f)
        require(previousSmoothedSpeed.isFinite() && previousSmoothedSpeed >= 0f)
        require(smoothingTimeConstantMillis.isFinite() && smoothingTimeConstantMillis >= 0f)
        // Duplicate/coalesced timestamps carry no new velocity information.
        // Hold the previous filtered value instead of injecting a false zero spike.
        val raw = if (elapsedMillis > 0L) {
            distanceDocumentUnits * 1_000f / elapsedMillis
        } else {
            previousSmoothedSpeed
        }
        val clamped = raw.coerceIn(0f, maximumSpeed)
        val smoothed = if (elapsedMillis == 0L || smoothingTimeConstantMillis == 0f) {
            clamped
        } else {
            val alpha = (1.0 - exp(-elapsedMillis.toDouble() / smoothingTimeConstantMillis)).toFloat()
            previousSmoothedSpeed + (clamped - previousSmoothedSpeed) * alpha
        }
        return SpeedSample(raw, clamped, (smoothed / maximumSpeed).coerceIn(0f, 1f), smoothed)
    }
}

data class TiltSample(val magnitude: Float, val azimuthRadians: Float, val hasDirection: Boolean)

object TiltSensor {
    fun sample(tiltX: Float, tiltY: Float): TiltSample {
        require(tiltX.isFinite() && tiltY.isFinite())
        val magnitude = sqrt(tiltX * tiltX + tiltY * tiltY).coerceIn(0f, 1f)
        val hasDirection = magnitude > 1e-6f
        return TiltSample(magnitude, if (hasDirection) atan2(tiltY, tiltX) else 0f, hasDirection)
    }
}
