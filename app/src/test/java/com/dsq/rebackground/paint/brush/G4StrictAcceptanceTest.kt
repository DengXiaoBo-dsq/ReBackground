package com.dsq.rebackground.paint.brush

import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.StrokeFrame
import com.dsq.rebackground.paint.stroke.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class G4StrictAcceptanceTest {
    @Test
    fun linear_curve_meets_101_point_gate() {
        val pairs = (0..100).map { it / 100f }.map { it to DynamicsCurve.Linear.evaluate(it) }
        val mae = pairs.map { abs(it.first - it.second) }.average()
        val maxError = pairs.maxOf { abs(it.first - it.second) }
        val violations = pairs.zipWithNext().count { (a, b) -> b.second < a.second }
        assertEquals(0.0, mae, 0.0)
        assertEquals(0f, maxError, 0f)
        assertEquals(0, violations)
        assertTrue(rSquared(pairs) >= 0.99)
    }

    @Test
    fun general_curves_match_independent_references() {
        val curves = listOf(
            DynamicsCurve.Soft to { x: Double -> 1.0 - (1.0 - x) * (1.0 - x) },
            DynamicsCurve.Hard to { x: Double -> x * x },
            DynamicsCurve.Inverse to { x: Double -> 1.0 - x },
            DynamicsCurve.Bezier(0.25f, 0.1f, 0.25f, 1f) to
                { x: Double -> referenceBezier(x, 0.25, 0.1, 0.25, 1.0) },
        )
        curves.forEach { (curve, reference) ->
            val errors = (0..100).map { it / 100.0 }
                .map { abs(curve.evaluate(it.toFloat()) - reference(it)) }
            assertTrue(errors.average() <= 0.01)
            assertTrue(errors.max() <= 0.03)
        }
    }

    @Test
    fun pressure_endpoints_independently_control_size_opacity_and_flow() {
        val definition = BrushDefinition(
            "pressure", "Pressure", 100f,
            opacity = 0.8f,
            flow = 0.6f,
            dynamics = BrushDynamics(
                minimumDiameterRatio = 0.2f,
                pressureSizeInfluence = 1f,
                pressureOpacityInfluence = 1f,
                minimumOpacityRatio = 0.1f,
                pressureFlowInfluence = 1f,
                minimumFlowRatio = 0.05f,
                speedSmoothingTimeConstantMillis = 0f,
            ),
        )
        val low = generate(definition, pressure = 0f)
        val high = generate(definition, pressure = 1f)
        assertEquals(20f, low.stamp.diameterDocumentUnits, 1e-5f)
        assertEquals(100f, high.stamp.diameterDocumentUnits, 1e-5f)
        assertEquals(0.1f, low.opacityFactor, 1e-6f)
        assertEquals(1f, high.opacityFactor, 1e-6f)
        assertEquals(0.05f, low.flowFactor, 1e-6f)
        assertEquals(1f, high.flowFactor, 1e-6f)
    }

    @Test
    fun speed_sensor_uses_known_distance_and_timestamp_and_response_is_monotonic() {
        val samples = listOf(10L, 20L, 40L).map {
            SpeedSensor.sample(10f, it, 1_000f, 0f, 0f)
        }
        assertEquals(listOf(1_000f, 500f, 250f), samples.map { it.rawDocumentUnitsPerSecond })
        assertEquals(listOf(1f, 0.5f, 0.25f), samples.map { it.normalized })
        val descending = samples.reversed().map { 1f - it.normalized }
        assertEquals(0, descending.zipWithNext().count { (a, b) -> b > a })
        val smoothed = SpeedSensor.sample(10f, 10L, 1_000f, 0f, 35f)
        assertTrue(smoothed.smoothedDocumentUnitsPerSecond in 0f..1_000f)
        assertTrue(smoothed.smoothedDocumentUnitsPerSecond.isFinite())
        val duplicateTimestamp = SpeedSensor.sample(10f, 0L, 1_000f, 321f, 35f)
        assertEquals(321f, duplicateTimestamp.rawDocumentUnitsPerSecond, 0f)
        assertEquals(321f, duplicateTimestamp.smoothedDocumentUnitsPerSecond, 0f)
    }

    @Test
    fun tilt_sensor_and_mapping_meet_orientation_gate() {
        val definition = BrushDefinition(
            "tilt", "Tilt", 20f,
            dynamics = BrushDynamics(tiltAspectInfluence = 1f, tiltRotationInfluence = 1f),
        )
        val errors = (0 until 360 step 5).map { degrees ->
            val radians = Math.toRadians(degrees.toDouble())
            val point = StrokePoint(
                Vec2(0f, 0f), 1f,
                (cos(radians) * 0.75).toFloat(),
                (sin(radians) * 0.75).toFloat(),
                0L,
            )
            val output = BrushGenerator().generate(definition, point)
            angularError(output.stamp.rotationRadians.toDouble(), radians)
        }
        assertTrue(Math.toDegrees(errors.average()) <= 3.0)
        assertTrue(Math.toDegrees(errors.max()) <= 3.0)
        val sensor = TiltSensor.sample(0.6f, 0.8f)
        assertEquals(1f, sensor.magnitude, 1e-6f)
        assertEquals(kotlin.math.atan2(0.8f, 0.6f), sensor.azimuthRadians, 1e-6f)
    }

    private fun generate(definition: BrushDefinition, pressure: Float): BrushGenerator.Output {
        val point = StrokePoint(Vec2(0f, 0f), pressure, 0f, 0f, 0L)
        return BrushGenerator().generate(definition, point, frame(point))
    }

    private fun frame(point: StrokePoint) = StrokeFrame(
        point.position, Vec2(0f, 0f), 0f, 0f, 0f, 0f, 0f, point.timestampMillis,
    )

    private fun rSquared(points: List<Pair<Float, Float>>): Double {
        val mean = points.map { it.second.toDouble() }.average()
        val residual = points.sumOf { (x, y) -> (y - x).toDouble().let { it * it } }
        val total = points.sumOf { (_, y) -> (y - mean).let { it * it } }
        return if (total == 0.0) 1.0 else 1.0 - residual / total
    }

    private fun referenceBezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        fun cubic(t: Double, a: Double, b: Double): Double {
            val oneMinus = 1.0 - t
            return 3.0 * oneMinus * oneMinus * t * a + 3.0 * oneMinus * t * t * b + t * t * t
        }
        var low = 0.0
        var high = 1.0
        repeat(60) {
            val t = (low + high) * 0.5
            if (cubic(t, x1, x2) < x) low = t else high = t
        }
        return cubic((low + high) * 0.5, y1, y2)
    }

    private fun angularError(actual: Double, expected: Double): Double {
        var delta = abs(actual - expected) % (2.0 * PI)
        if (delta > PI) delta = 2.0 * PI - delta
        return delta
    }
}
