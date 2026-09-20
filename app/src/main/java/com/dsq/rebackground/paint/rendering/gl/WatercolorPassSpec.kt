package com.dsq.rebackground.paint.rendering.gl

/** Stable pass ids for the next GPU watercolour integration. */
object WatercolorPassSpec {
    const val PIGMENT_DEPOSIT = "pigment_deposit"
    const val PIGMENT_DIFFUSE = "pigment_diffuse"
    const val WETNESS_DIFFUSE = "wetness_diffuse"
    const val PAPER_COMPOSITE = "paper_composite"
    const val PARTICLE = "particle"
    const val PRESENT = "present"
}
