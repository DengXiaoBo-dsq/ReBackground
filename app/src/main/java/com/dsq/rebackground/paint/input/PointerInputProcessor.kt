package com.dsq.rebackground.paint.input

import android.view.MotionEvent
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.Stroke
import com.dsq.rebackground.paint.stroke.StrokeBuilder
import com.dsq.rebackground.paint.stroke.StrokePoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Android boundary adapter and low-latency input coordinator.
 * All samples are transformed before they enter the queue; downstream objects never see View space.
 */
class PointerInputProcessor(
    private val transform: DocumentSpaceTransform = DocumentSpaceTransform.Identity,
    private val queue: InputQueue = InputQueue(),
    private val resamplingConfig: ResamplingConfig = ResamplingConfig(),
    private val onStrokeFinished: (Stroke) -> Unit = {},
    private val onStrokeCancelled: (Stroke) -> Unit = {},
    private val onStrokePoint: (Int, StrokePoint) -> Unit = { _, _ -> }
) {
    private class ActiveStroke(val pointerId: Int, config: ResamplingConfig) {
        val builder = StrokeBuilder()
        val path = StrokePath()
        val resampler = StrokeResampler(config)
        var lastTimestamp = Long.MIN_VALUE
        var lastPoint: StrokePoint? = null
    }

    private val active = HashMap<Int, ActiveStroke>()

    /** Useful for tests and alternate input frontends. Drains synchronously to minimize latency. */
    fun submit(phase: PointerPhase, sample: PointerSample) {
        val sanitized = PointerSample(
            sample.pointerId,
            sample.position,
            if (sample.pressure.isFinite()) sample.pressure.coerceIn(0f, 1f) else 1f,
            if (sample.tiltX.isFinite()) sample.tiltX else 0f,
            if (sample.tiltY.isFinite()) sample.tiltY else 0f,
            sample.timestampMillis.coerceAtLeast(0L)
        )
        if (queue.offer(QueuedPointerSample(phase, sanitized))) drain()
    }

    /** Test/adapter entry point for a raw View-space observation. */
    fun submitView(
        phase: PointerPhase, pointerId: Int, viewX: Float, viewY: Float, pressure: Float = 1f,
        tiltX: Float = 0f, tiltY: Float = 0f, timestampMillis: Long
    ) {
        safeSample(pointerId, viewX, viewY, pressure, tiltX, tiltY, timestampMillis)?.let { submit(phase, it) }
    }

    fun activePointerCount(): Int = active.size

    fun onMotionEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                enqueueCurrent(event, event.actionIndex, PointerPhase.DOWN)
            MotionEvent.ACTION_MOVE -> {
                for (historyIndex in 0 until event.historySize) {
                    for (pointerIndex in 0 until event.pointerCount) {
                        enqueueHistorical(event, pointerIndex, historyIndex, PointerPhase.MOVE)
                    }
                }
                for (pointerIndex in 0 until event.pointerCount) enqueueCurrent(event, pointerIndex, PointerPhase.MOVE)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val liftedIndex = event.actionIndex
                // History belongs to the whole event. Keep every surviving pointer continuous.
                for (historyIndex in 0 until event.historySize) {
                    for (pointerIndex in 0 until event.pointerCount) {
                        enqueueHistorical(event, pointerIndex, historyIndex, PointerPhase.MOVE)
                    }
                }
                for (pointerIndex in 0 until event.pointerCount) {
                    enqueueCurrent(event, pointerIndex, if (pointerIndex == liftedIndex) PointerPhase.UP else PointerPhase.MOVE)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                // A cancel applies to every active pointer, including a pointer absent from this final event.
                for (pointerIndex in 0 until event.pointerCount) enqueueCurrent(event, pointerIndex, PointerPhase.CANCEL)
                val reportedIds = (0 until event.pointerCount).mapTo(HashSet()) { event.getPointerId(it) }
                active.filterKeys { it !in reportedIds }.forEach { (id, state) ->
                    state.lastPoint?.let { queue.offer(QueuedPointerSample(PointerPhase.CANCEL, PointerSample(id, it.position, it.pressure, it.tiltX, it.tiltY, it.timestampMillis))) }
                }
            }
            else -> return false
        }
        drain()
        return true
    }

    private fun enqueueCurrent(event: MotionEvent, pointerIndex: Int, phase: PointerPhase) {
        sample(event, pointerIndex, -1)?.let { queue.offer(QueuedPointerSample(phase, it)) }
    }

    private fun enqueueHistorical(event: MotionEvent, pointerIndex: Int, historyIndex: Int, phase: PointerPhase) {
        sample(event, pointerIndex, historyIndex)?.let { queue.offer(QueuedPointerSample(phase, it)) }
    }

    private fun sample(event: MotionEvent, index: Int, history: Int): PointerSample? {
        val x = if (history < 0) event.getX(index) else event.getHistoricalX(index, history)
        val y = if (history < 0) event.getY(index) else event.getHistoricalY(index, history)
        if (!x.isFinite() || !y.isFinite()) return null
        val pressure = if (history < 0) event.getPressure(index) else event.getHistoricalPressure(index, history)
        val orientation = if (history < 0) event.getAxisValue(MotionEvent.AXIS_ORIENTATION, index)
            else event.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, index, history)
        val tilt = if (history < 0) event.getAxisValue(MotionEvent.AXIS_TILT, index)
            else event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, index, history)
        val timestamp = if (history < 0) event.eventTime else event.getHistoricalEventTime(history)
        return safeSample(event.getPointerId(index), x, y, pressure, tilt * cos(orientation), tilt * sin(orientation), timestamp)
    }

    private fun safeSample(id: Int, viewX: Float, viewY: Float, pressure: Float, tiltX: Float, tiltY: Float, timestamp: Long): PointerSample? {
        val position = transform.toDocument(viewX, viewY)
        if (!position.isFinite) return null
        return PointerSample(id, position, if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 1f,
            if (tiltX.isFinite()) tiltX else 0f, if (tiltY.isFinite()) tiltY else 0f, timestamp.coerceAtLeast(0L))
    }

    private fun drain() {
        while (true) {
            val event = queue.poll() ?: return
            when (event.phase) {
                PointerPhase.DOWN -> start(event.sample)
                PointerPhase.MOVE -> append(event.sample, final = false)
                PointerPhase.UP -> finish(event.sample, cancelled = false)
                PointerPhase.CANCEL -> finish(event.sample, cancelled = true)
            }
        }
    }

    private fun start(sample: PointerSample) {
        val state = ActiveStroke(sample.pointerId, resamplingConfig)
        active[sample.pointerId] = state
        appendTo(state, normalized(sample, state), final = false)
    }

    private fun append(sample: PointerSample, final: Boolean) {
        active[sample.pointerId]?.let { appendTo(it, normalized(sample, it), final) }
    }

    private fun finish(sample: PointerSample, cancelled: Boolean) {
        val state = active.remove(sample.pointerId) ?: return
        appendTo(state, normalized(sample, state), final = true)
        val stroke = state.builder.build()
        if (cancelled) onStrokeCancelled(stroke) else onStrokeFinished(stroke)
    }

    private fun normalized(sample: PointerSample, state: ActiveStroke): StrokePoint {
        val timestamp = sample.timestampMillis.coerceAtLeast(state.lastTimestamp.takeIf { it != Long.MIN_VALUE } ?: 0L)
        state.lastTimestamp = timestamp
        return sample.toStrokePoint().copy(timestampMillis = timestamp)
    }

    private fun appendTo(state: ActiveStroke, point: StrokePoint, final: Boolean) {
        val points = if (final) state.resampler.finish(point) else state.resampler.add(point)
        points.forEach { emitted ->
            state.path.append(emitted)
            state.builder.add(emitted)
            state.lastPoint = emitted
            onStrokePoint(state.pointerId, emitted)
        }
    }
}
