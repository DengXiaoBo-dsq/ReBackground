package com.dsq.rebackground.paint.material

import com.dsq.rebackground.paint.math.Vec2

/**
 * Brush Kernel 输出到 Material Engine 的统一协议。
 *
 * G0: 仅几何 / 动态骨架，不含 Wet/Dry 物理场。
 * G6+: 可扩展 water / pigment 字段。
 *
 * 不变量：
 * - position 为 document space 坐标
 * - radius > 0
 * - coverage / opacity / flow / pressure 均在 [0, 1]
 * - speed >= 0
 * - texturePhase 沿 stroke arcLength 单调递增
 */
data class MaterialDeposit(
    val position: Vec2,
    val radius: Float,
    val rotation: Float,
    val coverage: Float,
    val opacity: Float,
    val flow: Float,
    val pressure: Float,
    val speed: Float,
    val texturePhase: Float
) {
    init {
        require(position.isFinite)
        require(radius > 0f)
        require(coverage in 0f..1f)
        require(opacity in 0f..1f)
        require(flow in 0f..1f)
        require(pressure in 0f..1f)
        require(speed >= 0f)
        require(texturePhase.isFinite())
    }
}