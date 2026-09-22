package com.dsq.rebackground.painttest

data class TestStroke(
    val fixtureId: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val points: List<FloatArray>,   // [x, y, pressure, tiltX, tiltY, timestampMs]
    val colorHex: Int,
    val seed: Long
)