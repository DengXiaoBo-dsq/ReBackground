package com.dsq.rebackground.paint.debug

import android.util.Log
import com.dsq.rebackground.paint.brush.BrushStamp
import com.dsq.rebackground.paint.stroke.StrokePoint

/**
 * G0 最小可观测性组件。
 *
 * 职责分工：
 * - BrushGenerator.generate        → stampCount++（每笔开始清零）
 * - PaintEngineController          → strokeId / pointCount / strokeLength / 生命周期
 * - PaintDebugMetrics              → 数据聚合与单次输出
 *
 * 不做 GPU 读取，不做 Reference Sampler，不做 Golden Test。
 * 不修改现有 Log.d / Log.w 调用。
 *
 * 输出：单条 Logcat 日志，tag = PAINT_METRIC
 */
class PaintDebugMetrics {

    private var strokeId: Long = 0L
    private var startMs: Long = 0L

    private var pointCount: Int = 0
    private var interpolatedStampCount: Int = 0
    private var oobPointCount: Int = 0
    private var largeJumpCount: Int = 0

    private var strokeLength: Float = 0f
    private var lastPoint: StrokePoint? = null

    private var minDiameter: Float = Float.MAX_VALUE
    private var maxDiameter: Float = 0f

    private var nanCount: Int = 0
    private var infCount: Int = 0

    private var multiTouchCancelled: Boolean = false

    fun beginStroke(id: Long) {
        strokeId = id
        startMs = System.currentTimeMillis()
        pointCount = 0
        interpolatedStampCount = 0
        oobPointCount = 0
        largeJumpCount = 0
        strokeLength = 0f
        lastPoint = null
        minDiameter = Float.MAX_VALUE
        maxDiameter = 0f
        nanCount = 0
        infCount = 0
        multiTouchCancelled = false
    }

    fun markOobPoint() { oobPointCount++ }

    fun markMultiTouchCancelled() { multiTouchCancelled = true }

    /**
     * 记录一次有效 stroke point。
     *
     * @param point     已归一化的 StrokePoint
     * @param stamp     BrushGenerator 输出
     * @param interp    本点与上一点之间自动插入的 stamp 数
     * @param largeJump 是否触发 MAX_STROKE_JUMP
     */
    fun onPoint(
        point: StrokePoint,
        stamp: BrushStamp,
        interp: Int,
        largeJump: Boolean
    ) {
        pointCount++
        interpolatedStampCount += interp
        if (largeJump) largeJumpCount++

        lastPoint?.let { prev ->
            strokeLength += prev.position.distanceTo(point.position)
        }
        lastPoint = point

        val d = stamp.diameterDocumentUnits
        if (d < minDiameter) minDiameter = d
        if (d > maxDiameter) maxDiameter = d

        if (!point.position.isFinite ||
            !point.pressure.isFinite() ||
            !point.tiltX.isFinite() ||
            !point.tiltY.isFinite() ||
            !d.isFinite()
        ) {
            nanCount++
        }
    }

    /**
     * 输出单条 PAINT_METRIC 日志。
     *
     * @param generatorStampCount 由 BrushGenerator.strokeStampCount 传入
     */
    fun endStrokeAndLog(generatorStampCount: Int) {
        val durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
        val dMin = if (minDiameter == Float.MAX_VALUE) 0f else minDiameter
        val totalStamps = generatorStampCount + interpolatedStampCount

        Log.d(
            TAG,
            "stroke=$strokeId " +
                    "points=$pointCount stamps=$totalStamps " +
                    "genStamps=$generatorStampCount interp=$interpolatedStampCount " +
                    "oob=$oobPointCount jump=$largeJumpCount " +
                    "length=$strokeLength " +
                    "diameterMin=$dMin diameterMax=$maxDiameter " +
                    "durationMs=$durationMs " +
                    "multiTouchCancelled=$multiTouchCancelled " +
                    "nan=$nanCount inf=$infCount"
        )
    }

    companion object {
        const val TAG = "PAINT_METRIC"
    }
}