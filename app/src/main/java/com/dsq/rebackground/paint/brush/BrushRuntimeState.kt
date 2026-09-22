package com.dsq.rebackground.paint.brush

import com.dsq.rebackground.paint.math.Vec2

/** Per-active-stroke state. This is disposable and must never be stored in a document or history command. */
data class BrushRuntimeState(
    val lastPosition: Vec2? = null,
    val lastTimestampMillis: Long? = null,
    val speedDocumentUnitsPerSecond: Float = 0f,
    val smoothedSpeedDocumentUnitsPerSecond: Float = speedDocumentUnitsPerSecond,
    val lastDiameterDocumentUnits: Float? = null,
    val lastAspectRatio: Float? = null,
    val lastRotationRadians: Float? = null,
    val lastOpacityFactor: Float = 1f,
    val lastFlowFactor: Float = 1f,
    val dryLoad: Float? = null,
)
