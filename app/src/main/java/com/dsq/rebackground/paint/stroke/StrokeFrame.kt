package com.dsq.rebackground.paint.stroke

import com.dsq.rebackground.paint.math.Vec2

/**
 * G1: Stroke geometry 派生数据。
 *
 * 与 StrokePoint 的分工：
 * - StrokePoint：归一化输入事实（pressure / tilt / timestamp）
 * - StrokeFrame：由路径几何派生的数据（tangent / angle / arcLength / speed）
 *
 * 不变量：
 * - tangent 为有限向量（首点可能为 (0,0)，仅代表"尚无方向"）
 * - angle ∈ [-π, π]
 * - unwrappedAngle 连续累积，不 wrap
 * - arcLength >= 0，单调不减
 * - speed >= 0
 * - curvature 暂为 0（G3 再启用）
 */
data class StrokeFrame(
    val position: Vec2,
    val tangent: Vec2,
    val angle: Float,
    val unwrappedAngle: Float,
    val arcLength: Float,
    val speed: Float,
    val curvature: Float,
    val timestampMillis: Long
) {
    /** Left-handed document-space normal.  It is derived, never independently filtered. */
    val normal: Vec2
        get() = if (tangent.x == 0f && tangent.y == 0f) Vec2(0f, 0f) else Vec2(-tangent.y, tangent.x)

    init {
        require(position.isFinite) { "StrokeFrame position must be finite" }
        require(tangent.isFinite) { "StrokeFrame tangent must be finite" }
        require(angle.isFinite()) { "StrokeFrame angle must be finite" }
        require(unwrappedAngle.isFinite()) { "StrokeFrame unwrappedAngle must be finite" }
        require(arcLength >= 0f) { "StrokeFrame arcLength must be >= 0" }
        require(speed >= 0f) { "StrokeFrame speed must be >= 0" }
        require(curvature.isFinite()) { "StrokeFrame curvature must be finite" }
    }
}
