package com.dsq.rebackground.paint.pigment

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * P4 facade. It owns pigment and wetness state, deposits brush-generated ink, then advances
 * diffusion, absorption and drying. Renderers consume these fields; they do not own them.
 */
class PigmentSimulator(
    val width: Int,
    val height: Int,
    private val config: PigmentSimulationConfig = PigmentSimulationConfig()
) {
    init {
        require(width > 0 && height > 0) { "Pigment simulation dimensions must be positive" }
    }

    val pigment = PigmentField(width, height)
    val wetness = WetnessField(width, height)
    private val diffusion = DiffusionModel(config)
    private val absorption = AbsorptionModel(config)
    private val drying = DryingModel(config)

    fun deposit(deposit: PigmentDeposit) {
        val minX = floor(deposit.centerX - deposit.radius).toInt().coerceIn(0, width - 1)
        val maxX = floor(deposit.centerX + deposit.radius).toInt().coerceIn(0, width - 1)
        val minY = floor(deposit.centerY - deposit.radius).toInt().coerceIn(0, height - 1)
        val maxY = floor(deposit.centerY + deposit.radius).toInt().coerceIn(0, height - 1)

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val dx = x + 0.5f - deposit.centerX
                val dy = y + 0.5f - deposit.centerY
                val distance = sqrt(dx * dx + dy * dy)
                val coverage = (1f - distance / deposit.radius).coerceIn(0f, 1f)
                if (coverage <= 0f) continue

                val amount = deposit.amount * coverage
                pigment.add(x, y, deposit.color.red, deposit.color.green, deposit.color.blue, amount)
                wetness.add(x, y, deposit.wetness * coverage)
            }
        }
    }

    fun step(dt: Float) {
        require(dt.isFinite() && dt >= 0f) { "Simulation delta time must be finite and non-negative" }
        val substep = dt / config.iterations
        repeat(config.iterations) {
            diffusion.step(pigment, wetness, substep)
            absorption.step(pigment, wetness, substep)
            drying.step(wetness, substep)
        }
    }

    fun clear() {
        pigment.clear()
        wetness.clear()
    }
}
