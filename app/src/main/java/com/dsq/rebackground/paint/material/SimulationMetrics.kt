package com.dsq.rebackground.paint.material

/**
 * Wet Engine 每次 step / resolve 后上报的指标。
 *
 * G0: 骨架。
 * G6+: 由 SimulationBackend.collectMetrics() 填充。
 *
 * 硬门槛：
 * - nanCount == 0 且 infCount == 0
 *
 * 不变量：
 * - 质量类字段 >= 0
 * - maxVelocity >= 0
 * - activeArea in [0, 1]
 */
data class SimulationMetrics(
    val waterMass: Double,
    val mobilePigmentMass: Double,
    val boundPigmentMass: Double,
    val maxVelocity: Float,
    val activeArea: Float,
    val nanCount: Int,
    val infCount: Int
) {
    init {
        require(waterMass >= 0.0)
        require(mobilePigmentMass >= 0.0)
        require(boundPigmentMass >= 0.0)
        require(maxVelocity >= 0f)
        require(activeArea in 0f..1f)
        require(nanCount >= 0)
        require(infCount >= 0)
    }
}