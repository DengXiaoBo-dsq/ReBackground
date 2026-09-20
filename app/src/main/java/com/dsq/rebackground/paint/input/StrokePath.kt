package com.dsq.rebackground.paint.input

import com.dsq.rebackground.paint.stroke.StrokePoint

/** Engine-owned path representation. It is intentionally independent of android.graphics.Path. */
class StrokePath {
    private val mutablePoints = ArrayList<StrokePoint>(128)
    val points: List<StrokePoint> get() = mutablePoints
    fun append(point: StrokePoint) { mutablePoints += point }
}
