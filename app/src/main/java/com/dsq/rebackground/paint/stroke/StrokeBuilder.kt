package com.dsq.rebackground.paint.stroke

/** Reusable builder owned by one active pointer. */
class StrokeBuilder {
    private val points = ArrayList<StrokePoint>(128)

    fun add(point: StrokePoint) {
        val last = points.lastOrNull()
        val timestamp = if (last != null && point.timestampMillis < last.timestampMillis) {
            last.timestampMillis
        } else {
            point.timestampMillis
        }
        points += if (timestamp == point.timestampMillis) point else point.copy(timestampMillis = timestamp)
    }

    fun isEmpty(): Boolean = points.isEmpty()
    fun build(): Stroke = Stroke(points)
}
