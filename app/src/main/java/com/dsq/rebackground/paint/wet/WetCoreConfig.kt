package com.dsq.rebackground.paint.wet

/** Numerical parameters for G6 Wet Core. All rates are expressed per second. */
data class WetCoreConfig(
    val cellSize: Float = 1f,
    val fixedDt: Float = 1f / 60f,
    val maxSubsteps: Int = 8,
    val waterDiffusion: Float = 0.08f,
    val pigmentDiffusion: Float = 0.04f,
    val absorptionRate: Float = 0.12f,
    val evaporationRate: Float = 0.03f,
    val depositionRate: Float = 0.45f,
    val waterCapacity: Float = 1f,
) {
    init {
        require(cellSize.isFinite() && cellSize > 0f)
        require(fixedDt.isFinite() && fixedDt > 0f)
        require(maxSubsteps > 0)
        require(waterDiffusion.isFinite() && waterDiffusion >= 0f)
        require(pigmentDiffusion.isFinite() && pigmentDiffusion >= 0f)
        require(absorptionRate.isFinite() && absorptionRate >= 0f)
        require(evaporationRate.isFinite() && evaporationRate >= 0f)
        require(depositionRate.isFinite() && depositionRate >= 0f)
        require(waterCapacity.isFinite() && waterCapacity > 0f)
        // G6 uses a five-point explicit diffusion update in its safety region.
        require(waterDiffusion * fixedDt / (cellSize * cellSize) <= 0.24f)
        require(pigmentDiffusion * fixedDt / (cellSize * cellSize) <= 0.24f)
    }
}
