package com.dsq.rebackground.paint.performance

/** Renderer/simulator knobs selected from the current performance tier. */
data class QualitySettings(
    val pigmentIterations: Int,
    val particleCapacity: Int,
    val bristleCount: Int,
    val paperGrainScale: Float
) {
    init {
        require(pigmentIterations > 0)
        require(particleCapacity > 0)
        require(bristleCount > 0)
        require(paperGrainScale.isFinite() && paperGrainScale > 0f)
    }
}
