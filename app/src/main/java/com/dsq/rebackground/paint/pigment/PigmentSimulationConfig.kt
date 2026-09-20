package com.dsq.rebackground.paint.pigment

/** Tunable parameters for the first CPU pigment/wetness simulation. */
data class PigmentSimulationConfig(
    val diffusionRate: Float = 0.08f,
    val absorptionRate: Float = 0.005f,
    val dryingRate: Float = 0.01f,
    val iterations: Int = 2
) {
    init {
        require(diffusionRate.isFinite() && diffusionRate >= 0f)
        require(absorptionRate.isFinite() && absorptionRate >= 0f)
        require(dryingRate.isFinite() && dryingRate >= 0f)
        require(iterations > 0)
    }
}
