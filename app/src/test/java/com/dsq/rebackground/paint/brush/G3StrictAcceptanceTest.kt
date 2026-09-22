package com.dsq.rebackground.paint.brush

import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.StrokeFrame
import com.dsq.rebackground.paint.stroke.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class G3StrictAcceptanceTest {
    private val generator = BrushGenerator()
    private val point = StrokePoint(Vec2(20f, 20f), 1f, 0f, 0f, 0L)

    @Test
    fun shape_and_grain_are_independent_for_all_four_combinations() {
        val shapes = listOf<BrushTip>(BrushTip.Round, BrushTip.Ellipse(2f))
        val grains = listOf(
            GrainSource.Texture("grain-a", 2f, depth = 0.5f, initialPhaseTexels = 3f),
            GrainSource.Texture("grain-b", 5f, depth = 0.8f, initialPhaseTexels = 7f),
        )
        val outputs = shapes.flatMapIndexed { shapeIndex, shape ->
            grains.mapIndexed { grainIndex, grain ->
                (shapeIndex to grainIndex) to generate(shape, grain, arcLength = 100f, angle = 0.4f)
            }
        }.toMap()

        for (shapeIndex in shapes.indices) {
            val a = outputs.getValue(shapeIndex to 0)
            val b = outputs.getValue(shapeIndex to 1)
            assertEquals(a.diameterDocumentUnits, b.diameterDocumentUnits, 0f)
            assertEquals(a.aspectRatio, b.aspectRatio, 0f)
            assertEquals(a.rotationRadians, b.rotationRadians, 0f)
        }
        for (grainIndex in grains.indices) {
            val a = outputs.getValue(0 to grainIndex)
            val b = outputs.getValue(1 to grainIndex)
            assertEquals(a.grainScaleDocumentUnitsPerTexel, b.grainScaleDocumentUnitsPerTexel, 0f)
            assertEquals(a.grainPhaseTexels, b.grainPhaseTexels, 0f)
            assertEquals(a.grainRotationRadians, b.grainRotationRadians, 0f)
        }
    }

    @Test
    fun phase_drift_is_below_gate_over_1000_pixels_and_never_resets() {
        val grain = GrainSource.Texture("grain", 3.7f, initialPhaseTexels = 2.25f)
        val positions = (0..1000).map { it.toFloat() }
        val errors = positions.map { distance ->
            val actual = GrainMapping.phaseTexels(grain, distance)
            val expected = 2.25 + distance.toDouble() / 3.7
            abs(actual.toDouble() - expected)
        }.sorted()
        assertTrue(errors.last() <= 0.5)
        assertTrue(errors[(errors.size * 0.99).toInt().coerceAtMost(errors.lastIndex)] <= 0.25)
        val checkpoints = listOf(100f, 200f, 500f, 750f, 1000f).map { GrainMapping.phaseTexels(grain, it) }
        assertTrue(checkpoints.zipWithNext().all { (a, b) -> b > a && b - a > 1f })
    }

    @Test
    fun rake_orientation_follows_stroke_frame_without_sign_error() {
        val grain = GrainSource.Texture(
            "directional",
            2f,
            orientationOffsetRadians = (PI / 4.0).toFloat(),
        )
        val angles = listOf(0f, (PI / 2).toFloat(), (PI / 4).toFloat()) +
            (0 until 360 step 5).map { Math.toRadians(it.toDouble()).toFloat() }
        val errors = angles.map { angle ->
            val actual = GrainMapping.worldRotationRadians(grain, angle)
            abs(actual - (angle + (PI / 4.0).toFloat()))
        }.sorted()
        assertTrue(errors.average() <= Math.toRadians(3.0))
        assertTrue(errors[(errors.size * 0.99).toInt().coerceAtMost(errors.lastIndex)] <= Math.toRadians(8.0))
    }

    @Test
    fun interpolated_stamp_phase_matches_arc_length_formula() {
        val end = 100f
        val values = (0..20).map { index ->
            val fraction = index / 20f
            GrainMapping.interpolatePhaseTexels(end, 40f, fraction, 2f)
        }
        values.forEachIndexed { index, value ->
            assertEquals(80f + index.toFloat(), value, 1e-5f)
        }
    }

    private fun generate(shape: BrushTip, grain: GrainSource.Texture, arcLength: Float, angle: Float): BrushStamp {
        val definition = BrushDefinition("g3", "G3", 20f, tip = shape, grain = grain)
        val frame = StrokeFrame(
            position = point.position,
            tangent = Vec2(1f, 0f),
            angle = angle,
            unwrappedAngle = angle,
            arcLength = arcLength,
            speed = 0f,
            curvature = 0f,
            timestampMillis = 0L,
        )
        return generator.generate(definition, point, frame).stamp
    }
}
