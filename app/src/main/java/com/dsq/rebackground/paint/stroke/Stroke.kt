package com.dsq.rebackground.paint.stroke

/** The durable drawing primitive used by history and future renderers. */
class Stroke(points: List<StrokePoint>) {
    val points: List<StrokePoint> = points.toList()
    val length: Float = this.points.zipWithNext().sumOf { (a, b) -> a.position.distanceTo(b.position).toDouble() }.toFloat()

    init {
        require(this.points.isNotEmpty()) { "A stroke requires at least one point" }
        require(this.points.zipWithNext().all { (a, b) -> b.timestampMillis >= a.timestampMillis }) {
            "Stroke timestamps must be monotonic"
        }
    }
}
