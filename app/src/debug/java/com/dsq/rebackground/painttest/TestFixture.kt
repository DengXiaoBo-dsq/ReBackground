package com.dsq.rebackground.painttest

data class TestFixture(
    val id: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val seed: Long,
    val timestampStepMs: Long,
    val idleWaitMs: Long,
    val segments: List<TestSegment>,
    val expected: Map<String, Any?>
)

data class TestSegment(
    val colorHex: Int,
    val opacity: Float,
    val flow: Float,
    val points: List<TestPoint>
)

data class TestPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f
)