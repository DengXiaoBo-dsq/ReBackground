package com.dsq.rebackground.paint.pigment

/**
 * Explicit finite-difference diffusion. Pigment is transported more where the paper is wet;
 * wetness also spreads on its own. This is intentionally a small, inspectable CPU model.
 */
class DiffusionModel(private val config: PigmentSimulationConfig) {
    fun step(pigment: PigmentField, wetness: WetnessField, dt: Float) {
        require(dt.isFinite() && dt >= 0f) { "Diffusion delta time must be finite and non-negative" }
        val sourceWetness = wetness.values
        val nextWetness = diffuseChannel(sourceWetness, sourceWetness, config.diffusionRate, dt, wetness.width, wetness.height)
        val nextRed = diffuseChannel(pigment.red, sourceWetness, config.diffusionRate, dt, pigment.width, pigment.height)
        val nextGreen = diffuseChannel(pigment.green, sourceWetness, config.diffusionRate, dt, pigment.width, pigment.height)
        val nextBlue = diffuseChannel(pigment.blue, sourceWetness, config.diffusionRate, dt, pigment.width, pigment.height)
        wetness.setValues(nextWetness)
        pigment.setChannels(nextRed, nextGreen, nextBlue)
    }

    private fun diffuseChannel(
        values: FloatArray,
        wetness: FloatArray,
        rate: Float,
        dt: Float,
        width: Int,
        height: Int
    ): FloatArray {
        val next = values.copyOf()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val factor = (rate * wetness[index] * dt * 0.25f).coerceIn(0f, 0.5f)
                if (factor <= 0f) continue

                if (x + 1 < width) {
                    val right = index + 1
                    val transfer = (values[right] - values[index]) * factor
                    next[index] += transfer
                    next[right] -= transfer
                }
                if (y + 1 < height) {
                    val down = index + width
                    val transfer = (values[down] - values[index]) * factor
                    next[index] += transfer
                    next[down] -= transfer
                }
            }
        }
        return next
    }
}
