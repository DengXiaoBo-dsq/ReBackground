package com.dsq.rebackground.paint.wet

import com.dsq.rebackground.paint.math.Vec2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * G6 deterministic CPU wet core.
 *
 * Water and mobile RGB pigment are conserved by closed-boundary forward bilinear
 * transport. Pigment enters [bound] only through deposition; water leaves active
 * state only through analytically integrated absorption or evaporation.
 */
class WetCoreSimulator(
    val width: Int,
    val height: Int,
    val config: WetCoreConfig = WetCoreConfig(),
) {
    init { require(width > 0 && height > 0) }

    private val size = width * height
    private val water = FloatArray(size)
    private val mobileR = FloatArray(size)
    private val mobileG = FloatArray(size)
    private val mobileB = FloatArray(size)
    private val boundR = FloatArray(size)
    private val boundG = FloatArray(size)
    private val boundB = FloatArray(size)
    private val velocityX = FloatArray(size)
    private val velocityY = FloatArray(size)

    private var accumulator = 0f
    private var initialWater = 0.0
    private var injectedWater = 0.0
    private var absorbedWater = 0.0
    private var evaporatedWater = 0.0
    private var escapedWater = 0.0
    private var initialPigment = 0.0
    private var injectedPigment = 0.0
    private var negativeStateViolations = 0
    private var nanCount = 0
    private var infCount = 0
    private var accuracyWarning = false
    private var executedSteps = 0L

    fun clear() {
        water.fill(0f); mobileR.fill(0f); mobileG.fill(0f); mobileB.fill(0f)
        boundR.fill(0f); boundG.fill(0f); boundB.fill(0f)
        velocityX.fill(0f); velocityY.fill(0f)
        accumulator = 0f
        initialWater = 0.0; injectedWater = 0.0; absorbedWater = 0.0; evaporatedWater = 0.0; escapedWater = 0.0
        initialPigment = 0.0; injectedPigment = 0.0
        negativeStateViolations = 0; nanCount = 0; infCount = 0; accuracyWarning = false; executedSteps = 0L
    }

    /** Establishes a test initial state, separate from subsequent brush injection. */
    fun seedCell(x: Int, y: Int, waterAmount: Float, pigment: WetRgb = WetRgb.Transparent, velocity: Vec2 = Vec2(0f, 0f)) {
        validateInjection(waterAmount, pigment, velocity)
        val i = index(x, y)
        water[i] += waterAmount
        mobileR[i] += pigment.red; mobileG[i] += pigment.green; mobileB[i] += pigment.blue
        velocityX[i] = velocity.x; velocityY[i] = velocity.y
        initialWater += waterAmount
        initialPigment += pigment.mass
        scanFiniteAndNonNegative()
    }

    /** Water-weighted velocity merge required by G6-10. */
    fun injectCell(x: Int, y: Int, waterAmount: Float, pigment: WetRgb = WetRgb.Transparent, velocity: Vec2 = Vec2(0f, 0f)) {
        validateInjection(waterAmount, pigment, velocity)
        val i = index(x, y)
        val oldWater = water[i]
        val total = oldWater + waterAmount
        if (total > EPSILON) {
            velocityX[i] = (oldWater * velocityX[i] + waterAmount * velocity.x) / total
            velocityY[i] = (oldWater * velocityY[i] + waterAmount * velocity.y) / total
        }
        water[i] = total
        mobileR[i] += pigment.red; mobileG[i] += pigment.green; mobileB[i] += pigment.blue
        injectedWater += waterAmount
        injectedPigment += pigment.mass
        scanFiniteAndNonNegative()
    }

    /** Advances real elapsed time through bounded fixed-size G6 steps. */
    fun advance(frameDeltaSeconds: Float): Int {
        require(frameDeltaSeconds.isFinite() && frameDeltaSeconds >= 0f)
        accumulator += frameDeltaSeconds
        var steps = 0
        while (accumulator + EPSILON >= config.fixedDt && steps < config.maxSubsteps) {
            stepFixed()
            accumulator -= config.fixedDt
            steps++
        }
        return steps
    }

    fun stepFixed() {
        transportClosed(water)
        transportClosed(mobileR); transportClosed(mobileG); transportClosed(mobileB)
        diffuseClosed(water, config.waterDiffusion)
        diffuseClosed(mobileR, config.pigmentDiffusion); diffuseClosed(mobileG, config.pigmentDiffusion); diffuseClosed(mobileB, config.pigmentDiffusion)
        depositMobilePigment()
        removeWaterAnalytically(config.absorptionRate, absorbed = true)
        removeWaterAnalytically(config.evaporationRate, absorbed = false)
        executedSteps++
        scanFiniteAndNonNegative()
    }

    fun setUniformVelocity(velocity: Vec2) {
        require(velocity.isFinite)
        velocityX.fill(velocity.x); velocityY.fill(velocity.y)
        updateCflDiagnostic()
    }

    fun velocityAt(x: Int, y: Int): Vec2 { val i = index(x, y); return Vec2(velocityX[i], velocityY[i]) }
    fun waterAt(x: Int, y: Int): Float = water[index(x, y)]
    fun wetnessAt(x: Int, y: Int): Float = (water[index(x, y)] / config.waterCapacity).coerceIn(0f, 1f)
    fun mobileAt(x: Int, y: Int): WetRgb = rgb(mobileR, mobileG, mobileB, index(x, y))
    fun boundAt(x: Int, y: Int): WetRgb = rgb(boundR, boundG, boundB, index(x, y))
    fun waterMass(): Double = sum(water)
    fun mobilePigmentMass(): Double = sum(mobileR) + sum(mobileG) + sum(mobileB)
    fun boundPigmentMass(): Double = sum(boundR) + sum(boundG) + sum(boundB)
    fun centroidOfWater(): Vec2 = centroid(water)
    fun varianceOfWater(): Double = variance(water)
    fun peakWater(): Float = water.maxOrNull() ?: 0f
    fun cflNumber(): Float = maxVelocity() * config.fixedDt / config.cellSize
    fun metrics(): WetCoreMetrics = WetCoreMetrics(
        waterMass = waterMass(), mobilePigmentMass = mobilePigmentMass(), boundPigmentMass = boundPigmentMass(),
        initialWater = initialWater, injectedWater = injectedWater, absorbedWater = absorbedWater,
        evaporatedWater = evaporatedWater, escapedWater = escapedWater,
        waterBudgetResidual = relativeResidual(initialWater + injectedWater - absorbedWater - evaporatedWater - escapedWater, waterMass(), initialWater + injectedWater),
        pigmentBudgetResidual = relativeResidual(initialPigment + injectedPigment, mobilePigmentMass() + boundPigmentMass(), initialPigment + injectedPigment),
        maxVelocity = maxVelocity(), cflNumber = cflNumber(), accuracyWarning = accuracyWarning,
        negativeStateViolations = negativeStateViolations, nanCount = nanCount, infCount = infCount, fixedSteps = executedSteps,
    )

    private fun transportClosed(field: FloatArray) {
        val next = FloatArray(size)
        for (y in 0 until height) for (x in 0 until width) {
            val i = index(x, y)
            val amount = field[i]
            if (amount == 0f) continue
            val targetX = (x + velocityX[i] * config.fixedDt / config.cellSize).coerceIn(0f, (width - 1).toFloat())
            val targetY = (y + velocityY[i] * config.fixedDt / config.cellSize).coerceIn(0f, (height - 1).toFloat())
            val x0 = floor(targetX).toInt(); val y0 = floor(targetY).toInt()
            val x1 = min(x0 + 1, width - 1); val y1 = min(y0 + 1, height - 1)
            val fx = targetX - x0; val fy = targetY - y0
            next[index(x0, y0)] += amount * (1f - fx) * (1f - fy)
            next[index(x1, y0)] += amount * fx * (1f - fy)
            next[index(x0, y1)] += amount * (1f - fx) * fy
            next[index(x1, y1)] += amount * fx * fy
        }
        field.indices.forEach { field[it] = next[it] }
    }

    /** Pairwise flux form keeps a closed domain conservative and non-negative in the configured safety range. */
    private fun diffuseClosed(field: FloatArray, rate: Float) {
        if (rate == 0f) return
        val mu = rate * config.fixedDt / (config.cellSize * config.cellSize)
        val next = field.copyOf()
        for (y in 0 until height) for (x in 0 until width) {
            val i = index(x, y)
            if (x + 1 < width) exchange(field, next, i, index(x + 1, y), mu)
            if (y + 1 < height) exchange(field, next, i, index(x, y + 1), mu)
        }
        field.indices.forEach { field[it] = max(0f, next[it]) }
    }

    private fun exchange(source: FloatArray, target: FloatArray, a: Int, b: Int, mu: Float) {
        val flux = (source[b] - source[a]) * mu
        target[a] += flux
        target[b] -= flux
    }

    private fun depositMobilePigment() {
        if (config.depositionRate == 0f) return
        for (i in 0 until size) {
            val factor = 1f - exp((-config.depositionRate * water[i] * config.fixedDt).toDouble()).toFloat()
            transfer(mobileR, boundR, i, factor); transfer(mobileG, boundG, i, factor); transfer(mobileB, boundB, i, factor)
        }
    }

    private fun removeWaterAnalytically(rate: Float, absorbed: Boolean) {
        if (rate == 0f) return
        val factor = exp((-rate * config.fixedDt).toDouble()).toFloat()
        var removed = 0.0
        for (i in 0 until size) {
            val before = water[i]
            water[i] = before * factor
            removed += (before - water[i]).toDouble()
        }
        if (absorbed) absorbedWater += removed else evaporatedWater += removed
    }

    private fun scanFiniteAndNonNegative() {
        val fields = arrayOf(water, mobileR, mobileG, mobileB, boundR, boundG, boundB, velocityX, velocityY)
        fields.forEach { field -> field.indices.forEach { i ->
            val value = field[i]
            if (value.isNaN()) { nanCount++; field[i] = 0f }
            else if (!value.isFinite()) { infCount++; field[i] = 0f }
            else if (field !== velocityX && field !== velocityY && value < -1e-6f) { negativeStateViolations++; field[i] = 0f }
            else if (field !== velocityX && field !== velocityY && value < 0f) field[i] = 0f
        } }
        updateCflDiagnostic()
    }

    private fun updateCflDiagnostic() { if (cflNumber() > 1f) accuracyWarning = true }
    private fun maxVelocity(): Float = velocityX.indices.maxOfOrNull { i -> sqrt(velocityX[i] * velocityX[i] + velocityY[i] * velocityY[i]) } ?: 0f
    private fun transfer(from: FloatArray, to: FloatArray, i: Int, factor: Float) { val amount = from[i] * factor; from[i] -= amount; to[i] += amount }
    private fun index(x: Int, y: Int): Int { require(x in 0 until width && y in 0 until height); return y * width + x }
    private fun validateInjection(w: Float, p: WetRgb, v: Vec2) { require(w.isFinite() && w >= 0f); require(p.isFinite && p.nonNegative); require(v.isFinite) }
    private fun sum(values: FloatArray): Double = values.sumOf { it.toDouble() }
    private fun centroid(values: FloatArray): Vec2 {
        val mass = sum(values); if (mass <= EPSILON) return Vec2(0f, 0f)
        var xSum = 0.0; var ySum = 0.0
        for (y in 0 until height) for (x in 0 until width) { val v = values[index(x, y)].toDouble(); xSum += x * v; ySum += y * v }
        return Vec2((xSum / mass).toFloat(), (ySum / mass).toFloat())
    }
    private fun variance(values: FloatArray): Double {
        val mass = sum(values); if (mass <= EPSILON) return 0.0
        val c = centroid(values); var result = 0.0
        for (y in 0 until height) for (x in 0 until width) { val dx = x - c.x; val dy = y - c.y; result += (dx * dx + dy * dy) * values[index(x, y)] }
        return result / mass
    }
    private fun relativeResidual(expected: Double, actual: Double, reference: Double): Double =
        kotlin.math.abs(expected - actual) / max(kotlin.math.abs(reference), EPSILON.toDouble())
    private fun rgb(r: FloatArray, g: FloatArray, b: FloatArray, i: Int) = WetRgb(r[i], g[i], b[i])

    companion object { private const val EPSILON = 1e-7f }
}

data class WetRgb(val red: Float, val green: Float, val blue: Float) {
    init { require(red.isFinite() && green.isFinite() && blue.isFinite()) }
    val mass: Double get() = red.toDouble() + green.toDouble() + blue.toDouble()
    val nonNegative: Boolean get() = red >= 0f && green >= 0f && blue >= 0f
    val isFinite: Boolean get() = red.isFinite() && green.isFinite() && blue.isFinite()
    companion object { val Transparent = WetRgb(0f, 0f, 0f) }
}

data class WetCoreMetrics(
    val waterMass: Double,
    val mobilePigmentMass: Double,
    val boundPigmentMass: Double,
    val initialWater: Double,
    val injectedWater: Double,
    val absorbedWater: Double,
    val evaporatedWater: Double,
    val escapedWater: Double,
    val waterBudgetResidual: Double,
    val pigmentBudgetResidual: Double,
    val maxVelocity: Float,
    val cflNumber: Float,
    val accuracyWarning: Boolean,
    val negativeStateViolations: Int,
    val nanCount: Int,
    val infCount: Int,
    val fixedSteps: Long,
)
