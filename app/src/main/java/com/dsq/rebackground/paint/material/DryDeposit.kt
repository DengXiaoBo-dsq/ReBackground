package com.dsq.rebackground.paint.material

data class DryDeposit(
    val brushCoverage: Float,
    val load: Float,
    val paperFactor: Float,
    val bristleFactor: Float,
) {
    val coverage: Float = (brushCoverage * load * paperFactor * bristleFactor).coerceIn(0f, 1f)
    val mass: Float get() = coverage

    init {
        require(brushCoverage.isFinite() && brushCoverage in 0f..1f)
        require(load.isFinite() && load in 0f..1f)
        require(paperFactor.isFinite() && paperFactor in 0f..1f)
        require(bristleFactor.isFinite() && bristleFactor in 0f..1f)
    }
}
