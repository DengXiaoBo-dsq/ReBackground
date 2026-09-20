package com.dsq.rebackground.paint.acceptance

import com.dsq.rebackground.paint.performance.PerformanceTier
import com.dsq.rebackground.paint.performance.RuntimePerformanceController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalAcceptanceMatrixTest {
    @Test
    fun acceptance_matrix_contains_required_real_device_scenarios() {
        assertTrue(FinalAcceptanceMatrix.scenarios.any { it.id == "4k" })
        assertTrue(FinalAcceptanceMatrix.scenarios.any { it.id == "wet-ink" })
    }

    @Test
    fun runtime_performance_controller_selects_tier_from_frame_time() {
        val controller = RuntimePerformanceController()
        repeat(60) { controller.recordFrame(10L) }
        assertEquals(PerformanceTier.HIGH, controller.currentTier())
    }
}
