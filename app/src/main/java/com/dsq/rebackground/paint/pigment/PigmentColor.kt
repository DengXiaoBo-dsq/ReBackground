package com.dsq.rebackground.paint.pigment

/** Normalized pigment colour. It carries no brush, paper or wetness state. */
data class PigmentColor(
    val red: Float,
    val green: Float,
    val blue: Float
) {
    init {
        require(red.isFinite() && green.isFinite() && blue.isFinite()) {
            "Pigment colour components must be finite"
        }
        require(red in 0f..1f && green in 0f..1f && blue in 0f..1f) {
            "Pigment colour components must be normalized"
        }
    }

    companion object {
        val White = PigmentColor(1f, 1f, 1f)
    }
}
