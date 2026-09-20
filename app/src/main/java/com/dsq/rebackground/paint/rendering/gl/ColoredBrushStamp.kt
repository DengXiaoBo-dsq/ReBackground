package com.dsq.rebackground.paint.rendering.gl

import com.dsq.rebackground.paint.brush.BrushStamp

/** A brush stamp plus the colour and flow it must be painted with. */
data class ColoredBrushStamp(
    val stamp: BrushStamp,
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f,
    // [MOD 2026-09-11] flow：单 stamp 沉积率（0~1）
    val flow: Float = 1f
) {
    init {
        require(red.isFinite() && green.isFinite() && blue.isFinite() && alpha.isFinite())
        require(red in 0f..1f && green in 0f..1f && blue in 0f..1f && alpha in 0f..1f)
        require(flow.isFinite() && flow in 0f..1f)
    }
}
