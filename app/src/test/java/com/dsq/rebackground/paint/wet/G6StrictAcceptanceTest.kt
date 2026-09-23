package com.dsq.rebackground.paint.wet

import com.dsq.rebackground.paint.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

class G6StrictAcceptanceTest {
    @Test
    fun g6_01_pure_water_conservation() {
        val simulator = WetCoreSimulator(48, 48, neutralConfig())
        simulator.seedCell(24, 24, 1f)
        val initial = simulator.waterMass()
        listOf(100, 500, 1000).forEach { target ->
            while (simulator.metrics().fixedSteps < target) simulator.stepFixed()
            assertTrue("steps=$target", relativeError(initial, simulator.waterMass()) <= if (target == 1000) .02 else .015)
        }
    }

    @Test
    fun g6_02_pure_advection_centroid_and_mass() {
        val simulator = WetCoreSimulator(80, 32, neutralConfig())
        gaussian(simulator, cx = 20, cy = 16, radius = 4)
        val start = simulator.centroidOfWater()
        val velocity = Vec2(.6f, 0f)
        simulator.setUniformVelocity(velocity)
        repeat(60) { simulator.stepFixed() }
        val expected = Vec2(start.x + .6f, start.y)
        assertTrue("centroid=${simulator.centroidOfWater()} expected=$expected", simulator.centroidOfWater().distanceTo(expected) <= .01f)
        assertTrue(relativeError(1.0, simulator.waterMass()) <= .02)
    }

    @Test
    fun g6_03_cfl_diagnostic() {
        val safe = WetCoreSimulator(8, 8, neutralConfig())
        safe.setUniformVelocity(Vec2(30f, 0f))
        assertTrue(safe.cflNumber() <= 1f)
        assertTrue(!safe.metrics().accuracyWarning)
        val warned = WetCoreSimulator(8, 8, neutralConfig())
        warned.setUniformVelocity(Vec2(90f, 0f))
        assertTrue(warned.cflNumber() > 1f)
        assertTrue(warned.metrics().accuracyWarning)
    }

    @Test
    fun g6_04_pure_diffusion_conserves_and_spreads() {
        val simulator = WetCoreSimulator(48, 48, neutralConfig(waterDiffusion = .2f))
        simulator.seedCell(24, 24, 1f)
        val initialMass = simulator.waterMass()
        val initialPeak = simulator.peakWater()
        val initialVariance = simulator.varianceOfWater()
        val initialCenter = simulator.centroidOfWater()
        repeat(300) { simulator.stepFixed() }
        assertTrue(relativeError(initialMass, simulator.waterMass()) <= .01)
        assertTrue(simulator.peakWater() < initialPeak)
        assertTrue(simulator.varianceOfWater() > initialVariance)
        assertTrue(simulator.centroidOfWater().distanceTo(initialCenter) <= .01f)
    }

    @Test
    fun g6_05_absorption_is_analytic() {
        val rate = .4f
        val simulator = WetCoreSimulator(8, 8, neutralConfig(absorptionRate = rate))
        simulator.seedCell(4, 4, 1f)
        val points = (0..120).map { step ->
            while (simulator.metrics().fixedSteps < step.toLong()) simulator.stepFixed()
            step * simulator.config.fixedDt.toDouble() to ln(simulator.waterMass())
        }
        val slope = linearSlope(points)
        assertTrue(rSquared(points) >= .995)
        assertTrue(abs(slope + rate) / rate <= .05)
    }

    @Test
    fun g6_06_never_emits_negative_nan_or_inf() {
        val simulator = WetCoreSimulator(32, 32, WetCoreConfig(waterDiffusion = .22f, pigmentDiffusion = .22f, absorptionRate = .7f, evaporationRate = .4f, depositionRate = 1.4f))
        gaussian(simulator, 16, 16, 5, WetRgb(1f, .4f, .1f))
        simulator.setUniformVelocity(Vec2(.7f, -.3f))
        repeat(1000) { simulator.stepFixed() }
        val metrics = simulator.metrics()
        assertEquals(0, metrics.negativeStateViolations)
        assertEquals(0, metrics.nanCount)
        assertEquals(0, metrics.infCount)
    }

    @Test
    fun g6_07_mass_budget_is_closed() {
        val simulator = WetCoreSimulator(32, 32, WetCoreConfig(absorptionRate = .3f, evaporationRate = .2f, depositionRate = 1f))
        simulator.injectCell(16, 16, 1f, WetRgb(.7f, .2f, .1f))
        repeat(360) { simulator.stepFixed() }
        val metrics = simulator.metrics()
        assertTrue("water=${metrics.waterBudgetResidual}", metrics.waterBudgetResidual <= .01)
        assertTrue("pigment=${metrics.pigmentBudgetResidual}", metrics.pigmentBudgetResidual <= .01)
    }

