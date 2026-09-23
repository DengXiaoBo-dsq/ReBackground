package com.dsq.rebackground.paint.brush

/**
 * Stamp coverage properties only. Pigment, wetness, diffusion and compositing are owned by later phases.
 *
 * [MOD PR-2 Step 1]
 *   新增 5 个字段：
 *     - rotationRandomness / fixedRotationDegrees：从  BrushConfig 迁移（保留铅笔/毛笔行为）
 *     - humidityResponse / bristleDensity / paperGrainAffinity：阶段 3/4 物理扩展占位
 *       默认 0f，在当前渲染管线中不生效；阶段 3/4 接入 GL 后填值即生效。
 */
data class BrushMaterial(
    val spacingRatio: Float = 0.15f,
    val edgeHardness: Float = 0.8f,
    val textureStrength: Float = 0f,
    val rotationRandomness: Float = 0f,
    val fixedRotationDegrees: Float = 0f,
    val humidityResponse: Float = 0f,
    val bristleDensity: Float = 0f,
    val paperGrainAffinity: Float = 0f,
    /** Per-brush Layer B multiplier. Canvas Layer A remains brush independent. */
    val paperResponseStrength: Float = 0.5f,
    val initialDryLoad: Float = 1f,
    val dryDepletionRate: Float = 0f,
    val dryRechargeRate: Float = 0f,
    val drySpeedDepletionInfluence: Float = 0f,
    val bristleSeed: Int = 0,
) {
    init {
        require(spacingRatio.isFinite() && spacingRatio > 0f)
        require(edgeHardness.isFinite() && edgeHardness in 0f..1f)
        require(textureStrength.isFinite() && textureStrength in 0f..1f)
        require(rotationRandomness.isFinite() && rotationRandomness in 0f..1f)
        require(fixedRotationDegrees.isFinite())
        require(humidityResponse.isFinite() && humidityResponse in 0f..1f)
        require(bristleDensity.isFinite() && bristleDensity in 0f..1f)
        require(paperGrainAffinity.isFinite() && paperGrainAffinity in 0f..1f)
        require(paperResponseStrength.isFinite() && paperResponseStrength in 0f..1f)
        require(initialDryLoad.isFinite() && initialDryLoad in 0f..1f)
        require(dryDepletionRate.isFinite() && dryDepletionRate >= 0f)
        require(dryRechargeRate.isFinite() && dryRechargeRate >= 0f)
        require(drySpeedDepletionInfluence.isFinite() && drySpeedDepletionInfluence in 0f..1f)
    }

    val hasDryResponse: Boolean
        get() = dryDepletionRate > 0f || bristleDensity > 0f || paperGrainAffinity > 0f
}
