package com.dsq.rebackground.paint.bristle

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

class BristleSimulatorTest {
    @Test
    fun low_pressure_and_tilt_change_bristle_offsets() {
        val model = BristleModel(count = 48, spread = .2f)
        val field = BristleField(model)
        val before = (0 until model.count).map { abs(field.offsetMagnitude(it)) }.average()
        BristleSimulator().step(
            field,
            model,
            BristleInput(pressure = .1f, tiltX = 0f, tiltY = 0f),
            dt = .05f
        )
        val after = (0 until model.count).map { abs(field.offsetMagnitude(it)) }.average()
        assertTrue(after < before)
    }
}
