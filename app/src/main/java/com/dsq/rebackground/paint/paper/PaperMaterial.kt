package com.dsq.rebackground.paint.paper

import com.dsq.rebackground.paint.pigment.PigmentSimulationConfig

/** Physical paper-surface parameters. They describe material behaviour, not texture pixels. */
data class PaperMaterial(
    val roughness: Float = 0.5f,
    val absorption: Float = 0.35f,
    val fiberDensity: Float = 0.5f,
    val grainScale: Float = 1f,
    val heightAmplitude: Float = 0.5f
) {
    init {
        require(roughness.isFinite() && roughness in 0f..1f)
        require(absorption.isFinite() && absorption in 0f..1f)
        require(fiberDensity.isFinite() && fiberDensity in 0f..1f)
        require(grainScale.isFinite() && grainScale > 0f)
        require(heightAmplitude.isFinite() && heightAmplitude in 0f..1f)
    }

    /** Map paper parameters into the current CPU pigment simulator without coupling the two systems. */
    fun toPigmentSimulationConfig(base: PigmentSimulationConfig = PigmentSimulationConfig()): PigmentSimulationConfig =
        base.copy(
            diffusionRate = base.diffusionRate * (1f - roughness * .35f),
            absorptionRate = base.absorptionRate + absorption * .02f,
            dryingRate = base.dryingRate * (1f - roughness * .2f)
        )
}
