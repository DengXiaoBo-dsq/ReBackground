package com.dsq.rebackground.paint

import com.dsq.rebackground.paint.brush.BrushDefinition
import com.dsq.rebackground.paint.brush.BrushDynamics
import com.dsq.rebackground.paint.brush.BrushGenerator
import com.dsq.rebackground.paint.brush.BrushTip
import com.dsq.rebackground.paint.brush.InMemoryBrushRepository
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushModelTest {
    @Test fun generator_creates_document_space_geometry_from_pressure() {
        val brush = BrushDefinition("round", "Round", 20f, dynamics = BrushDynamics(minimumDiameterRatio = .2f))
        val output = BrushGenerator().generate(brush, StrokePoint(Vec2(10f, 20f), .5f, 0f, 0f, 10))
        assertEquals(Vec2(10f, 20f), output.stamp.center)
        assertEquals(12f, output.stamp.diameterDocumentUnits)
    }

    @Test fun tilt_controls_stamp_rotation_without_entering_pigment_model() {
        val brush = BrushDefinition("flat", "Flat", 10f, BrushTip.Ellipse(.5f), dynamics = BrushDynamics(tiltAspectInfluence = 1f))
        val output = BrushGenerator().generate(brush, StrokePoint(Vec2(0f, 0f), 1f, 1f, 0f, 0))
        assertEquals(0f, output.stamp.rotationRadians)
        assertEquals(1f, output.stamp.aspectRatio)
    }

    @Test fun repository_replaces_by_stable_id() {
        val first = BrushDefinition("ink", "Old", 5f)
        val replacement = BrushDefinition("ink", "New", 9f)
        val repository = InMemoryBrushRepository(listOf(first))
        repository.put(replacement)
        assertEquals(1, repository.all().size)
        assertSame(replacement, repository.find("ink"))
    }

    @Test fun runtime_speed_is_derived_from_document_distance_and_time() {
        val generator = BrushGenerator()
        val brush = BrushDefinition("round", "Round", 10f)
        val first = generator.generate(brush, StrokePoint(Vec2(0f, 0f), 1f, 0f, 0f, 0))
        val second = generator.generate(brush, StrokePoint(Vec2(10f, 0f), 1f, 0f, 0f, 100), first.nextState)
        assertEquals(100f, second.nextState.speedDocumentUnitsPerSecond)
        assertTrue(second.stamp.diameterDocumentUnits > 0f)
    }
}
