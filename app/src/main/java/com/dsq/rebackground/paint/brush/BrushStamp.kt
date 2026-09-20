package com.dsq.rebackground.paint.brush

import com.dsq.rebackground.paint.math.Vec2

/** Renderer-ready geometry in document space; it is not a Canvas, Bitmap, texture, or pigment deposit. */
data class BrushStamp(
    val center: Vec2,
    val diameterDocumentUnits: Float,
    val aspectRatio: Float,
    val rotationRadians: Float,
    val textureResourceKey: String? = null
) {
    init {
        require(center.isFinite)
        require(diameterDocumentUnits.isFinite() && diameterDocumentUnits > 0f)
        require(aspectRatio.isFinite() && aspectRatio > 0f)
        require(rotationRadians.isFinite())
        require(textureResourceKey == null || textureResourceKey.isNotBlank())
    }
}