    @Test
    fun g6_08_mobile_to_bound_pigment() {
        val simulator = WetCoreSimulator(16, 16, WetCoreConfig(depositionRate = 2f))
        simulator.seedCell(8, 8, 1f, WetRgb(1f, 0f, 0f))
        val beforeMobile = simulator.mobilePigmentMass()
        repeat(180) { simulator.stepFixed() }
        assertTrue(simulator.mobilePigmentMass() < beforeMobile)
        assertTrue(simulator.boundPigmentMass() > 0.0)
        assertTrue(simulator.metrics().pigmentBudgetResidual <= .01)
    }

    @Test
    fun g6_09_wet_on_dry_deposits_without_mass_loss() {
        val simulator = WetCoreSimulator(20, 20, WetCoreConfig(absorptionRate = .2f, evaporationRate = .1f, depositionRate = 1.2f))
        simulator.injectCell(10, 10, .8f, WetRgb(.2f, .4f, .9f))
        repeat(240) { simulator.stepFixed() }
        assertTrue(simulator.mobilePigmentMass() < .2 + .4 + .9)
        assertTrue(simulator.boundPigmentMass() > 0.0)
        assertTrue(simulator.metrics().waterBudgetResidual <= .01)
        assertTrue(simulator.metrics().pigmentBudgetResidual <= .01)
    }

    @Test
    fun g6_10_wet_on_wet_uses_water_weighted_velocity_merge() {
        val simulator = WetCoreSimulator(8, 8, neutralConfig())
        simulator.injectCell(4, 4, 1f, WetRgb(1f, 0f, 0f), Vec2(2f, 0f))
        simulator.injectCell(4, 4, 3f, WetRgb(0f, 0f, 1f), Vec2(-1f, 0f))
        assertEquals(-.25f, simulator.velocityAt(4, 4).x, 1e-6f)
        assertEquals(2.0, simulator.mobilePigmentMass(), 1e-6)
    }

    @Test
    fun g6_11_wetness_decays_without_input() {
        val simulator = WetCoreSimulator(16, 16, WetCoreConfig(absorptionRate = .2f, evaporationRate = .1f))
        simulator.seedCell(8, 8, 1f)
        var previous = simulator.wetnessAt(8, 8)
        repeat(300) {
            simulator.stepFixed()
            val current = simulator.wetnessAt(8, 8)
            assertTrue("$current > $previous", current <= previous + 1e-4f)
            previous = current
        }
    }

    private fun neutralConfig(
        waterDiffusion: Float = 0f,
        pigmentDiffusion: Float = 0f,
        absorptionRate: Float = 0f,
        evaporationRate: Float = 0f,
        depositionRate: Float = 0f,
    ) = WetCoreConfig(waterDiffusion = waterDiffusion, pigmentDiffusion = pigmentDiffusion, absorptionRate = absorptionRate, evaporationRate = evaporationRate, depositionRate = depositionRate)

    private fun gaussian(simulator: WetCoreSimulator, cx: Int, cy: Int, radius: Int, pigment: WetRgb = WetRgb.Transparent) {
        var total = 0f
        val weights = ArrayList<Triple<Int, Int, Float>>()
        for (y in cy - radius..cy + radius) for (x in cx - radius..cx + radius) {
            val dx = (x - cx).toFloat(); val dy = (y - cy).toFloat()
            val weight = kotlin.math.exp(-(dx * dx + dy * dy) / (2f * radius * radius))
            weights += Triple(x, y, weight); total += weight
        }
        weights.forEach { (x, y, weight) -> simulator.seedCell(x, y, weight / total, WetRgb(pigment.red * weight / total, pigment.green * weight / total, pigment.blue * weight / total)) }
    }

    private fun relativeError(expected: Double, actual: Double) = abs(expected - actual) / maxOf(abs(expected), 1e-12)
    private fun linearSlope(points: List<Pair<Double, Double>>): Double { val mx = points.map { it.first }.average(); val my = points.map { it.second }.average(); return points.sumOf { (it.first - mx) * (it.second - my) } / points.sumOf { (it.first - mx) * (it.first - mx) } }
    private fun rSquared(points: List<Pair<Double, Double>>): Double { val slope = linearSlope(points); val mx = points.map { it.first }.average(); val my = points.map { it.second }.average(); val intercept = my - slope * mx; val total = points.sumOf { (it.second - my) * (it.second - my) }; val residual = points.sumOf { val e = it.second - (intercept + slope * it.first); e * e }; return 1.0 - residual / total }
}
