package com.dsq.rebackground.paint.brush

import com.dsq.rebackground.paint.stroke.StrokeFrame
import com.dsq.rebackground.paint.stroke.StrokePoint
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.material.DryBrushLoad
import com.dsq.rebackground.paint.material.DryBrushLoadConfig
/** Converts a normalized StrokePoint into brush geometry without rendering it. */
class BrushGenerator {
    data class Output(
        val stamp: BrushStamp,
        val opacityFactor: Float,
        val flowFactor: Float,
        val nextState: BrushRuntimeState,
    )

    // [G0] 当前笔的 stamp 计数，供 PaintDebugMetrics 读取。
    // 每次 generate() = 产生 1 个 BrushStamp。
    // replayDocument 前必须调用 beginStroke() 重置，避免 metrics 污染。
    private var _strokeStampCount: Int = 0
    val strokeStampCount: Int get() = _strokeStampCount

    // [G0]
    fun beginStroke() { _strokeStampCount = 0 }

    fun generate(
        definition: BrushDefinition,
        point: StrokePoint,
        frame: StrokeFrame = defaultFrame(point),
        previous: BrushRuntimeState = BrushRuntimeState()
    ): Output {
        _strokeStampCount++   // [G0]
        val evaluated = DynamicsEvaluator.evaluate(definition, point, frame, previous)
        val material = definition.material
        val distance = previous.lastPosition?.distanceTo(point.position) ?: 0f
        val dryLoadConfig = DryBrushLoadConfig(
            initialLoad = material.initialDryLoad,
            depletionRatePerDocumentUnit = material.dryDepletionRate,
            rechargeRatePerDocumentUnit = material.dryRechargeRate,
            speedDepletionInfluence = material.drySpeedDepletionInfluence,
        )
        val dryLoad = DryBrushLoad.advance(
            previous.dryLoad ?: dryLoadConfig.initialLoad,
            distance,
            evaluated.normalizedSpeed,
            dryLoadConfig,
        )
        val grain = definition.grain as? GrainSource.Texture
        // ============================================================
        // ============================================================
        return Output(

            BrushStamp(
                center = point.position,
                diameterDocumentUnits = evaluated.diameterDocumentUnits,
                aspectRatio = evaluated.aspectRatio,
                rotationRadians = evaluated.rotationRadians,
                textureResourceKey = (definition.tip as? BrushTip.Texture)?.resourceKey,
                textureSamplingMode = (definition.tip as? BrushTip.Texture)?.samplingMode
                    ?: BrushTextureSamplingMode.ALPHA_MASK,
                grainResourceKey = grain?.resourceKey,
                grainScaleDocumentUnitsPerTexel = grain?.scaleDocumentUnitsPerTexel ?: 1f,
                grainPhaseTexels = grain?.let { GrainMapping.phaseTexels(it, frame.arcLength) } ?: 0f,
                grainRotationRadians = grain?.let { GrainMapping.relativeRotationRadians(it, evaluated.rotationRadians) } ?: 0f,
                grainDepth = grain?.depth ?: 0f,
                dryLoad = dryLoad,
                dryArcLengthDocumentUnits = frame.arcLength,
                bristleDensity = material.bristleDensity,
                paperGrainAffinity = material.paperGrainAffinity,
                paperResponseStrength = material.paperResponseStrength,
                bristleSeed = material.bristleSeed,
                dryPressure = point.pressure,
            ),
            evaluated.opacityFactor,
            evaluated.flowFactor,
            BrushRuntimeState(
                lastPosition = point.position,
                lastTimestampMillis = point.timestampMillis,
                speedDocumentUnitsPerSecond = evaluated.rawSpeedDocumentUnitsPerSecond,
                smoothedSpeedDocumentUnitsPerSecond = evaluated.filteredSpeedDocumentUnitsPerSecond,
                lastDiameterDocumentUnits = evaluated.diameterDocumentUnits,
                lastAspectRatio = evaluated.aspectRatio,
                lastRotationRadians = evaluated.rotationRadians,
                lastOpacityFactor = evaluated.opacityFactor,
                lastFlowFactor = evaluated.flowFactor,
                dryLoad = dryLoad,
            )
        )
    }

    /** Compatibility entry point for non-rendering callers which only have runtime state. */
    fun generate(
        definition: BrushDefinition,
        point: StrokePoint,
        previous: BrushRuntimeState
    ): Output = generate(definition, point, defaultFrame(point), previous)

    private fun defaultFrame(point: StrokePoint) = StrokeFrame(
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
