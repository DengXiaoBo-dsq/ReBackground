package com.dsq.rebackground.paint.pigment

import org.junit.Assert.assertTrue
import org.junit.Test

class PigmentSimulatorTest {
    @Test
    fun deposit_places_pigment_and_wetness() {
        val simulator = PigmentSimulator(16, 16)
        simulator.deposit(
            PigmentDeposit(
                centerX = 8f,
                centerY = 8f,
                radius = 2f,
                color = PigmentColor(1f, 0f, 0f),
                amount = 1f,
                wetness = .8f
            )
        )

        val center = simulator.pigment.colorAt(8, 8)
        assertTrue(center.red > 0f)
        assertTrue(simulator.wetness.get(8, 8) > 0f)
    }

    @Test
    fun diffusion_spreads_pigment_to_neighbours() {
        val simulator = PigmentSimulator(16, 16)
        simulator.deposit(
            PigmentDeposit(
                centerX = 8f,
                centerY = 8f,
                radius = 1f,
                color = PigmentColor(1f, 1f, 1f),
                amount = 1f,
                wetness = 1f
            )
        )

        repeat(30) { simulator.step(1f / 60f) }
        assertTrue(simulator.pigment.colorAt(9, 8).red > 0f)
        assertTrue(simulator.pigment.colorAt(8, 9).green > 0f)
    }

    @Test
    fun drying_reduces_wetness_over_time() {
        val simulator = PigmentSimulator(16, 16)
        simulator.deposit(
            PigmentDeposit(
                centerX = 8f,
                centerY = 8f,
                radius = 2f,
                color = PigmentColor.White,
                amount = 1f,
                wetness = 1f
            )
        )

        val before = simulator.wetness.get(8, 8)
        repeat(120) { simulator.step(1f / 60f) }
        assertTrue(simulator.wetness.get(8, 8) < before)
    }
}
