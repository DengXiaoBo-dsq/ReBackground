package com.dsq.rebackground.paint.bristle

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** Procedural parallel bristle bundles in brush-local space. */
object DryBristleField {
    fun coverage(
        localNormal: Float,
        arcLengthDocumentUnits: Float,
        density: Float,
        seed: Int = 0,
    ): Float {
        require(localNormal.isFinite())
        require(arcLengthDocumentUnits.isFinite() && arcLengthDocumentUnits >= 0f)
        require(density.isFinite() && density in 0f..1f)
        if (density == 0f) return 1f
        val bands = 8f + density * 40f
        // Align one deterministic bundle with the stroke centerline; without
        // this half-band shift the center can accidentally sit in a permanent gap.
        val coordinate = (localNormal.coerceIn(-1f, 1f) * 0.5f + 0.5f) * bands + 0.5f
        val band = floor(coordinate)
        val within = coordinate - band
        val bristleLoad = hash(band + seed * 17.13f)
        val halfWidth = 0.16f + bristleLoad * 0.22f
        val fiber = 1f - smoothstep(halfWidth, halfWidth + 0.10f, abs(within - 0.5f))
        val longitudinal = valueNoise(arcLengthDocumentUnits * 0.055f, band * 0.37f + seed)
        val breakup = smoothstep(0.24f, 0.60f, longitudinal + bristleLoad * 0.22f)
        val pattern = fiber * (0.08f + 0.92f * breakup) * (0.55f + 0.45f * bristleLoad)
        // Dense dry brushes expose the spaces between individual bundles much
        // more strongly than a linear blend, while sparse brushes remain soft.
        val separation = density * (2f - density)
        return (1f + (pattern - 1f) * separation).coerceIn(0f, 1f)
    }

    private fun valueNoise(x: Float, y: Float): Float {
        val ix = floor(x)
        val iy = floor(y)
        val fx = x - ix
        val fy = y - iy
        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)
        return lerp(lerp(hash(ix + iy * 19.19f), hash(ix + 1f + iy * 19.19f), ux),
            lerp(hash(ix + (iy + 1f) * 19.19f), hash(ix + 1f + (iy + 1f) * 19.19f), ux), uy)
    }

    private fun hash(value: Float): Float {
        val raw = sin(value * 127.1f) * 43_758.547f
        return raw - floor(raw)
    }

    private fun smoothstep(a: Float, b: Float, value: Float): Float {
        val t = ((value - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
