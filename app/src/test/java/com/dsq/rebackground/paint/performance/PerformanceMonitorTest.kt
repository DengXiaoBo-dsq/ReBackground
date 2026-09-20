package com.dsq.rebackground.paint.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceMonitorTest {
    @Test
    fun monitor_computes_average_and_fps() {
        val monitor = PerformanceMonitor(4)
        monitor.recordFrame(10)
        monitor.recordFrame(20)
        assertTrue(monitor.averageFrameMillis() > 0.0)
        assertTrue(monitor.framesPerSecond() > 0.0)
    }

    @Test
    fun quality_controller_selects_tier_and_settings() {
        val controller = QualityController()
        assertEquals(PerformanceTier.HIGH, controller.tierForAverageFrameMillis(10.0))
        assertEquals(512, controller.settingsFor(PerformanceTier.HIGH).particleCapacity)
    }
}
