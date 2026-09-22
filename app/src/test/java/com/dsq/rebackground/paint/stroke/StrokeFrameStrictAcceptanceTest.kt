package com.dsq.rebackground.paint.stroke

import com.dsq.rebackground.paint.input.ResamplingConfig
import com.dsq.rebackground.paint.input.StrokeResampler
import com.dsq.rebackground.paint.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** G1-SUITE-v1: raw input -> resampling -> frame solve -> mathematical reference. */
class StrokeFrameStrictAcceptanceTest {
    private fun point(x: Float, y: Float, time: Long) = StrokePoint(Vec2(x, y), 1f, 0f, 0f, time)

    private fun pipeline(raw: List<StrokePoint>, spacing: Float = 2f): List<StrokeFrame> {
        val resampler = StrokeResampler(ResamplingConfig(spacing, 1_000L))
        val sampled = raw.flatMapIndexed { index, p ->
            if (index == raw.lastIndex) resampler.finish(p) else resampler.add(p)
        }
        return StrokeFrameSolver().solve(sampled)
    }

    private fun assertSummary(
        name: String,
        frames: List<StrokeFrame>,
        reference: (Vec2) -> Vec2,
        mean: Float,
        p99: Float,
        max: Float,
        dropEnds: Int = 0
    ) {
        val evaluated = frames.drop(dropEnds).dropLast(dropEnds).filter { it.tangent != Vec2(0f, 0f) }
        val errors = evaluated.map { StrokeFrameMetrics.angleErrorDegrees(it.tangent, reference(it.position)) }
        val summary = StrokeFrameMetrics.summarize(errors)
        assertTrue("$name mean=${summary.meanDegrees}", summary.meanDegrees <= mean)
        assertTrue("$name p99=${summary.p99Degrees}", summary.p99Degrees <= p99)
        assertTrue("$name max=${summary.maxDegrees}", summary.maxDegrees <= max)
        println("G1_METRIC $name samples=${summary.sampleCount} mean=${summary.meanDegrees} p99=${summary.p99Degrees} max=${summary.maxDegrees}")
        evaluated.forEach { frame ->
            assertTrue("$name non-finite tangent", frame.tangent.isFinite)
            assertTrue("$name normal not perpendicular",
                abs(frame.tangent.x * frame.normal.x + frame.tangent.y * frame.normal.y) < 1e-5f)
        }
    }

    private fun line(dx: Float, dy: Float) = (0..40).map { i -> point(20f + dx * i, 20f + dy * i, i * 8L) }

    @Test fun g1_horizontal() = assertSummary("HORIZONTAL", pipeline(line(3f, 0f)), { Vec2(1f, 0f) }, 2f, 5f, 10f)
    @Test fun g1_vertical() = assertSummary("VERTICAL", pipeline(line(0f, 3f)), { Vec2(0f, 1f) }, 2f, 5f, 10f)

    @Test fun g1_45deg() {
        val unit = 0.70710677f
        assertSummary("45DEG", pipeline(line(3f, 3f)), { Vec2(unit, unit) }, 2f, 5f, 10f)
    }

    @Test fun g1_circle() {
        val center = Vec2(100f, 100f)
        val raw = (0..240).map { i ->
            val t = i * 2.0 * PI / 240.0
            point(center.x + 60f * cos(t).toFloat(), center.y + 60f * sin(t).toFloat(), i * 8L)
        }
        assertSummary("CIRCLE", pipeline(raw), { p ->
            val x = (p.x - center.x) / 60f
            val y = (p.y - center.y) / 60f
            Vec2(-y, x)
        }, 2f, 5f, 10f, dropEnds = 3)
    }

    @Test fun g1_s_curve() {
        val raw = (0..120).map { i ->
            val x = i * 2f
            point(x, 100f + 30f * sin(x / 30f), i * 8L)
        }
        assertSummary("S_CURVE", pipeline(raw), { p -> Vec2(1f, cos(p.x / 30f)) }, 3f, 8f, 15f, dropEnds = 3)
    }

    @Test fun g1_zigzag() {
        val raw = listOf(Vec2(0f, 0f), Vec2(40f, 0f), Vec2(40f, 40f),
            Vec2(80f, 40f), Vec2(80f, 80f), Vec2(120f, 80f))
            .mapIndexed { i, p -> point(p.x, p.y, i * 30L) }
        val frames = pipeline(raw)
        assertTrue(frames.all { it.tangent.isFinite && it.angle.isFinite() && it.unwrappedAngle.isFinite() })
        assertEquals(0, wrapViolations(frames))
        println("G1_METRIC ZIGZAG wrapViolations=${wrapViolations(frames)} samples=${frames.size}")
    }

