package com.dsq.rebackground.paint.brush

/** Input-to-geometry controls. Values are normalized, deterministic and renderer independent. */
data class BrushDynamics(
    val minimumDiameterRatio: Float = 0.15f,
    val pressureSizeInfluence: Float = 1f,
    val speedSizeInfluence: Float = 0f,
    val maximumSpeed: Float = 5_000f,
    val tiltAspectInfluence: Float = 0f
) {
    init {
        require(minimumDiameterRatio.isFinite() && minimumDiameterRatio in 0f..1f)
        require(pressureSizeInfluence.isFinite() && pressureSizeInfluence in 0f..1f)
        require(speedSizeInfluence.isFinite() && speedSizeInfluence in 0f..1f)
        require(maximumSpeed.isFinite() && maximumSpeed > 0f)
        require(tiltAspectInfluence.isFinite() && tiltAspectInfluence in 0f..1f)
    }
}
