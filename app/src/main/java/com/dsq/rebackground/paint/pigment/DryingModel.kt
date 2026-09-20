package com.dsq.rebackground.paint.pigment

/** First-order drying: wetness evaporates over time while pigment remains in place. */
class DryingModel(private val config: PigmentSimulationConfig) {
    fun step(wetness: WetnessField, dt: Float) {
        require(dt.isFinite() && dt >= 0f) { "Drying delta time must be finite and non-negative" }
        val factor = (1f - config.dryingRate * dt).coerceIn(0f, 1f)
        wetness.scale(factor)
    }
}
