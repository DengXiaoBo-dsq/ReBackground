package com.dsq.rebackground.paint.brush

/** Input-to-geometry controls. Values are normalized, deterministic and renderer independent. */
data class BrushDynamics(
    val minimumDiameterRatio: Float = 0.15f,
    val pressureSizeInfluence: Float = 1f,
    val speedSizeInfluence: Float = 0f,
    val maximumSpeed: Float = 5_000f,
    val tiltAspectInfluence: Float = 0f,
    val pressureSizeCurve: DynamicsCurve = DynamicsCurve.Linear,
    val pressureOpacityInfluence: Float = 0f,
    val pressureOpacityCurve: DynamicsCurve = DynamicsCurve.Linear,
    val minimumOpacityRatio: Float = 0f,
    val pressureFlowInfluence: Float = 0f,
    val pressureFlowCurve: DynamicsCurve = DynamicsCurve.Linear,
    val minimumFlowRatio: Float = 0f,
    val speedSizeCurve: DynamicsCurve = DynamicsCurve.Linear,
    val speedOpacityInfluence: Float = 0f,
    val speedOpacityCurve: DynamicsCurve = DynamicsCurve.Linear,
    val speedFlowInfluence: Float = 0f,
    val speedFlowCurve: DynamicsCurve = DynamicsCurve.Linear,
    val speedSmoothingTimeConstantMillis: Float = 35f,
    val tiltCurve: DynamicsCurve = DynamicsCurve.Linear,
    val tiltRotationInfluence: Float = 0f,
) {
    init {
        require(minimumDiameterRatio.isFinite() && minimumDiameterRatio in 0f..1f)
        require(pressureSizeInfluence.isFinite() && pressureSizeInfluence in 0f..1f)
        require(speedSizeInfluence.isFinite() && speedSizeInfluence in 0f..1f)
        require(maximumSpeed.isFinite() && maximumSpeed > 0f)
        require(tiltAspectInfluence.isFinite() && tiltAspectInfluence in 0f..1f)
        require(pressureOpacityInfluence.isFinite() && pressureOpacityInfluence in 0f..1f)
        require(minimumOpacityRatio.isFinite() && minimumOpacityRatio in 0f..1f)
        require(pressureFlowInfluence.isFinite() && pressureFlowInfluence in 0f..1f)
        require(minimumFlowRatio.isFinite() && minimumFlowRatio in 0f..1f)
        require(speedOpacityInfluence.isFinite() && speedOpacityInfluence in 0f..1f)
        require(speedFlowInfluence.isFinite() && speedFlowInfluence in 0f..1f)
        require(speedSmoothingTimeConstantMillis.isFinite() && speedSmoothingTimeConstantMillis >= 0f)
        require(tiltRotationInfluence.isFinite() && tiltRotationInfluence in 0f..1f)
    }
}
