package com.dsq.rebackground.paint.stroke

import com.dsq.rebackground.paint.math.Vec2
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * G1: 有状态 StrokeFrame 生成器（per-pointer）。
 *
 * 算法：
 * - 切线：一阶差分 T[i] = normalize(P[i] - P[i-1])
 *   （G1 采用一阶差分，不加平滑；平滑属于 G3 范畴）
 * - 低速兜底：|P[i] - P[i-1]| < epsilon 时用 lastStableTangent
 * - 角度解包：unwrappedAngle += wrapToPi(angle - lastAngle)
 * - 弧长：arcLength += |P[i] - P[i-1]|
 * - 速度：distance / (t[i] - t[i-1]) * 1000
 *
 * 首点：tangent = (0, 0)，angle = 0，unwrappedAngle = 0，
 *       arcLength = 0，speed = 0，lastAngleDelta = 0
 *
 * 低速阈值 epsilon 默认 0.1f。
 * 若实测抖动，可调到 0.2f；报告中记录最终值。
 */
class StrokeFrameBuilder(
    private val lowSpeedEpsilon: Float = 0.1f
) {
    private var lastPoint: StrokePoint? = null
    private var lastStableTangent: Vec2 = Vec2(0f, 0f)
    private var lastAngle: Float = 0f
    private var lastUnwrappedAngle: Float = 0f
    private var arcLength: Float = 0f
    private var lastTimestampMillis: Long? = null
    private var hasInitialAngle: Boolean = false   // [G1-FIX] 首次有效切线作为角度基准
    /**
     * 最近一次 pushPoint 产生的 unwrappedAngle 增量。
     * 首点时为 0。供 PaintDebugMetrics 统计角度变化。
     */
    var lastAngleDelta: Float = 0f
        private set

    fun beginStroke() {
        lastPoint = null
        lastStableTangent = Vec2(0f, 0f)
        lastAngle = 0f
        lastUnwrappedAngle = 0f
        arcLength = 0f
        lastTimestampMillis = null
        lastAngleDelta = 0f
        hasInitialAngle = false   // [G1-FIX]
    }
    fun pushPoint(point: StrokePoint): StrokeFrame {
        val prev = lastPoint

        if (prev == null) {
            lastPoint = point
            lastTimestampMillis = point.timestampMillis
            lastAngleDelta = 0f
            hasInitialAngle = false   // [G1-FIX] 首点尚无方向
            return StrokeFrame(
                position = point.position,
                tangent = Vec2(0f, 0f),
                angle = 0f,
                unwrappedAngle = 0f,
                arcLength = 0f,
                speed = 0f,
                curvature = 0f,
                timestampMillis = point.timestampMillis
            )
        }

        val dx = point.position.x - prev.position.x
        val dy = point.position.y - prev.position.y
        val distance = sqrt(dx * dx + dy * dy)
        arcLength += distance

        val tangent: Vec2 = if (distance < lowSpeedEpsilon) {
            lastStableTangent
        } else {
            val inv = 1f / distance
            val t = Vec2(dx * inv, dy * inv)
            lastStableTangent = t
            t
        }

        val angle = if (tangent.x == 0f && tangent.y == 0f) 0f
        else atan2(tangent.y, tangent.x)

        // [G1-FIX] 首次得到有效切线时只建立基准，不产生 delta。
        // 若切线仍无效（首点后即进入低速抖动），继续等待，基准仍未建立。
        val delta: Float
        val unwrappedAngle: Float
        if (!hasInitialAngle) {
            if (tangent.x != 0f || tangent.y != 0f) {
                lastAngle = angle
                lastUnwrappedAngle = angle
                unwrappedAngle = angle
                delta = 0f
                hasInitialAngle = true
            } else {
                unwrappedAngle = 0f
                delta = 0f
            }
        } else {
            delta = wrapToPi(angle - lastAngle)
            unwrappedAngle = lastUnwrappedAngle + delta
            lastAngle = angle
            lastUnwrappedAngle = unwrappedAngle
        }

        val prevTs = lastTimestampMillis ?: point.timestampMillis
        val elapsedMs = (point.timestampMillis - prevTs).coerceAtLeast(0L)
        val speed = if (elapsedMs > 0L) distance * 1000f / elapsedMs else 0f

        lastPoint = point
        lastTimestampMillis = point.timestampMillis
        lastAngleDelta = delta

        return StrokeFrame(
            position = point.position,
            tangent = tangent,
            angle = angle,
            unwrappedAngle = unwrappedAngle,
            arcLength = arcLength,
            speed = speed,
            curvature = 0f,
            timestampMillis = point.timestampMillis
        )
    }

    fun endStroke() { /* no-op */ }

    private fun wrapToPi(x: Float): Float {
        var v = x
        val twoPi = 2f * PI.toFloat()
        while (v > PI.toFloat()) v -= twoPi
        while (v < -PI.toFloat()) v += twoPi
        return v
    }
}
