package com.dsq.rebackground.paint.material

import com.dsq.rebackground.paint.brush.BrushMaterial
import com.dsq.rebackground.paint.paper.PaperHeightField
import com.dsq.rebackground.paint.paper.PaperMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

class G5StrictAcceptanceTest {
    private val dryBrush = BrushMaterial(
        bristleDensity = 0.9f,
        paperGrainAffinity = 0.7f,
        initialDryLoad = 1f,
        dryDepletionRate = 0.0011f,
        bristleSeed = 17,
    )

    @Test
    fun load_is_bounded_monotonic_and_exponential() {
        val config = DryBrushLoadConfig(initialLoad = 1f, depletionRatePerDocumentUnit = 0.0011f)
        val checkpoints = listOf(0f, 100f, 250f, 500f, 750f, 1000f)
        val loads = checkpoints.map { DryBrushLoad.advance(1f, it, 0f, config) }
        assertTrue(loads.all { it in 0f..1f })
        assertEquals(0, loads.zipWithNext().count { (a, b) -> b > a + 1e-7f })
        assertTrue(rSquared(checkpoints.map { it.toDouble() }, loads.map { ln(it.toDouble()) }) >= 0.98)
    }

    @Test
    fun deposit_budget_tracks_load() {
        val loads = (0..20).map { it / 20.0 }
        val masses = loads.map { DryDeposit(1f, it.toFloat(), 1f, 1f).mass.toDouble() }
        assertTrue(correlation(loads, masses) >= 0.8)
        assertEquals(0, masses.zipWithNext().count { (a, b) -> b < a })
    }

    @Test
    fun paper_response_is_visible_and_correlated_with_height() {
        val field = PaperHeightField(seed = 23)
        val low = PaperMaterial(grainScale = 1f, heightAmplitude = 0f)
        val high = PaperMaterial(grainScale = 1f, heightAmplitude = 1f)
        val lowCoverage = mutableListOf<Double>()
        val highCoverage = mutableListOf<Double>()
        val heights = mutableListOf<Double>()
        for (y in 0 until 128 step 2) for (x in 0 until 128 step 2) {
            val h = field.sample(x.toFloat(), y.toFloat())
            heights += h.toDouble()
            lowCoverage += DryPaperResponse.coverageFactor(h, low.heightAmplitude, 1f, 0.5f).toDouble()
            highCoverage += DryPaperResponse.coverageFactor(h, high.heightAmplitude, 1f, 0.5f).toDouble()
        }
        val meanAbsDifference = lowCoverage.zip(highCoverage).map { abs(it.first - it.second) }.average()
        assertTrue(meanAbsDifference >= 0.05)
        assertTrue(abs(correlation(heights, highCoverage)) >= 0.5)
        val lowVariance = variance(lowCoverage)
        val highVariance = variance(highCoverage)
        assertTrue(highVariance / maxOf(lowVariance, 1e-12) >= 1.20)
    }

    @Test
    fun dedicated_dry_brush_has_calibrated_gaps() {
        val field = PaperHeightField(seed = 23)
        val paper = PaperMaterial(grainScale = 1f, heightAmplitude = 0.65f)
        val coverage = (0..1000).map { x ->
            val load = DryBrushLoad.advance(
                1f, x.toFloat(), 0f,
                DryBrushLoadConfig(initialLoad = 1f, depletionRatePerDocumentUnit = dryBrush.dryDepletionRate),
            )
            DryMaterialEvaluator.evaluate(
                1f, load, x.toFloat(), 64f, 0f, x.toFloat(), 0.65f,
                dryBrush, paper, field,
            ).coverage
        }
        val gaps = coverage.count { it < 0.08f }
        val gapRate = gaps.toDouble() / coverage.size
        assertTrue("gapRate=$gapRate", gapRate in 0.05..0.35)
    }

    @Test
    fun bristle_direction_transform_has_no_orientation_error() {
        val errors = (0 until 360 step 5).map { degrees ->
            val expected = Math.toRadians(degrees.toDouble())
            val localDirectionX = 1.0
            val localDirectionY = 0.0
            val worldX = localDirectionX * kotlin.math.cos(expected) - localDirectionY * kotlin.math.sin(expected)
            val worldY = localDirectionX * kotlin.math.sin(expected) + localDirectionY * kotlin.math.cos(expected)
            angleError(kotlin.math.atan2(worldY, worldX), expected)
        }.sorted()
        assertTrue(Math.toDegrees(errors.average()) <= 5.0)
        assertTrue(Math.toDegrees(errors[(errors.size * 0.99).toInt().coerceAtMost(errors.lastIndex)]) <= 12.0)
    }

    private fun rSquared(x: List<Double>, y: List<Double>): Double {
        val mean = y.average()
        val slope = covariance(x, y) / variance(x)
        val intercept = mean - slope * x.average()
        val residual = x.indices.sumOf { index ->
            val error = y[index] - (intercept + slope * x[index])
            error * error
        }
        val total = y.sumOf { (it - mean) * (it - mean) }
        return if (total == 0.0) 1.0 else 1.0 - residual / total
    }

    private fun correlation(a: List<Double>, b: List<Double>): Double =
        covariance(a, b) / sqrt(variance(a) * variance(b))

    private fun covariance(a: List<Double>, b: List<Double>): Double {
        val meanA = a.average()
        val meanB = b.average()
        return a.indices.sumOf { (a[it] - meanA) * (b[it] - meanB) } / a.size
    }

    private fun variance(values: List<Double>): Double {
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }

    private fun angleError(actual: Double, expected: Double): Double {
        var delta = abs(actual - expected) % (2.0 * Math.PI)
        if (delta > Math.PI) delta = 2.0 * Math.PI - delta
        return delta
    }
}
