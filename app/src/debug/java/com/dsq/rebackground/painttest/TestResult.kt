package com.dsq.rebackground.painttest

data class TestResult(
    val fixtureId: String,
    val pass: Boolean,
    val failures: List<String>,
    val segmentMetrics: List<Map<String, Any>>,
    val lifecycle: Map<String, Int>,
    val extras: Map<String, Any>,
    val segmentBitmapPaths: List<String>,
    val finalBitmapPath: String?
)