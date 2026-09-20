package com.dsq.rebackground.paint.bristle

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** SoA layout for the current bristle-tip positions in local brush space. */
class BristleField(val model: BristleModel) {
    private val goldenAngle = (PI * (3.0 - sqrt(5.0))).toFloat()
    internal val baseX = FloatArray(model.count)
    internal val baseY = FloatArray(model.count)
    internal val offsetX = FloatArray(model.count)
    internal val offsetY = FloatArray(model.count)

    init {
        reset()
    }

    fun reset() {
        for (index in 0 until model.count) {
            val radius = model.spread * sqrt((index + 0.5f) / model.count)
            val angle = goldenAngle * index
            baseX[index] = cos(angle) * radius
            baseY[index] = sin(angle) * radius
            offsetX[index] = baseX[index]
            offsetY[index] = baseY[index]
        }
    }

    fun offsetMagnitude(index: Int): Float {
        require(index in 0 until model.count)
        return sqrt(offsetX[index] * offsetX[index] + offsetY[index] * offsetY[index])
    }
}
