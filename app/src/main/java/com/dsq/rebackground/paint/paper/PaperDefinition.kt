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
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
    }
}
