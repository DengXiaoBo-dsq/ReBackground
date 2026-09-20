package com.dsq.rebackground.paint.performance

/** Runtime bridge between frame metrics and adaptive quality selection. */
class RuntimePerformanceController(
    private val frameMonitor: PerformanceMonitor = PerformanceMonitor(),
    private val inputLatencyMonitor: PerformanceMonitor = PerformanceMonitor(),
    private val qualityController: QualityController = QualityController()
) {
    fun recordFrame(durationMillis: Long) {
        frameMonitor.recordFrame(durationMillis)
    }

    fun recordInputLatency(durationMillis: Long) {
        inputLatencyMonitor.recordFrame(durationMillis)
    }

    fun averageFrameMillis(): Double = frameMonitor.averageFrameMillis()
    fun averageInputLatencyMillis(): Double = inputLatencyMonitor.averageFrameMillis()
    fun framesPerSecond(): Double = frameMonitor.framesPerSecond()
    fun currentTier(): PerformanceTier = qualityController.tierForAverageFrameMillis(averageFrameMillis())
    fun currentQualitySettings(): QualitySettings = qualityController.settingsFor(currentTier())

    fun reset() {
        frameMonitor.reset()
        inputLatencyMonitor.reset()
    }
}
