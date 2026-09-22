package com.dsq.rebackground.paint.paper

import kotlin.math.floor
import kotlin.math.sin

/** Continuous deterministic document-space paper height field. */
class PaperHeightField(private val seed: Int = 0) {
    fun sample(documentX: Float, documentY: Float, grainScale: Float = 1f): Float {
        require(documentX.isFinite() && documentY.isFinite())
        require(grainScale.isFinite() && grainScale > 0f)
        var amplitude = 0.5714286f
        var frequency = 1f / (18f * grainScale)
        var value = 0f
        repeat(3) {
            value += valueNoise(documentX * frequency, documentY * frequency) * amplitude
            frequency *= 2.07f
            amplitude *= 0.5f
        }
        return value.coerceIn(0f, 1f)
    }

    private fun valueNoise(x: Float, y: Float): Float {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val fx = x - ix
        val fy = y - iy
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        val a = hash(ix, iy)
        val b = hash(ix + 1, iy)
        val c = hash(ix, iy + 1)
        val d = hash(ix + 1, iy + 1)
        return lerp(lerp(a, b, ux), lerp(c, d, ux), uy)
    }

    private fun hash(x: Int, y: Int): Float {
        // Keep the sine output bounded instead of amplifying it before fract().
        // This remains visually irregular while avoiding CPU/GPU precision chaos.
        val phase = x * 12.9898 + y * 78.233 + seed * 37.719
        return (0.5 + 0.5 * sin(phase)).toFloat()
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
