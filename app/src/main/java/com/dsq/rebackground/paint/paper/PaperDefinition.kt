package com.dsq.rebackground.paint.paper

import com.dsq.rebackground.paint.pigment.PigmentColor

/** Serialized paper recipe. */
data class PaperDefinition(
    val id: String,
    val displayName: String,
    val material: PaperMaterial = PaperMaterial(),
    val baseColor: PigmentColor = PigmentColor.White,
    /** Stable procedural-paper seed; the canvas stores only [id]. */
    val seed: Int = 0,
    /** Strength of the paper's visible canvas texture in the composite pass. */
    val visualStrength: Float = 0.2f,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(visualStrength.isFinite() && visualStrength in 0f..1f)
    }
}
