package com.dsq.rebackground.paint.brush

import com.dsq.rebackground.paint.stroke.StrokeFrame
import com.dsq.rebackground.paint.stroke.StrokePoint
import kotlin.math.PI

data class DynamicsEvaluation(
    val diameterDocumentUnits: Float,
    val opacityFactor: Float,
    val flowFactor: Float,
    val aspectRatio: Float,
    val rotationRadians: Float,
    val rawSpeedDocumentUnitsPerSecond: Float,
    val filteredSpeedDocumentUnitsPerSecond: Float,
    val normalizedSpeed: Float,
    val tiltMagnitude: Float,
    val tiltAzimuthRadians: Float,
)

object DynamicsEvaluator {
    fun evaluate(
        definition: BrushDefinition,
        point: StrokePoint,
        frame: StrokeFrame,
        previous: BrushRuntimeState,
    ): DynamicsEvaluation {
        val dynamics = definition.dynamics
        val elapsedMillis = previous.lastTimestampMillis
            ?.let { (point.timestampMillis - it).coerceAtLeast(0L) } ?: 0L
        val distance = previous.lastPosition?.distanceTo(point.position) ?: 0f
        val speed = SpeedSensor.sample(
            distance,
            elapsedMillis,
            dynamics.maximumSpeed,
            previous.smoothedSpeedDocumentUnitsPerSecond,
            dynamics.speedSmoothingTimeConstantMillis,
        )
        val pressure = point.pressure.coerceIn(0f, 1f)
        val pressureSize = dynamics.pressureSizeCurve.evaluate(pressure)
        val pressureOpacity = dynamics.pressureOpacityCurve.evaluate(pressure)
        val pressureFlow = dynamics.pressureFlowCurve.evaluate(pressure)
        val speedSize = dynamics.speedSizeCurve.evaluate(speed.normalized)
        val speedOpacity = dynamics.speedOpacityCurve.evaluate(speed.normalized)
        val speedFlow = dynamics.speedFlowCurve.evaluate(speed.normalized)

        val sizePressureFactor = responseFactor(
            pressureSize,
            dynamics.pressureSizeInfluence,
            dynamics.minimumDiameterRatio,
        )
        val sizeSpeedFactor = descendingFactor(speedSize, dynamics.speedSizeInfluence)
        val opacityFactor = responseFactor(
            pressureOpacity,
            dynamics.pressureOpacityInfluence,
            dynamics.minimumOpacityRatio,
        ) * descendingFactor(speedOpacity, dynamics.speedOpacityInfluence)
        val flowFactor = responseFactor(
            pressureFlow,
            dynamics.pressureFlowInfluence,
            dynamics.minimumFlowRatio,
        ) * descendingFactor(speedFlow, dynamics.speedFlowInfluence)

        val tilt = TiltSensor.sample(point.tiltX, point.tiltY)
        val tiltResponse = dynamics.tiltCurve.evaluate(tilt.magnitude)
        val aspect = definition.tip.aspectRatio * (1f + tiltResponse * dynamics.tiltAspectInfluence)
        val pathRotation = if (frame.tangent.x != 0f || frame.tangent.y != 0f) frame.angle else 0f
        val rotation = when {
            !tilt.hasDirection -> pathRotation
            frame.tangent.x == 0f && frame.tangent.y == 0f -> tilt.azimuthRadians
            dynamics.tiltRotationInfluence > 0f -> blendAngle(
                pathRotation,
                tilt.azimuthRadians,
                tiltResponse * dynamics.tiltRotationInfluence,
            )
            else -> pathRotation
        }
        return DynamicsEvaluation(
            diameterDocumentUnits = definition.baseDiameterDocumentUnits *
                (sizePressureFactor * sizeSpeedFactor).coerceAtLeast(dynamics.minimumDiameterRatio),
            opacityFactor = opacityFactor.coerceIn(0f, 1f),
            flowFactor = flowFactor.coerceIn(0f, 1f),
            aspectRatio = aspect,
            rotationRadians = rotation,
            rawSpeedDocumentUnitsPerSecond = speed.rawDocumentUnitsPerSecond,
            filteredSpeedDocumentUnitsPerSecond = speed.smoothedDocumentUnitsPerSecond,
            normalizedSpeed = speed.normalized,
            tiltMagnitude = tilt.magnitude,
            tiltAzimuthRadians = tilt.azimuthRadians,
        )
    }

    private fun responseFactor(response: Float, influence: Float, minimum: Float): Float {
        val controlled = 1f + (response - 1f) * influence
        return minimum + (1f - minimum) * controlled
    }

    private fun descendingFactor(response: Float, influence: Float): Float =
        1f - response * influence

    private fun blendAngle(from: Float, to: Float, amount: Float): Float {
        var delta = to - from
        val twoPi = (2.0 * PI).toFloat()
        while (delta > PI.toFloat()) delta -= twoPi
        while (delta < -PI.toFloat()) delta += twoPi
        return from + delta * amount.coerceIn(0f, 1f)
    }
}
