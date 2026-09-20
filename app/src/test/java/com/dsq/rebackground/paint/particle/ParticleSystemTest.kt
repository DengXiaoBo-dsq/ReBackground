package com.dsq.rebackground.paint.particle

import com.dsq.rebackground.paint.pigment.PigmentColor
import com.dsq.rebackground.paint.pigment.PigmentDeposit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticleSystemTest {
    @Test
    fun particles_are_emitted_and_then_expire() {
        val system = ParticleSystem(
            capacity = 64,
            config = ParticleConfig(
                droplets = 2,
                splatters = 1,
                dryFragments = 0,
                maximumLife = .1f
            ),
            random = Random(1)
        )
        system.emit(
            PigmentDeposit(
                centerX = 8f,
                centerY = 8f,
                radius = 2f,
                color = PigmentColor.White,
                amount = 1f,
                wetness = .5f
            )
        )

        assertTrue(system.activeCount > 0)
        repeat(60) { system.step(1f / 60f) }
        assertEquals(0, system.activeCount)
    }
}
