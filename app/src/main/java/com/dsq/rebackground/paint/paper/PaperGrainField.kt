package com.dsq.rebackground.paint.paper

import kotlin.random.Random

/** Deterministic scalar grain field used by later render/composite passes. */
class PaperGrainField(
    val width: Int,
    val height: Int,
    seed: Long = 0L
) {
    init {
        require(width > 0 && height > 0)
    }

    private val values = FloatArray(width * height)

    init {
        val random = Random(seed)
        for (index in values.indices) values[index] = random.nextFloat()
    }

    fun sample(x: Int, y: Int): Float {
        require(x in 0 until width && y in 0 until height)
        return values[y * width + x]
    }
}
