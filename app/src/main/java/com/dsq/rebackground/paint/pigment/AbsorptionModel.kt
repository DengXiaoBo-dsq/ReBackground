package com.dsq.rebackground.paint.pigment

/** First-order absorption: both pigment and water are partially bound by the paper over time. */
class AbsorptionModel(private val config: PigmentSimulationConfig) {
    fun step(pigment: PigmentField, wetness: WetnessField, dt: Float) {
        require(dt.isFinite() && dt >= 0f) { "Absorption delta time must be finite and non-negative" }
        val factor = (1f - config.absorptionRate * dt).coerceIn(0f, 1f)
        pigment.scale(factor)
        wetness.scale(factor)
    }
}
