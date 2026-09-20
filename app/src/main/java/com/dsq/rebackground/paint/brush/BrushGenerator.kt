package com.dsq.rebackground.paint.brush

import com.dsq.rebackground.paint.stroke.StrokePoint
import kotlin.math.atan2
import kotlin.math.sqrt

/** Converts a normalized StrokePoint into brush geometry without rendering it. */
class BrushGenerator {
    data class Output(val stamp: BrushStamp, val nextState: BrushRuntimeState)

    // [G0] 当前笔的 stamp 计数，供 PaintDebugMetrics 读取。
    // 每次 generate() = 产生 1 个 BrushStamp。
    // replayDocument 前必须调用 beginStroke() 重置，避免 metrics 污染。
    private var _strokeStampCount: Int = 0
    val strokeStampCount: Int get() = _strokeStampCount

    // [G0]
    fun beginStroke() { _strokeStampCount = 0 }

    fun generate(definition: BrushDefinition, point: StrokePoint, previous: BrushRuntimeState = BrushRuntimeState()): Output {
        _strokeStampCount++   // [G0]
        val elapsedMillis = previous.lastTimestampMillis?.let { (point.timestampMillis - it).coerceAtLeast(0L) } ?: 0L
        val distance = previous.lastPosition?.distanceTo(point.position) ?: 0f
        val speed = if (elapsedMillis > 0L) distance * 1_000f / elapsedMillis else 0f
        val normalizedSpeed = (speed / definition.dynamics.maximumSpeed).coerceIn(0f, 1f)
        val pressureFactor = (1f - definition.dynamics.pressureSizeInfluence) + point.pressure * definition.dynamics.pressureSizeInfluence
        val speedFactor = 1f - normalizedSpeed * definition.dynamics.speedSizeInfluence
        // [MOD 2026-09-11] flow 不再影响直径，改为单 stamp 沉积率
        val diameter = definition.baseDiameterDocumentUnits *
            (definition.dynamics.minimumDiameterRatio + (1f - definition.dynamics.minimumDiameterRatio) * pressureFactor) * speedFactor
        val tiltMagnitude = sqrt(point.tiltX * point.tiltX + point.tiltY * point.tiltY).coerceIn(0f, 1f)
        val aspect = definition.tip.aspectRatio * (1f + tiltMagnitude * definition.dynamics.tiltAspectInfluence)

        // ============================================================
        // [MOD PR-2.8] 笔迹方向旋转
        //
        // 优先级：
        //   1. 有笔迹方向（相对上一点的位移）→ 用笔迹方向
        //   2. 无笔迹方向但有倾斜 → 用倾斜方向（保留原行为）
        //   3. 都无 → 0
        //
        // 效果：纹理跟随笔迹方向旋转（Procreate 的"跟进描边"）
        // ============================================================
        val dx = previous.lastPosition?.let { point.position.x - it.x } ?: 0f
        val dy = previous.lastPosition?.let { point.position.y - it.y } ?: 0f
        val moveDistSq = dx * dx + dy * dy

        val rotation = when {
            // 位移 > 0.1 像素才认为有方向（避免静止时抖动）
            moveDistSq > 0.01f -> atan2(dy, dx)
            tiltMagnitude > 0f -> atan2(point.tiltY, point.tiltX)
            else -> 0f
        }
        // ============================================================
        return Output(

            BrushStamp(
                center = point.position,
                diameterDocumentUnits = diameter,
                aspectRatio = aspect,
                rotationRadians = rotation,
                textureResourceKey = (definition.tip as? BrushTip.Texture)?.resourceKey
            ),
            BrushRuntimeState(point.position, point.timestampMillis, speed)
        )
    }
}
