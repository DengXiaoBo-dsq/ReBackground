package com.dsq.rebackground.paint.brush

/** Independent interior texture mapped continuously along a stroke. */
sealed interface GrainSource {
    data object None : GrainSource

    data class Texture(
        val resourceKey: String,
        /** Document-space distance represented by one grain texel. */
        val scaleDocumentUnitsPerTexel: Float,
        val depth: Float = 1f,
        val initialPhaseTexels: Float = 0f,
        val orientation: GrainOrientation = GrainOrientation.FOLLOW_STROKE,
        val orientationOffsetRadians: Float = 0f,
    ) : GrainSource {
        init {
            require(resourceKey.isNotBlank())
            require(scaleDocumentUnitsPerTexel.isFinite() && scaleDocumentUnitsPerTexel > 0f)
            require(depth.isFinite() && depth in 0f..1f)
            require(initialPhaseTexels.isFinite())
            require(orientationOffsetRadians.isFinite())
        }
    }
}

enum class GrainOrientation {
    FOLLOW_STROKE,
    FIXED_DOCUMENT,
}

/** BrushTip is the stamp boundary source; this name makes the Shape/Grain split explicit. */
typealias ShapeSource = BrushTip
