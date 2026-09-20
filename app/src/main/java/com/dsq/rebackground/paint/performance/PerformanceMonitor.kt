package com.dsq.rebackground.paint.performance

/** Small circular-buffer frame-time monitor. GPU/CPU counters can be added without changing this API. */
class PerformanceMonitor(private val capacity: Int = 240) {
    init {
        require(capacity > 0)
    }

    private val frameTimesMillis = LongArray(capacity)
    private var head = 0
    private var size = 0
    private var sumMillis = 0L

    fun recordFrame(durationMillis: Long) {
        require(durationMillis >= 0L)
        if (size == capacity) sumMillis -= frameTimesMillis[head]
        sumMillis += durationMillis
        frameTimesMillis[head] = durationMillis
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun averageFrameMillis(): Double = if (size == 0) 0.0 else sumMillis.toDouble() / size

    fun framesPerSecond(): Double {
        val average = averageFrameMillis()
        return if (average <= 0.0) 0.0 else 1_000.0 / average
    }

    fun reset() {
        head = 0
        size = 0
        sumMillis = 0L
        frameTimesMillis.fill(0L)
    }
}
