package com.dsq.rebackground.paint

import com.dsq.rebackground.paint.core.PaintDocument
import com.dsq.rebackground.paint.core.PaintEngine
import com.dsq.rebackground.paint.core.PaintLayer
import com.dsq.rebackground.paint.input.DocumentSpaceTransform
import com.dsq.rebackground.paint.input.InputQueue
import com.dsq.rebackground.paint.input.PointerInputProcessor
import com.dsq.rebackground.paint.input.PointerPhase
import com.dsq.rebackground.paint.input.PointerSample
import com.dsq.rebackground.paint.input.QueuedPointerSample
import com.dsq.rebackground.paint.input.ResamplingConfig
import com.dsq.rebackground.paint.input.StrokeInterpolator
import com.dsq.rebackground.paint.input.StrokeResampler
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.Stroke
import com.dsq.rebackground.paint.stroke.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaintPipelineTest {
    private fun sample(id: Int = 0, x: Float, y: Float = 0f, pressure: Float = 1f, time: Long = 0L) =
        PointerSample(id, Vec2(x, y), pressure, 0f, 0f, time)

    @Test fun p0_document_keeps_semantic_layers() {
        val document = PaintDocument(100, 100).also { it.addLayer(PaintLayer("ink")) }
        assertEquals(document, PaintEngine().open(document).document)
    }

    @Test fun pointer_sample_clamps_pressure_at_stroke_boundary() {
        assertEquals(1f, sample(x = 0f, pressure = 2f).toStrokePoint().pressure)
        assertEquals(0f, sample(x = 0f, pressure = -1f).toStrokePoint().pressure)
    }

    @Test fun non_finite_pressure_and_tilt_use_safe_fallbacks() {
        val point = PointerSample(0, Vec2(0f, 0f), Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, -1).toStrokePoint()
        assertEquals(1f, point.pressure)
        assertEquals(0f, point.tiltX)
        assertEquals(0f, point.tiltY)
        assertEquals(0L, point.timestampMillis)
    }

    @Test(expected = IllegalArgumentException::class) fun non_finite_document_coordinates_are_rejected() {
        PointerSample(0, Vec2(Float.NaN, 0f), 1f, 0f, 0f, 0)
    }

    @Test fun input_queue_is_fifo_and_has_bounded_capacity() {
        val queue = InputQueue(2)
        val first = QueuedPointerSample(PointerPhase.DOWN, sample(x = 0f))
        val second = QueuedPointerSample(PointerPhase.MOVE, sample(x = 1f))
        assertTrue(queue.offer(first))
        assertTrue(queue.offer(second))
        assertFalse(queue.offer(QueuedPointerSample(PointerPhase.UP, sample(x = 2f))))
        assertEquals(first, queue.poll())
        assertEquals(second, queue.poll())
        assertTrue(queue.isEmpty())
    }

    @Test fun resampler_spaces_fast_motion_and_preserves_end() {
        val resampler = StrokeResampler(ResamplingConfig(spacingDocumentUnits = 2f, maximumIntervalMillis = 1000))
        resampler.add(sample(x = 0f).toStrokePoint())
        val result = resampler.finish(sample(x = 10f, time = 1).toStrokePoint())
        assertEquals(listOf(2f, 4f, 6f, 8f, 10f), result.map { it.position.x })
    }

    @Test fun interpolator_preserves_pressure_and_tilt_continuity() {
        val from = StrokePoint(Vec2(0f, 0f), 0f, 0f, 0f, 0)
        val to = StrokePoint(Vec2(10f, 0f), 1f, 1f, -1f, 10)
        val middle = StrokeInterpolator().pointAt(from, to, .5f)
        assertEquals(.5f, middle.pressure)
        assertEquals(.5f, middle.tiltX)
        assertEquals(-.5f, middle.tiltY)
    }

    @Test fun down_move_up_builds_monotonic_document_space_stroke() {
        val output = mutableListOf<Stroke>()
        val processor = PointerInputProcessor(
            transform = DocumentSpaceTransform { x, y -> Vec2(x * 2f, y * 3f) },
            resamplingConfig = ResamplingConfig(2f, 1000), onStrokeFinished = output::add
        )
        processor.submitView(PointerPhase.DOWN, 0, 1f, 2f, timestampMillis = 10)
        processor.submitView(PointerPhase.MOVE, 0, 4f, 2f, timestampMillis = 9)
        processor.submitView(PointerPhase.UP, 0, 5f, 2f, timestampMillis = 12)
        assertEquals(1, output.size)
        assertEquals(Vec2(2f, 6f), output.single().points.first().position)
        assertTrue(output.single().points.zipWithNext().all { it.second.timestampMillis >= it.first.timestampMillis })
        assertEquals(0, processor.activePointerCount())
    }

    @Test fun cancel_and_multiple_pointers_are_isolated() {
        val complete = mutableListOf<Stroke>()
        val cancelled = mutableListOf<Stroke>()
        val processor = PointerInputProcessor(onStrokeFinished = complete::add, onStrokeCancelled = cancelled::add)
        processor.submit(PointerPhase.DOWN, sample(1, 0f, time = 0))
        processor.submit(PointerPhase.DOWN, sample(2, 0f, time = 0))
        processor.submit(PointerPhase.CANCEL, sample(1, 2f, time = 2))
        processor.submit(PointerPhase.UP, sample(2, 2f, time = 2))
        assertEquals(1, cancelled.size)
        assertEquals(1, complete.size)
        assertFalse(cancelled.single().points.isEmpty())
    }

    @Test fun single_point_stroke_has_zero_length() {
        val strokes = mutableListOf<Stroke>()
        val processor = PointerInputProcessor(onStrokeFinished = strokes::add)
        processor.submit(PointerPhase.DOWN, sample(x = 3f, time = 1))
        processor.submit(PointerPhase.UP, sample(x = 3f, time = 2))
        assertEquals(1, strokes.single().points.size)
        assertEquals(0f, strokes.single().length)
    }

    @Test fun queued_samples_preserve_historical_time_order() {
        val strokes = mutableListOf<Stroke>()
        val processor = PointerInputProcessor(resamplingConfig = ResamplingConfig(.1f, 1000), onStrokeFinished = strokes::add)
        processor.submit(PointerPhase.DOWN, sample(x = 0f, time = 1))
        processor.submit(PointerPhase.MOVE, sample(x = 1f, time = 2))
        processor.submit(PointerPhase.MOVE, sample(x = 2f, time = 3))
        processor.submit(PointerPhase.UP, sample(x = 3f, time = 4))
        assertEquals(listOf(1L, 2L, 3L, 4L), strokes.single().points.map { it.timestampMillis }.distinct())
    }

    @Test fun move_without_a_down_is_ignored() {
        val strokes = mutableListOf<Stroke>()
        val processor = PointerInputProcessor(onStrokeFinished = strokes::add)
        processor.submit(PointerPhase.MOVE, sample(x = 1f, time = 1))
        processor.submit(PointerPhase.UP, sample(x = 2f, time = 2))
        assertTrue(strokes.isEmpty())
    }

    @Test fun live_stroke_points_are_emitted_for_preview_consumers() {
        val live = mutableListOf<Pair<Int, StrokePoint>>()
        val processor = PointerInputProcessor(
            onStrokeFinished = {},
            onStrokePoint = { pointerId, point -> live += pointerId to point }
        )
        processor.submitView(PointerPhase.DOWN, 7, 0f, 0f, timestampMillis = 0)
        processor.submitView(PointerPhase.UP, 7, 2f, 0f, timestampMillis = 1)
        assertEquals(listOf(7, 7), live.map { it.first })
        assertEquals(listOf(0f, 2f), live.map { it.second.position.x })
    }

    @Test fun high_frequency_input_is_coalesced_but_low_frequency_input_is_retained() {
        val dense = StrokeResampler(ResamplingConfig(5f, 1000))
        dense.add(sample(x = 0f).toStrokePoint())
        repeat(100) { dense.add(sample(x = (it + 1) * .01f, time = (it + 1).toLong()).toStrokePoint()) }
        assertEquals(1, dense.finish(sample(x = 1f, time = 101).toStrokePoint()).size)

        val sparse = StrokeResampler(ResamplingConfig(100f, 10))
        sparse.add(sample(x = 0f, time = 0).toStrokePoint())
        assertEquals(1, sparse.add(sample(x = 0.1f, time = 20).toStrokePoint()).size)
    }
}
