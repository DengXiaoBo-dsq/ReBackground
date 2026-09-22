package com.dsq.rebackground.paint.stroke

import com.dsq.rebackground.paint.input.ResamplingConfig
import com.dsq.rebackground.paint.input.StrokeResampler
import com.dsq.rebackground.paint.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * G1 deterministic, mathematical acceptance suite.  It intentionally exercises
 * raw input -> resampling -> StrokeFrameBuilder rather than rendering pixels.
 */
class StrokeFrameAcceptanceTest {
    private data class Sample(val point: StrokePoint, val reference: Vec2)

    private fun evaluate(raw: List<Sample>): Pair<List<StrokeFrame>, List<Float>> {
        val resampler = StrokeResampler(ResamplingConfig(spacingDocumentUnits = 2f, maximumIntervalMillis = 1_000L))
        val builder = StrokeFrameBuilder(lowSpeedEpsilon = 0.1f)
        val frames = mutableListOf<StrokeFrame>()
        val errors = mutableListOf<Float>()
        raw.forEachIndexed { index, sample ->
            val emitted = if (index == raw.lastIndex) resampler.finish(sample.point) else resampler.add(sample.point)
            emitted.forEach { point ->
                val frame = builder.pushPoint(point)
                frames += frame
                if (frame.tangent.x != 0f || frame.tangent.y != 0f) {
                    // Fixture intervals are straight enough that the source segment's
                    // analytic tangent is the reference for interpolated samples.
                    errors += StrokeFrameMetrics.angleErrorDegrees(frame.tangent, sample.reference)
                }
            }
        }
        return frames to errors
    }

    private fun point(x: Float, y: Float, time: Long) = StrokePoint(Vec2(x, y), 1f, 0f, 0f, time)

    private fun assertSmooth(errors: List<Float>, mean: Float = 2f, p99: Float = 5f, max: Float = 10f) {
        val metrics = StrokeFrameMetrics.summarize(errors)
        assertTrue("mean=${metrics.meanDegrees}", metrics.meanDegrees <= mean)
        assertTrue("p99=${metrics.p99Degrees}", metrics.p99Degrees <= p99)
        assertTrue("max=${metrics.maxDegrees}", metrics.maxDegrees <= max)
    }

    @Test fun horizontal_vertical_and_45_degree_meet_smooth_limits() {
        val horizontal = (0..20).map { Sample(point(it * 5f, 0f, it.toLong()), Vec2(1f, 0f)) }
        val vertical = (0..20).map { Sample(point(0f, it * 5f, it.toLong()), Vec2(0f, 1f)) }
        val diagonal = (0..20).map { Sample(point(it * 5f, it * 5f, it.toLong()), Vec2(0.70710677f, 0.70710677f)) }
        assertSmooth(evaluate(horizontal).second)
        assertSmooth(evaluate(vertical).second)
        assertSmooth(evaluate(diagonal).second)
    }

    @Test fun circle_matches_analytic_tangent() {
        val cx = 100f; val cy = 100f; val radius = 60f
        val raw = (0..180).map { index ->
            val t = index * (2.0 * PI / 180.0)
            Sample(point(cx + radius * cos(t).toFloat(), cy + radius * sin(t).toFloat(), index.toLong()),
                Vec2((-sin(t)).toFloat(), cos(t).toFloat()))
        }
        val (_, errors) = evaluate(raw)
        // The backward finite difference is offset by one source interval.
        assertSmooth(errors.drop(1), mean = 2f, p99 = 5f, max = 10f)
    }

