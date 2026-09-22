package com.dsq.rebackground.paint.brush

import com.dsq.rebackground.paint.math.Vec2

/** Renderer-ready geometry in document space; it is not a Canvas, Bitmap, texture, or pigment deposit. */
data class BrushStamp(
    val center: Vec2,
    val diameterDocumentUnits: Float,
    val aspectRatio: Float,
    val rotationRadians: Float,
    val textureResourceKey: String? = null,
    val textureSamplingMode: BrushTextureSamplingMode = BrushTextureSamplingMode.ALPHA_MASK,
    val grainResourceKey: String? = null,
    val grainScaleDocumentUnitsPerTexel: Float = 1f,
    val grainPhaseTexels: Float = 0f,
    val grainRotationRadians: Float = 0f,
    val grainDepth: Float = 0f,
    val dryLoad: Float = 1f,
    val dryArcLengthDocumentUnits: Float = 0f,
    val bristleDensity: Float = 0f,
    val paperGrainAffinity: Float = 0f,
    val bristleSeed: Int = 0,
    val dryPressure: Float = 1f,
) {
    init {
        require(center.isFinite)
        require(diameterDocumentUnits.isFinite() && diameterDocumentUnits > 0f)
        require(aspectRatio.isFinite() && aspectRatio > 0f)
        require(rotationRadians.isFinite())
        require(textureResourceKey == null || textureResourceKey.isNotBlank())
        require(grainResourceKey == null || grainResourceKey.isNotBlank())
        require(grainScaleDocumentUnitsPerTexel.isFinite() && grainScaleDocumentUnitsPerTexel > 0f)
        require(grainPhaseTexels.isFinite())
        require(grainRotationRadians.isFinite())
        require(grainDepth.isFinite() && grainDepth in 0f..1f)
        require(dryLoad.isFinite() && dryLoad in 0f..1f)
        require(dryArcLengthDocumentUnits.isFinite() && dryArcLengthDocumentUnits >= 0f)
        require(bristleDensity.isFinite() && bristleDensity in 0f..1f)
        require(paperGrainAffinity.isFinite() && paperGrainAffinity in 0f..1f)
        require(dryPressure.isFinite() && dryPressure in 0f..1f)
    }
}
