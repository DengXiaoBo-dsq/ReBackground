package com.dsq.rebackground.paint.brush

object GrainMapping {
    fun phaseTexels(source: GrainSource.Texture, arcLengthDocumentUnits: Float): Float {
        require(arcLengthDocumentUnits.isFinite() && arcLengthDocumentUnits >= 0f)
        return (source.initialPhaseTexels.toDouble() +
            arcLengthDocumentUnits.toDouble() / source.scaleDocumentUnitsPerTexel.toDouble()).toFloat()
    }

    fun relativeRotationRadians(
        source: GrainSource.Texture,
        strokeRotationRadians: Float,
    ): Float = when (source.orientation) {
        GrainOrientation.FOLLOW_STROKE -> source.orientationOffsetRadians
        GrainOrientation.FIXED_DOCUMENT -> source.orientationOffsetRadians - strokeRotationRadians
    }

    fun worldRotationRadians(
        source: GrainSource.Texture,
        strokeRotationRadians: Float,
    ): Float = strokeRotationRadians + relativeRotationRadians(source, strokeRotationRadians)

    fun interpolatePhaseTexels(
        endPhaseTexels: Float,
        segmentLengthDocumentUnits: Float,
        fraction: Float,
        scaleDocumentUnitsPerTexel: Float,
    ): Float {
        require(segmentLengthDocumentUnits.isFinite() && segmentLengthDocumentUnits >= 0f)
        require(fraction.isFinite() && fraction in 0f..1f)
        require(scaleDocumentUnitsPerTexel.isFinite() && scaleDocumentUnitsPerTexel > 0f)
        return endPhaseTexels - segmentLengthDocumentUnits * (1f - fraction) / scaleDocumentUnitsPerTexel
    }
}
