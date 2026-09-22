package com.dsq.rebackground.paint.debug

import android.util.Log
import com.dsq.rebackground.paint.brush.BrushStamp
import com.dsq.rebackground.paint.stroke.StrokePoint
import kotlin.math.abs

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

    private var documentOobCount: Int = 0
    private var unexpectedOobCount: Int = 0
    private var expectedTextureDiscardCount: Int = 0
    private var angleWrapCorrectionCount: Int = 0

    // [G0] Batch 2: 跨笔累计，不随 beginStroke 清零
    private var strokeBeginCount: Int = 0
    private var strokeFinishCount: Int = 0
    private var activeStrokeCount: Int = 0
    private var multiTouchCancelCount: Int = 0

    // [G1] angle delta 统计
    private val angleDeltas: MutableList<Float> = mutableListOf()
    private var angleDeltaSum: Float = 0f
    private var angleDeltaMax: Float = 0f

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
        documentOobCount = 0
        unexpectedOobCount = 0
        expectedTextureDiscardCount = 0
        angleWrapCorrectionCount = 0
        angleDeltas.clear()
        angleDeltaSum = 0f
        angleDeltaMax = 0f
        // [G0] Batch 2 累计
        strokeBeginCount++
        activeStrokeCount++
    }
    fun markOobPoint() { oobPointCount++ }

    fun markDocumentOob() { documentOobCount++ }

    fun markUnexpectedOob() { unexpectedOobCount++ }

    fun markExpectedTextureDiscard() { expectedTextureDiscardCount++ }

    fun onAngleRawDelta(rawDelta: Float) {
        if (rawDelta > Math.PI.toFloat() || rawDelta < -Math.PI.toFloat()) {
            angleWrapCorrectionCount++
        }
    }

    fun snapshot(): Map<String, Any> = mapOf(
        "strokeId" to strokeId,
        "points" to pointCount,
        "stamps" to interpolatedStampCount,
        "interp" to interpolatedStampCount,
        "oob" to oobPointCount,
        "documentOob" to documentOobCount,
        "unexpectedOob" to unexpectedOobCount,
        "expectedTextureDiscard" to expectedTextureDiscardCount,
        "jump" to largeJumpCount,
        "length" to strokeLength,
        "diameterMin" to (if (minDiameter == Float.MAX_VALUE) 0f else minDiameter),
        "diameterMax" to maxDiameter,
        "nan" to nanCount,
        "inf" to infCount,
        "angleWrapCorrection" to angleWrapCorrectionCount,
        "angleMean" to (if (angleDeltas.isEmpty()) 0f else angleDeltaSum / angleDeltas.size),
        "angleMax" to angleDeltaMax,
        "angleP99" to (if (angleDeltas.isEmpty()) 0f else {
            val s = angleDeltas.sorted()
            s[((s.size - 1) * 0.99f).toInt().coerceIn(0, s.size - 1)]
        })
    )


    fun markStrokeFinished() {
        strokeFinishCount++
        activeStrokeCount = (activeStrokeCount - 1).coerceAtLeast(0)
    }

    fun markMultiTouchCancel() {
        multiTouchCancelCount++
        activeStrokeCount = (activeStrokeCount - 1).coerceAtLeast(0)
    }

    fun resetLifecycleCounters() {
        strokeBeginCount = 0
        strokeFinishCount = 0
        activeStrokeCount = 0
        multiTouchCancelCount = 0
    }

    fun lifecycleSnapshot(): Map<String, Int> = mapOf(
        "begin" to strokeBeginCount,
        "finish" to strokeFinishCount,
        "active" to activeStrokeCount,
        "cancel" to multiTouchCancelCount
    )

    fun markMultiTouchCancelled() { multiTouchCancelled = true }

    /**
     * [G1] 记录一次 StrokeFrameBuilder 产生的角度增量。
     * 传入的 delta 为 unwrappedAngle 差值，可正可负；内部取绝对值。
     */
    fun onAngleDelta(delta: Float) {
        val a = abs(delta)
        angleDeltas.add(a)
        angleDeltaSum += a
        if (a > angleDeltaMax) angleDeltaMax = a
    }

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

    fun endStrokeAndLog(generatorStampCount: Int) {
        val durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
        val dMin = if (minDiameter == Float.MAX_VALUE) 0f else minDiameter
        val totalStamps = generatorStampCount + interpolatedStampCount

        val angleMean = if (angleDeltas.isEmpty()) 0f else angleDeltaSum / angleDeltas.size
        val angleMax = angleDeltaMax
        val angleP99 = if (angleDeltas.isEmpty()) 0f else {
            val sorted = angleDeltas.sorted()
            val idx = ((sorted.size - 1) * 0.99f).toInt().coerceIn(0, sorted.size - 1)
            sorted[idx]
        }

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
                    "nan=$nanCount inf=$infCount " +
                    "angleMean=$angleMean angleMax=$angleMax angleP99=$angleP99"
        )
    }

    companion object {
        const val TAG = "PAINT_METRIC"
    }
}