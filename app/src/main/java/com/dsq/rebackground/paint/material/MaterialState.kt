package com.dsq.rebackground.paint.material

import com.dsq.rebackground.paint.math.Vec2

/**
 * Wet Engine 的逻辑状态。
 *
 * G0: 骨架，不参与 shader / FBO。
 * G6+: 由 SimulationBackend 映射到具体 FBO。
 *
 * 术语约定：
 * - 使用 BoundPigment，而非 FixedPigment。
 *   已沉积颜料仍可能被重新加水激活，Fixed 语义错误。
 *
 * 不变量：
 * - waterMass >= 0
 * - mobilePigmentMass >= 0
 * - boundPigmentMass >= 0
 * - dryness in [0, 1]
 * - velocity 为 finite
 */
data class MaterialState(
    val waterMass: Float,
    val mobilePigmentMass: Float,
    val boundPigmentMass: Float,
    val velocity: Vec2,
    val dryness: Float
) {
    init {
        require(waterMass >= 0f)
        require(mobilePigmentMass >= 0f)
        require(boundPigmentMass >= 0f)
        require(velocity.isFinite)
        require(dryness in 0f..1f)
    }
}