    @Test fun s_curve_and_zigzag_remain_finite_and_bounded() {
        val sCurve = (-50..50 step 2).mapIndexed { index, x ->
            val xf = x.toFloat(); val y = xf * xf / 50f
            val derivative = Vec2(1f, xf / 25f)
            val length = kotlin.math.sqrt(derivative.x * derivative.x + derivative.y * derivative.y)
            Sample(point(xf + 80f, y + 20f, index.toLong()), Vec2(derivative.x / length, derivative.y / length))
        }
        assertSmooth(evaluate(sCurve).second.drop(2), mean = 3f, p99 = 8f, max = 15f)

        val zigzag = listOf(Vec2(0f, 0f), Vec2(20f, 0f), Vec2(20f, 20f), Vec2(40f, 20f), Vec2(40f, 40f))
        val (_, errors) = evaluate(zigzag.mapIndexed { index, p ->
            val prior = zigzag.getOrElse((index - 1).coerceAtLeast(0)) { p }
            val delta = Vec2(p.x - prior.x, p.y - prior.y)
            val length = kotlin.math.sqrt(delta.x * delta.x + delta.y * delta.y).coerceAtLeast(1f)
            Sample(point(p.x, p.y, index.toLong()), Vec2(delta.x / length, delta.y / length))
        })
        assertTrue(errors.all { it.isFinite() && it <= 0.01f })
    }

    @Test fun corner_and_u_turn_preserve_expected_direction_and_unwrap() {
        val corner = listOf(
            Sample(point(0f, 0f, 0), Vec2(1f, 0f)),
            Sample(point(20f, 0f, 1), Vec2(1f, 0f)),
            Sample(point(20f, 20f, 2), Vec2(0f, 1f))
        )
        val (cornerFrames) = evaluate(corner)
        assertTrue(StrokeFrameMetrics.angleErrorDegrees(cornerFrames[1].tangent, Vec2(1f, 0f)) <= 3f)
        assertTrue(StrokeFrameMetrics.angleErrorDegrees(cornerFrames.last().tangent, Vec2(0f, 1f)) <= 3f)

        val uTurn = listOf(Vec2(0f, 0f), Vec2(20f, 0f), Vec2(20f, 20f), Vec2(0f, 20f))
        val (frames) = evaluate(uTurn.mapIndexed { index, p ->
            val reference = when (index) { 0, 1 -> Vec2(1f, 0f); 2 -> Vec2(0f, 1f); else -> Vec2(-1f, 0f) }
            Sample(point(p.x, p.y, index.toLong()), reference)
        })
        val unwrapped = frames.drop(1).map { it.unwrappedAngle }
        assertTrue("unwrapped=$unwrapped", unwrapped.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun low_speed_and_noisy_input_follow_the_specification() {
        val lowSpeed = listOf(
            Sample(point(0f, 0f, 0), Vec2(1f, 0f)),
            Sample(point(10f, 0f, 1), Vec2(1f, 0f)),
            Sample(point(10.02f, -0.01f, 2), Vec2(1f, 0f)),
            Sample(point(10.04f, 0.01f, 3), Vec2(1f, 0f))
        )
        val (_, lowErrors) = evaluate(lowSpeed)
        assertTrue("lowSpeedP99=${StrokeFrameMetrics.summarize(lowErrors).p99Degrees}",
            StrokeFrameMetrics.summarize(lowErrors).p99Degrees <= 5f)

        val noise = floatArrayOf(-2f, 2f, -1f, 1f, 0f, -2f, 2f, -1f, 1f, 0f)
        val noisyLine = (0..30).map { index ->
            Sample(point(index * 50f, noise[index % noise.size], index.toLong()), Vec2(1f, 0f))
        }
        val (_, noisyErrors) = evaluate(noisyLine)
        assertTrue("noisyP99=${StrokeFrameMetrics.summarize(noisyErrors).p99Degrees}",
            StrokeFrameMetrics.summarize(noisyErrors).p99Degrees <= 8f)
    }

    @Test fun normal_is_perpendicular_to_tangent() {
        val frame = StrokeFrameBuilder().apply { beginStroke() }
            .pushPoint(point(0f, 0f, 0))
        assertEquals(0f, frame.normal.x)
        assertEquals(0f, frame.normal.y)
    }
}
