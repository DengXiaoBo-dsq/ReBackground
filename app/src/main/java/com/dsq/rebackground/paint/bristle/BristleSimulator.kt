package com.dsq.rebackground.paint.bristle

import kotlin.math.abs
import kotlin.math.exp

/** Simplified spring-damper bristle model. It updates local tip offsets from pressure/tilt/velocity. */
class BristleSimulator {
    fun step(field: BristleField, model: BristleModel, input: BristleInput, dt: Float) {
        require(dt.isFinite() && dt >= 0f)
        if (dt == 0f) return

        val pressureScale = .5f + .5f * input.pressure
        val tiltScale = 1f + abs(input.tiltX) + abs(input.tiltY)
        val velocityInfluence = model.length * .08f
        val tiltInfluence = model.length * .35f
        val relaxation = 1f - exp(-model.stiffness * dt)

        for (index in 0 until model.count) {
            val targetX = field.baseX[index] * pressureScale * tiltScale +
                input.velocityX * velocityInfluence + input.tiltX * tiltInfluence
            val targetY = field.baseY[index] * pressureScale * tiltScale +
                input.velocityY * velocityInfluence + input.tiltY * tiltInfluence
            field.offsetX[index] += (targetX - field.offsetX[index]) * relaxation
            field.offsetY[index] += (targetY - field.offsetY[index]) * relaxation
        }
    }
}
