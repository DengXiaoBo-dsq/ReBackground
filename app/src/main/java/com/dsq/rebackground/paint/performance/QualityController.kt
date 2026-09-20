package com.dsq.rebackground.paint.performance

/** Maps measured frame time or a configured tier to deterministic quality settings. */
class QualityController {
    fun settingsFor(tier: PerformanceTier): QualitySettings = when (tier) {
        PerformanceTier.LOW -> QualitySettings(
            pigmentIterations = 1,
            particleCapacity = 128,
            bristleCount = 24,
            paperGrainScale = .5f
        )
        PerformanceTier.MEDIUM -> QualitySettings(
            pigmentIterations = 2,
            particleCapacity = 256,
            bristleCount = 48,
            paperGrainScale = .75f
        )
        PerformanceTier.HIGH -> QualitySettings(
            pigmentIterations = 3,
            particleCapacity = 512,
            bristleCount = 96,
            paperGrainScale = 1f
        )
    }

    fun tierForAverageFrameMillis(averageFrameMillis: Double): PerformanceTier {
        require(averageFrameMillis.isFinite() && averageFrameMillis >= 0.0)
        return when {
            averageFrameMillis <= 12.0 -> PerformanceTier.HIGH
            averageFrameMillis <= 20.0 -> PerformanceTier.MEDIUM
            else -> PerformanceTier.LOW
        }
    }
}