    @Test fun g1_corner90() {
        val raw = (0..20).map { point(it * 2f, 0f, it * 8L) } +
            (1..20).map { point(40f, it * 2f, (20 + it) * 8L) }
        val frames = pipeline(raw)
        val corner = frames.indexOfFirst { abs(it.position.x - 40f) < 0.01f && abs(it.position.y) < 0.01f }
        assertTrue(corner > 0)
        val before = StrokeFrameMetrics.angleErrorDegrees(frames[corner - 1].tangent, Vec2(1f, 0f))
        val at = StrokeFrameMetrics.angleErrorDegrees(frames[corner].tangent, Vec2(0f, 1f))
        val after = StrokeFrameMetrics.angleErrorDegrees(frames[corner + 1].tangent, Vec2(0f, 1f))
        assertTrue(before <= 3f)
        assertTrue(at <= 3f)
        assertTrue(after <= 3f)
        assertEquals(0, wrapViolations(frames))
        println("G1_METRIC CORNER90 before=$before at=$at after=$after wrapViolations=${wrapViolations(frames)}")
    }

    @Test fun g1_u_turn() {
        val raw = (0..20).map { point(it * 2f, 0f, it * 8L) } +
            (1..20).map { point(40f, it * 2f, (20 + it) * 8L) } +
            (1..20).map { point(40f - it * 2f, 40f, (40 + it) * 8L) }
        val frames = pipeline(raw)
        assertEquals(0, wrapViolations(frames))
        val angles = frames.map { it.unwrappedAngle }
        assertTrue("U_TURN=$angles", angles.zipWithNext().all { (a, b) -> b + 1e-4f >= a })
        assertTrue(abs(angles.last() - PI.toFloat()) <= Math.toRadians(3.0).toFloat())
        println("G1_METRIC U_TURN finalDegrees=${Math.toDegrees(angles.last().toDouble())} wrapViolations=${wrapViolations(frames)}")
    }

    @Test fun g1_low_speed() {
        val raw = (0..10).map { point(it * 2f, 0f, it * 8L) } + listOf(
            point(20.02f, -0.01f, 96L), point(20.04f, 0.01f, 104L), point(20.06f, 0f, 112L))
        val errors = pipeline(raw).drop(1).map { StrokeFrameMetrics.angleErrorDegrees(it.tangent, Vec2(1f, 0f)) }
        val summary = StrokeFrameMetrics.summarize(errors)
        assertTrue("LOW_SPEED p99=${summary.p99Degrees}", summary.p99Degrees <= 5f)
        println("G1_METRIC LOW_SPEED samples=${summary.sampleCount} mean=${summary.meanDegrees} p99=${summary.p99Degrees} max=${summary.maxDegrees}")
    }

    @Test fun g1_noisy_input() {
        listOf(0.5f, 1f, 2f).forEach { amplitude ->
            var seed = 0x1234ABCDL
            val raw = (0..100).map { i ->
                seed = (seed * 1103515245L + 12345L) and 0x7fffffff
                val noise = ((seed.toFloat() / 0x7fffffffL.toFloat()) * 2f - 1f) * amplitude
                point(i * 4f, noise, i * 8L)
            }
            val errors = pipeline(raw).drop(5).dropLast(5).map {
                StrokeFrameMetrics.angleErrorDegrees(it.tangent, Vec2(1f, 0f))
            }
            val summary = StrokeFrameMetrics.summarize(errors)
            assertTrue("NOISY_INPUT amplitude=$amplitude p99=${summary.p99Degrees}", summary.p99Degrees <= 8f)
            println("G1_METRIC NOISY_INPUT amplitude=$amplitude samples=${summary.sampleCount} mean=${summary.meanDegrees} p99=${summary.p99Degrees} max=${summary.maxDegrees}")
        }
    }

    @Test fun g1_resampler_spacing_error() {
        val frames = pipeline((0..30).map { i -> point(i * 3.7f, 0f, i * 8L) })
        val distances = frames.zipWithNext().map { (a, b) -> a.position.distanceTo(b.position) }.dropLast(1)
        val maxRelativeError = distances.maxOf { abs(it - 2f) / 2f }
        assertTrue("spacingError=$maxRelativeError", maxRelativeError <= 0.05f)
        assertTrue(frames.zipWithNext().all { (a, b) -> b.arcLength + 1e-5f >= a.arcLength })
        println("G1_METRIC RESAMPLER spacingErrorPercent=${maxRelativeError * 100f} samples=${distances.size}")
    }

    private fun wrapViolations(frames: List<StrokeFrame>): Int = frames.zipWithNext().count { (a, b) ->
        abs(b.unwrappedAngle - a.unwrappedAngle) > PI.toFloat() + 1e-4f
    }
}
